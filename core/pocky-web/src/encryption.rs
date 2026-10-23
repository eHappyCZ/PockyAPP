use pocky_core::{EncryptionStatus, PassphraseStrength, RecoveryState};
use serde::Serialize;

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Encryption {
    pub can_recover: bool,
    pub can_verify_with_device: bool,
    pub device_verified: bool,
    pub backup_enabled: bool,
    pub recovery_enabled: bool,
}

impl From<EncryptionStatus> for Encryption {
    fn from(status: EncryptionStatus) -> Self {
        Self {
            can_recover: status.can_recover(),
            can_verify_with_device: status.can_verify_with_device(),
            device_verified: status.device_verified,
            backup_enabled: status.backup_enabled,
            recovery_enabled: status.recovery == RecoveryState::Enabled,
        }
    }
}

pub fn strength_name(strength: PassphraseStrength) -> &'static str {
    match strength {
        PassphraseStrength::TooShort => "tooShort",
        PassphraseStrength::NoSymbol => "noSymbol",
        PassphraseStrength::Weak => "weak",
        PassphraseStrength::Fair => "fair",
        PassphraseStrength::Strong => "strong",
    }
}
