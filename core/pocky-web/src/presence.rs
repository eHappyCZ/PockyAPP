use pocky_core::{OwnStatus, Presence, TypingUser, UserPresence};
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PresenceInfo {
    pub user_id: String,
    pub presence: &'static str,
    pub last_active_ms: Option<u64>,
    pub status: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub text: String,
    pub until_ms: Option<u64>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Typist {
    pub user_id: String,
    pub name: Option<String>,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Privacy {
    pub share_presence: bool,
    pub share_typing: bool,
    pub location_precision: &'static str,
    pub chat_style: &'static str,
}

impl From<UserPresence> for PresenceInfo {
    fn from(info: UserPresence) -> Self {
        Self {
            user_id: info.user_id,
            presence: match info.presence {
                Presence::Online => "online",
                Presence::Unavailable => "unavailable",
                Presence::Offline => "offline",
                Presence::Unknown => "unknown",
            },
            last_active_ms: info.last_active_ms,
            status: info.status,
        }
    }
}

impl From<OwnStatus> for Status {
    fn from(status: OwnStatus) -> Self {
        Self {
            text: status.text,
            until_ms: status.until_ms,
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
