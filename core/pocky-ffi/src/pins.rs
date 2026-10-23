use pocky_core::PinnedMessage;

use crate::rooms::MessageContent;

#[derive(Debug, Clone, uniffi::Record)]
pub struct Pinned {
    pub event_id: String,
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub timestamp_ms: u64,
    pub content: MessageContent,
}

impl From<PinnedMessage> for Pinned {
    fn from(message: PinnedMessage) -> Self {
        Self {
            event_id: message.event_id,
            sender_id: message.sender_id,
            sender_name: message.sender_name,
            timestamp_ms: message.timestamp_ms,
            content: message.content.into(),
        }
    }
}
