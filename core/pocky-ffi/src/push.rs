use pocky_core::PushNotification;

use crate::rooms::MessageContent;

#[derive(Debug, Clone, uniffi::Record)]
pub struct NotificationInfo {
    pub room_id: String,
    pub event_id: String,
    pub room_name: String,
    pub room_avatar: Option<String>,
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub sender_avatar: Option<String>,
    pub is_direct: bool,
    pub is_invite: bool,
    pub content: MessageContent,
}

impl From<PushNotification> for NotificationInfo {
    fn from(notification: PushNotification) -> Self {
        Self {
            room_id: notification.room_id,
            event_id: notification.event_id,
            room_name: notification.room_name,
            room_avatar: notification.room_avatar,
            sender_id: notification.sender_id,
            sender_name: notification.sender_name,
            sender_avatar: notification.sender_avatar,
            is_direct: notification.is_direct,
            is_invite: notification.is_invite,
            content: notification.content.into(),
        }
    }
}
