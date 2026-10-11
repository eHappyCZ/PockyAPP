use pocky_core::{Presence, TypingUser, UserPresence};

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum PresenceStatus {
    Online,
    Unavailable,
    Offline,
    Unknown,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct PresenceInfo {
    pub user_id: String,
    pub status: PresenceStatus,
    pub last_active_ms: Option<u64>,
    pub status_text: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct StatusMessage {
    pub text: String,
    pub until_ms: Option<u64>,
}

impl From<pocky_core::OwnStatus> for StatusMessage {
    fn from(status: pocky_core::OwnStatus) -> Self {
        Self {
            text: status.text,
            until_ms: status.until_ms,
        }
    }
}

#[uniffi::export(callback_interface)]
pub trait OwnStatusListener: Send + Sync {
    fn on_update(&self, status: Option<StatusMessage>);
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Typist {
    pub user_id: String,
    pub name: Option<String>,
}

#[uniffi::export(callback_interface)]
pub trait TypingListener: Send + Sync {
    fn on_update(&self, room_id: String, typists: Vec<Typist>);
}

impl From<UserPresence> for PresenceInfo {
    fn from(presence: UserPresence) -> Self {
        Self {
            user_id: presence.user_id,
            status: match presence.presence {
                Presence::Online => PresenceStatus::Online,
                Presence::Unavailable => PresenceStatus::Unavailable,
                Presence::Offline => PresenceStatus::Offline,
                Presence::Unknown => PresenceStatus::Unknown,
            },
            last_active_ms: presence.last_active_ms,
            status_text: presence.status,
        }
    }
}

impl From<TypingUser> for Typist {
    fn from(user: TypingUser) -> Self {
        Self {
            user_id: user.user_id,
            name: user.name,
        }
    }
}
