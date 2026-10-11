use pocky_core::DeviceInfo;

#[derive(Debug, Clone, uniffi::Record)]
pub struct AccountDevice {
    pub device_id: String,
    pub name: Option<String>,
    pub last_seen_ms: Option<u64>,
    pub is_current: bool,
    pub verified: bool,
}

impl From<DeviceInfo> for AccountDevice {
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
