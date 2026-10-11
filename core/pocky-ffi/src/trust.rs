use pocky_core::{IdentityWarning, ShieldReason, TrustLevel};

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum UserTrust {
    NoIdentity,
    Unverified,
    Verified,
    VerificationBroken,
}

impl From<TrustLevel> for UserTrust {
    fn from(level: TrustLevel) -> Self {
        match level {
            TrustLevel::NoIdentity => UserTrust::NoIdentity,
            TrustLevel::Unverified => UserTrust::Unverified,
            TrustLevel::Verified => UserTrust::Verified,
            TrustLevel::VerificationBroken => UserTrust::VerificationBroken,
        }
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct IdentityChange {
    pub user_id: String,
    pub name: Option<String>,
    pub was_verified: bool,
}

impl From<IdentityWarning> for IdentityChange {
    fn from(warning: IdentityWarning) -> Self {
        Self {
            user_id: warning.user_id,
            name: warning.name,
            was_verified: warning.was_verified,
        }
    }
}

#[uniffi::export(callback_interface)]
pub trait IdentityChangeListener: Send + Sync {
    fn on_update(&self, changes: Vec<IdentityChange>);
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MessageShield {
    AuthenticityNotGuaranteed,
    UnknownDevice,
    UnverifiedDevice,
    UnverifiedSender,
    IdentityChanged,
    MismatchedSender,
    SentInClear,
}

impl From<ShieldReason> for MessageShield {
    fn from(reason: ShieldReason) -> Self {
        match reason {
            ShieldReason::AuthenticityNotGuaranteed => MessageShield::AuthenticityNotGuaranteed,
            ShieldReason::UnknownDevice => MessageShield::UnknownDevice,
            ShieldReason::UnverifiedDevice => MessageShield::UnverifiedDevice,
            ShieldReason::UnverifiedSender => MessageShield::UnverifiedSender,
            ShieldReason::IdentityChanged => MessageShield::IdentityChanged,
            ShieldReason::MismatchedSender => MessageShield::MismatchedSender,
            ShieldReason::SentInClear => MessageShield::SentInClear,
        }
    }
}

#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct ShieldInfo {
    pub danger: bool,
    pub reason: MessageShield,
}

impl From<pocky_core::Shield> for ShieldInfo {
    fn from(shield: pocky_core::Shield) -> Self {
        Self {
            danger: shield.danger,
            reason: shield.reason.into(),
        }
    }
}
