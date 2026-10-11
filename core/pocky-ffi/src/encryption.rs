use pocky_core::{EncryptionStatus, LogoutRisk, PassphraseStrength as CoreStrength, RecoveryState};

#[derive(Debug, Clone, uniffi::Record)]
pub struct EncryptionInfo {
    pub recovery: Recovery,
    pub backup_on_server: bool,
    pub backup_enabled: bool,
    pub secret_storage: bool,
    pub has_identity: bool,
    pub device_verified: bool,
    pub other_devices: u32,
    pub verified_other_devices: u32,
    pub can_recover: bool,
    pub can_verify_with_device: bool,
    pub can_enable_recovery: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LogoutWarning {
    pub last_device: bool,
    pub recovery_enabled: bool,
    pub loses_history: bool,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum Recovery {
    Unknown,
    Enabled,
    Disabled,
    Incomplete,
}

#[uniffi::export(callback_interface)]
pub trait EncryptionListener: Send + Sync {
    fn on_update(&self, info: EncryptionInfo);
}

impl From<EncryptionStatus> for EncryptionInfo {
    fn from(status: EncryptionStatus) -> Self {
        Self {
            can_recover: status.can_recover(),
            can_verify_with_device: status.can_verify_with_device(),
            can_enable_recovery: status.can_enable_recovery(),
            recovery: match status.recovery {
                RecoveryState::Unknown => Recovery::Unknown,
                RecoveryState::Enabled => Recovery::Enabled,
                RecoveryState::Disabled => Recovery::Disabled,
                RecoveryState::Incomplete => Recovery::Incomplete,
            },
            backup_on_server: status.backup_on_server,
            backup_enabled: status.backup_enabled,
            secret_storage: status.secret_storage,
            has_identity: status.has_identity,
            device_verified: status.device_verified,
            other_devices: status.other_devices,
            verified_other_devices: status.verified_other_devices,
        }
    }
}

impl From<LogoutRisk> for LogoutWarning {
    fn from(risk: LogoutRisk) -> Self {
        Self {
            loses_history: risk.loses_history(),
            last_device: risk.last_device,
            recovery_enabled: risk.recovery_enabled,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PassphraseStrength {
    TooShort,
    NoSymbol,
    Weak,
    Fair,
    Strong,
}

impl From<CoreStrength> for PassphraseStrength {
    fn from(strength: CoreStrength) -> Self {
        match strength {
            CoreStrength::TooShort => Self::TooShort,
            CoreStrength::NoSymbol => Self::NoSymbol,
            CoreStrength::Weak => Self::Weak,
            CoreStrength::Fair => Self::Fair,
            CoreStrength::Strong => Self::Strong,
        }
    }
}

#[uniffi::export]
pub fn passphrase_strength(passphrase: String) -> PassphraseStrength {
    pocky_core::passphrase_strength(&passphrase).into()
}

#[uniffi::export]
pub fn min_passphrase_chars() -> u32 {
    pocky_core::MIN_PASSPHRASE_CHARS as u32
}

#[uniffi::export]
pub fn recommended_passphrase_chars() -> u32 {
    pocky_core::RECOMMENDED_PASSPHRASE_CHARS as u32
}
