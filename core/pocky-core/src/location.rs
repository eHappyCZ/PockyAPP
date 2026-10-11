use std::time::Duration;

use matrix_sdk::{
    ruma::events::room::message::{
        LocationMessageEventContent, MessageType, RoomMessageEventContent,
    },
    BeaconError, Client,
};
use matrix_sdk_ui::timeline::Timeline;

use crate::{
    error::{CoreError, Result},
    preferences::LocationPrecision,
    rooms::known_room,
};

const METERS_PER_DEGREE: f64 = 111_320.0;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct GeoPoint {
    pub latitude: f64,
    pub longitude: f64,
    pub accuracy_m: Option<f64>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LiveDuration {
    Minutes15,
    Hour1,
    Hours8,
}

impl LiveDuration {
    pub fn duration(self) -> Duration {
        match self {
            Self::Minutes15 => Duration::from_secs(15 * 60),
            Self::Hour1 => Duration::from_secs(60 * 60),
            Self::Hours8 => Duration::from_secs(8 * 60 * 60),
        }
    }
}

impl LocationPrecision {
    pub fn radius_m(self) -> Option<f64> {
        match self {
            Self::Exact => None,
            Self::Neighborhood => Some(500.0),
            Self::City => Some(5_000.0),
        }
    }

    fn decimals(self) -> usize {
        match self {
            Self::Exact => 6,
            Self::Neighborhood => 4,
            Self::City => 3,
        }
    }
}

pub fn blur(point: GeoPoint, precision: LocationPrecision) -> GeoPoint {
    let Some(cell) = precision.radius_m() else {
        return point;
    };

    let lat_step = cell / METERS_PER_DEGREE;
    let latitude = snap(point.latitude.clamp(-90.0, 90.0), lat_step).clamp(-90.0, 90.0);

    let scale = latitude.to_radians().cos().max(0.01);
    let lon_step = cell / (METERS_PER_DEGREE * scale);
    let mut longitude = snap(point.longitude, lon_step);
    if longitude > 180.0 {
        longitude -= 360.0;
    } else if longitude < -180.0 {
        longitude += 360.0;
    }

    GeoPoint {
        latitude,
        longitude,
        accuracy_m: Some(point.accuracy_m.map_or(cell, |a| a.max(cell))),
    }
}

fn snap(value: f64, step: f64) -> f64 {
    ((value / step).floor() + 0.5) * step
}

pub fn geo_uri(point: GeoPoint, precision: LocationPrecision) -> String {
    let decimals = precision.decimals();
    let mut uri = format!(
        "geo:{:.decimals$},{:.decimals$}",
        point.latitude, point.longitude
    );
    if let Some(accuracy) = point.accuracy_m.filter(|a| a.is_finite() && *a > 0.0) {
        uri.push_str(&format!(";u={}", accuracy.round() as u64));
    }
    uri
}

pub fn parse_geo_uri(uri: &str) -> Option<GeoPoint> {
    let rest = uri.trim().strip_prefix("geo:")?;
    let mut parts = rest.split(';');
    let mut coords = parts.next()?.split(',');
    let latitude: f64 = coords.next()?.trim().parse().ok()?;
    let longitude: f64 = coords.next()?.trim().parse().ok()?;
    if !(-90.0..=90.0).contains(&latitude) || !(-180.0..=180.0).contains(&longitude) {
        return None;
    }

    let accuracy_m = parts
        .filter_map(|p| p.split_once('='))
        .find(|(key, _)| key.eq_ignore_ascii_case("u"))
        .and_then(|(_, value)| value.parse::<f64>().ok())
        .filter(|a| a.is_finite() && *a >= 0.0);

    Some(GeoPoint {
        latitude,
        longitude,
        accuracy_m,
    })
}

const EARTH_RADIUS_M: f64 = 6_371_000.0;
const BEACON_GAP_MS: u64 = 5_000;
const DRIVING_MPS: f64 = 5.0;
const WALKING_MPS: f64 = 0.7;
const DRIVING_INTERVAL_MS: u64 = 5_000;
const WALKING_INTERVAL_MS: u64 = 10_000;
const RESTING_INTERVAL_MS: u64 = 60_000;
const KEEP_ALIVE_MS: u64 = 180_000;
const AREA_KEEP_ALIVE_MS: u64 = 300_000;
const MIN_MOVE_M: f64 = 10.0;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BeaconFix {
    pub point: GeoPoint,
    pub speed_mps: Option<f64>,
    pub at_ms: u64,
}

pub fn distance_m(a: GeoPoint, b: GeoPoint) -> f64 {
    let (lat1, lat2) = (a.latitude.to_radians(), b.latitude.to_radians());
    let d_lat = lat2 - lat1;
    let d_lon = (b.longitude - a.longitude).to_radians();
    let h = (d_lat / 2.0).sin().powi(2) + lat1.cos() * lat2.cos() * (d_lon / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_M * h.sqrt().asin()
}

pub fn should_send_beacon(
    last: Option<&BeaconFix>,
    fix: &BeaconFix,
    precision: LocationPrecision,
    rooms: usize,
) -> bool {
    let Some(last) = last else { return true };
    let elapsed = fix.at_ms.saturating_sub(last.at_ms);
    if elapsed < BEACON_GAP_MS * rooms.max(1) as u64 {
        return false;
    }

    if precision != LocationPrecision::Exact {
        let moved = geo_uri(blur(last.point, precision), precision)
            != geo_uri(blur(fix.point, precision), precision);
        return moved || elapsed >= AREA_KEEP_ALIVE_MS;
    }

    let moved = distance_m(last.point, fix.point);
    let speed = fix
        .speed_mps
        .unwrap_or_else(|| moved / (elapsed as f64 / 1000.0));
    let interval = if speed >= DRIVING_MPS {
        DRIVING_INTERVAL_MS
    } else if speed >= WALKING_MPS {
        WALKING_INTERVAL_MS
    } else {
        RESTING_INTERVAL_MS
    };
    let threshold = fix.point.accuracy_m.unwrap_or(0.0).max(MIN_MOVE_M);

    (elapsed >= interval && moved >= threshold) || elapsed >= KEEP_ALIVE_MS
}

pub async fn send_location(
    timeline: &Timeline,
    point: GeoPoint,
    precision: LocationPrecision,
) -> Result<()> {
    let uri = geo_uri(blur(point, precision), precision);
    let content = LocationMessageEventContent::new(uri.clone(), uri);
    timeline
        .send(RoomMessageEventContent::new(MessageType::Location(content)).into())
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))?;
    Ok(())
}

