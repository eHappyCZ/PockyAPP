use pocky_core::{Delivery, MessagePreview, PreviewKind, RoomSummary, SyncState};
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Room {
    pub id: String,
    pub name: String,
    pub avatar_url: Option<String>,
    pub last_message: Option<LastMessage>,
    pub timestamp_ms: Option<u64>,
    pub unread_messages: u64,
    pub unread_mentions: u64,
    pub is_encrypted: bool,
    pub is_direct: bool,
    pub direct_user_id: Option<String>,
    pub is_invite: bool,
    pub is_favourite: bool,
    pub is_muted: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LastMessage {
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub is_own: bool,
    pub content: MessageContent,
    pub delivery: Option<&'static str>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum MessageContent {
    Text { body: String },
    Emote { body: String },
    Image,
    Video,
    Audio,
    File,
    Location,
    Sticker,
    Poll,
    Encrypted,
    Invite,
    Other,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum SyncStatus {
    Idle,
    Running,
    Offline,
    Error,
    Terminated,
}

impl From<RoomSummary> for Room {
    fn from(room: RoomSummary) -> Self {
        Self {
            id: room.id,
            name: room.name,
            avatar_url: room.avatar_url,
            last_message: room.last_message.map(Into::into),
            timestamp_ms: room.timestamp_ms,
            unread_messages: room.unread_messages,
            unread_mentions: room.unread_mentions,
            is_encrypted: room.is_encrypted,
            is_direct: room.is_direct,
            direct_user_id: room.direct_user_id,
            is_invite: room.is_invite,
            is_favourite: room.is_favourite,
            is_muted: room.is_muted,
        }
    }
}

impl From<MessagePreview> for LastMessage {
    fn from(preview: MessagePreview) -> Self {
        Self {
            sender_id: preview.sender_id,
            sender_name: preview.sender_name,
            is_own: preview.is_own,
            content: preview.kind.into(),
            delivery: preview.delivery.map(|delivery| match delivery {
                Delivery::Sending => "sending",
                Delivery::Failed => "failed",
                Delivery::Sent => "sent",
                Delivery::Read => "read",
            }),
        }
    }
}

impl From<PreviewKind> for MessageContent {
    fn from(kind: PreviewKind) -> Self {
        match kind {
            PreviewKind::Text(body) => Self::Text { body },
            PreviewKind::Emote(body) => Self::Emote { body },
            PreviewKind::Image => Self::Image,
            PreviewKind::Video => Self::Video,
            PreviewKind::Audio => Self::Audio,
            PreviewKind::File => Self::File,
            PreviewKind::Location => Self::Location,
            PreviewKind::Sticker => Self::Sticker,
            PreviewKind::Poll => Self::Poll,
            PreviewKind::Encrypted => Self::Encrypted,
            PreviewKind::Invite => Self::Invite,
            PreviewKind::Other => Self::Other,
        }
    }
}

impl From<SyncState> for SyncStatus {
    fn from(state: SyncState) -> Self {
        match state {
            SyncState::Idle => Self::Idle,
            SyncState::Running => Self::Running,
            SyncState::Offline => Self::Offline,
            SyncState::Error => Self::Error,
            SyncState::Terminated => Self::Terminated,
        }
    }
}
