use pocky_core::{DeviceInfo, LogoutRisk};
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Device {
    pub device_id: String,
    pub name: Option<String>,
    pub last_seen_ms: Option<u64>,
    pub is_current: bool,
    pub verified: bool,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LogoutWarning {
    pub loses_history: bool,
}

impl From<DeviceInfo> for Device {
    fn from(device: DeviceInfo) -> Self {
        Self {
            device_id: device.device_id,
            name: device.name,
            last_seen_ms: device.last_seen_ms,
            is_current: device.is_current,
            verified: device.verified,
        }
    }
}

impl From<LogoutRisk> for LogoutWarning {
    fn from(risk: LogoutRisk) -> Self {
        Self {
            loses_history: risk.loses_history(),
        }
    }
}