pub async fn start_live_location(
    client: &Client,
    room_id: &str,
    length: LiveDuration,
) -> Result<()> {
    let room = known_room(client, room_id)?;
    let millis = u64::try_from(length.duration().as_millis()).unwrap_or(u64::MAX);
    room.start_live_location_share(millis, None).await?;
    Ok(())
}

pub async fn send_beacon(
    client: &Client,
    room_id: &str,
    point: GeoPoint,
    precision: LocationPrecision,
) -> Result<()> {
    let room = known_room(client, room_id)?;
    let uri = geo_uri(blur(point, precision), precision);
    room.send_location_beacon(uri).await.map_err(beacon_error)?;
    Ok(())
}

pub async fn stop_live_location(client: &Client, room_id: &str) -> Result<()> {
    let room = known_room(client, room_id)?;
    match room.stop_live_location_share().await {
        Ok(_) | Err(BeaconError::NotLive | BeaconError::NotFound | BeaconError::Redacted) => Ok(()),
        Err(err) => Err(beacon_error(err)),
    }
}

fn beacon_error(err: BeaconError) -> CoreError {
    match err {
        BeaconError::NotLive | BeaconError::NotFound | BeaconError::Redacted => {
            CoreError::LiveLocationEnded
        }
        BeaconError::Network(http) => http.into(),
        other => CoreError::Timeline(other.to_string()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn point(latitude: f64, longitude: f64) -> GeoPoint {
        GeoPoint {
            latitude,
            longitude,
            accuracy_m: None,
        }
    }

    fn distance_m(a: GeoPoint, b: GeoPoint) -> f64 {
        let dy = (a.latitude - b.latitude) * METERS_PER_DEGREE;
        let dx = (a.longitude - b.longitude) * METERS_PER_DEGREE * a.latitude.to_radians().cos();
        (dx * dx + dy * dy).sqrt()
    }

    #[test]
    fn exact_keeps_point() {
        let p = point(49.854_321, 18.541_234);
        assert_eq!(blur(p, LocationPrecision::Exact), p);
    }

    #[test]
    fn blur_stays_within_cell() {
        let p = point(49.854_321, 18.541_234);
        for precision in [LocationPrecision::Neighborhood, LocationPrecision::City] {
            let cell = precision.radius_m().unwrap();
            let blurred = blur(p, precision);
            assert!(distance_m(p, blurred) <= cell);
            assert_eq!(blurred.accuracy_m, Some(cell));
        }
    }

    #[test]
    fn nearby_points_share_cell() {
        let a = blur(point(49.8501, 18.5401), LocationPrecision::City);
        let b = blur(point(49.8502, 18.5402), LocationPrecision::City);
        assert_eq!(a, b);
    }

    #[test]
    fn blur_handles_edges() {
        for p in [
            point(90.0, 180.0),
            point(-90.0, -180.0),
            point(89.99, 179.99),
        ] {
            let blurred = blur(p, LocationPrecision::City);
            assert!((-90.0..=90.0).contains(&blurred.latitude));
            assert!((-180.0..=180.0).contains(&blurred.longitude));
        }
    }

    #[test]
    fn uri_round_trip() {
        let p = GeoPoint {
            latitude: 49.854_321,
            longitude: 18.541_234,
            accuracy_m: Some(12.4),
        };
        let uri = geo_uri(p, LocationPrecision::Exact);
        assert_eq!(uri, "geo:49.854321,18.541234;u=12");
        let parsed = parse_geo_uri(&uri).unwrap();
        assert_eq!(parsed.latitude, 49.854_321);
        assert_eq!(parsed.accuracy_m, Some(12.0));
    }

    #[test]
    fn blurred_uri_drops_digits() {
        let uri = geo_uri(
            blur(point(49.854_321, 18.541_234), LocationPrecision::City),
            LocationPrecision::City,
        );
        let (coords, accuracy) = uri.split_once(';').unwrap();
        assert_eq!(accuracy, "u=5000");
        assert!(coords
            .split(',')
            .all(|c| c.rsplit('.').next().unwrap().len() == 3));
    }

    fn fix(latitude: f64, longitude: f64, speed: Option<f64>, at_ms: u64) -> BeaconFix {
        BeaconFix {
            point: GeoPoint {
                latitude,
                longitude,
                accuracy_m: Some(5.0),
            },
            speed_mps: speed,
            at_ms,
        }
    }

    #[test]
    fn distance_is_haversine() {
        let d = distance_m(point(50.0, 14.0), point(50.0, 14.001));
        assert!((d - 71.5).abs() < 1.0, "{d}");
    }

    #[test]
    fn first_fix_is_always_sent() {
        assert!(should_send_beacon(
            None,
            &fix(50.0, 14.0, None, 0),
            LocationPrecision::Exact,
            1
        ));
    }

    #[test]
    fn respects_gap_per_room() {
        let last = fix(50.0, 14.0, Some(20.0), 0);
        let next = fix(50.001, 14.0, Some(20.0), 6_000);
        assert!(should_send_beacon(
            Some(&last),
            &next,
            LocationPrecision::Exact,
            1
        ));
        assert!(!should_send_beacon(
            Some(&last),
            &next,
            LocationPrecision::Exact,
            2
        ));
    }

    #[test]
    fn walking_waits_longer_than_driving() {
        let last = fix(50.0, 14.0, Some(1.4), 0);
        let near = fix(50.0001, 14.0, Some(1.4), 6_000);
        assert!(!should_send_beacon(
            Some(&last),
            &near,
            LocationPrecision::Exact,
            1
        ));
        let later = fix(50.0002, 14.0, Some(1.4), 11_000);
        assert!(should_send_beacon(
            Some(&last),
            &later,
            LocationPrecision::Exact,
            1
        ));
    }

    #[test]
    fn resting_sends_only_keep_alive() {
        let last = fix(50.0, 14.0, Some(0.0), 0);
        let jitter = fix(50.00003, 14.0, Some(0.0), 90_000);
        assert!(!should_send_beacon(
            Some(&last),
            &jitter,
            LocationPrecision::Exact,
            1
        ));
        let alive = fix(50.00003, 14.0, Some(0.0), 181_000);
        assert!(should_send_beacon(
            Some(&last),
            &alive,
            LocationPrecision::Exact,
            1
        ));
    }

    #[test]
    fn area_sends_on_cell_change() {
        let last = fix(49.8501, 18.5401, Some(15.0), 0);
        let same = fix(49.8502, 18.5402, Some(15.0), 60_000);
        assert!(!should_send_beacon(
            Some(&last),
            &same,
            LocationPrecision::City,
            1
        ));
        let other = fix(49.95, 18.70, Some(15.0), 60_000);
        assert!(should_send_beacon(
            Some(&last),
            &other,
            LocationPrecision::City,
            1
        ));
        let alive = fix(49.8502, 18.5402, Some(0.0), 301_000);
        assert!(should_send_beacon(
            Some(&last),
            &alive,
            LocationPrecision::City,
            1
        ));
    }

    #[test]
    fn parse_rejects_garbage() {
        assert!(parse_geo_uri("geo:abc,1").is_none());
        assert!(parse_geo_uri("geo:91,10").is_none());
        assert!(parse_geo_uri("http://x").is_none());
        assert!(parse_geo_uri("geo:50.1,14.2,230;crs=wgs84").is_some());
    }
}
