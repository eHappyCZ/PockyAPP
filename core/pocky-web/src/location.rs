use pocky_core::{GeoPoint, LiveShare, LocationPrecision};
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LiveLocation {
    pub room_id: String,
    pub user_id: String,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub geo_uri: Option<String>,
    pub updated_ms: Option<u64>,
    pub ends_ms: u64,
    pub is_own: bool,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Point {
    pub latitude: f64,
    pub longitude: f64,
    pub accuracy_m: Option<f64>,
}

impl From<LiveShare> for LiveLocation {
    fn from(share: LiveShare) -> Self {
        Self {
            room_id: share.room_id,
            user_id: share.user_id,
            name: share.name,
            avatar_url: share.avatar_url,
            geo_uri: share.geo_uri,
            updated_ms: share.updated_ms,
            ends_ms: share.ends_ms,
            is_own: share.is_own,
        }
    }
}

impl From<GeoPoint> for Point {
    fn from(point: GeoPoint) -> Self {
        Self {
            latitude: point.latitude,
            longitude: point.longitude,
            accuracy_m: point.accuracy_m,
        }
    }
}

pub(crate) fn precision(name: &str) -> LocationPrecision {
    match name {
        "exact" => LocationPrecision::Exact,
        "neighborhood" => LocationPrecision::Neighborhood,
        _ => LocationPrecision::City,
    }
}

pub(crate) fn precision_name(precision: LocationPrecision) -> &'static str {
    match precision {
        LocationPrecision::Exact => "exact",
        LocationPrecision::Neighborhood => "neighborhood",
        LocationPrecision::City => "city",
    }
}
