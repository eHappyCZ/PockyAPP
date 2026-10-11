use pocky_core::{BeaconFix, GeoPoint, LiveDuration, LiveShare, OwnLiveShare};

use crate::preferences::LocationArea;

#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct GeoLocation {
    pub latitude: f64,
    pub longitude: f64,
    pub accuracy_m: Option<f64>,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum LiveLength {
    Minutes15,
    Hour1,
    Hours8,
}

impl From<GeoLocation> for GeoPoint {
    fn from(location: GeoLocation) -> Self {
        Self {
            latitude: location.latitude,
            longitude: location.longitude,
            accuracy_m: location.accuracy_m,
        }
    }
}

impl From<GeoPoint> for GeoLocation {
    fn from(point: GeoPoint) -> Self {
        Self {
            latitude: point.latitude,
            longitude: point.longitude,
            accuracy_m: point.accuracy_m,
        }
    }
}

impl From<LiveLength> for LiveDuration {
    fn from(length: LiveLength) -> Self {
        match length {
            LiveLength::Minutes15 => Self::Minutes15,
            LiveLength::Hour1 => Self::Hour1,
            LiveLength::Hours8 => Self::Hours8,
        }
    }
}

#[uniffi::export]
pub fn parse_geo_uri(uri: String) -> Option<GeoLocation> {
    pocky_core::parse_geo_uri(&uri).map(Into::into)
}

#[uniffi::export]
pub fn blur_location(location: GeoLocation, area: LocationArea) -> GeoLocation {
    pocky_core::blur(location.into(), area.into()).into()
}

#[uniffi::export]
pub fn live_length_ms(length: LiveLength) -> u64 {
    u64::try_from(LiveDuration::from(length).duration().as_millis()).unwrap_or(u64::MAX)
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LivePerson {
    pub room_id: String,
    pub user_id: String,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub location: Option<GeoLocation>,
    pub updated_ms: Option<u64>,
    pub ends_ms: u64,
    pub is_own: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct OwnLiveLocation {
    pub room_id: String,
    pub ends_ms: u64,
}

#[uniffi::export(callback_interface)]
pub trait LiveLocationListener: Send + Sync {
    fn on_update(&self, people: Vec<LivePerson>);
}

impl From<LiveShare> for LivePerson {
    fn from(share: LiveShare) -> Self {
        Self {
            location: share
                .geo_uri
                .as_deref()
                .and_then(pocky_core::parse_geo_uri)
                .map(Into::into),
            room_id: share.room_id,
            user_id: share.user_id,
            name: share.name,
            avatar_url: share.avatar_url,
            updated_ms: share.updated_ms,
            ends_ms: share.ends_ms,
            is_own: share.is_own,
        }
    }
}

impl From<OwnLiveShare> for OwnLiveLocation {
    fn from(share: OwnLiveShare) -> Self {
        Self {
            room_id: share.room_id,
            ends_ms: share.ends_ms,
        }
    }
}

#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct LocationFix {
    pub location: GeoLocation,
    pub speed_mps: Option<f64>,
    pub at_ms: u64,
}

impl From<LocationFix> for BeaconFix {
    fn from(fix: LocationFix) -> Self {
        Self {
            point: fix.location.into(),
            speed_mps: fix.speed_mps,
            at_ms: fix.at_ms,
        }
    }
}

#[uniffi::export]
pub fn should_send_live_location(
    last: Option<LocationFix>,
    fix: LocationFix,
    area: LocationArea,
    rooms: u32,
) -> bool {
    let last = last.map(BeaconFix::from);
    pocky_core::should_send_beacon(last.as_ref(), &fix.into(), area.into(), rooms as usize)
}
