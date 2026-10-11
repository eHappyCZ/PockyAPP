use std::sync::Arc;

use matrix_sdk::{
    ruma::{
        api::client::push::{PusherIds, PusherInit, PusherKind},
        push::{HttpPusherData, PushFormat},
        EventId, RoomId,
    },
    Client,
};
use matrix_sdk_ui::{
    notification_client::{
        NotificationClient, NotificationEvent, NotificationProcessSetup, NotificationStatus,
        RawNotificationEvent,
    },
    sync_service::SyncService,
};

use crate::{
    error::{CoreError, Result},
    rooms::{preview_kind, PreviewKind},
};

pub const PUSH_GATEWAY: &str = "https://push.pokecio.com/_matrix/push/v1/notify";
pub const PUSH_APP_ID: &str = "com.pokecio.app.android";
const APP_NAME: &str = "PockyAPP";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PushNotification {
    pub room_id: String,
    pub event_id: String,
    pub room_name: String,
    pub room_avatar: Option<String>,
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub sender_avatar: Option<String>,
    pub is_direct: bool,
    pub is_invite: bool,
    pub content: PreviewKind,
}

pub async fn register_pusher(
    client: &Client,
    push_key: &str,
    device_name: &str,
    lang: &str,
) -> Result<()> {
    if push_key.is_empty() {
        return Err(CoreError::Sync("empty push key".to_owned()));
    }
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }

    let mut data = HttpPusherData::new(PUSH_GATEWAY.to_owned());
    data.format = Some(PushFormat::EventIdOnly);

    let pusher = PusherInit {
        ids: PusherIds::new(push_key.to_owned(), PUSH_APP_ID.to_owned()),
        kind: PusherKind::Http(data),
        app_display_name: APP_NAME.to_owned(),
        device_display_name: device_name.to_owned(),
        profile_tag: None,
        lang: lang.to_owned(),
    };
    client.pusher().set(pusher.into(), false).await?;
    Ok(())
}

pub async fn unregister_pusher(client: &Client, push_key: &str) -> Result<()> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }
    client
        .pusher()
        .delete(PusherIds::new(push_key.to_owned(), PUSH_APP_ID.to_owned()))
        .await?;
    Ok(())
}

pub async fn fetch_notification(
    client: &Client,
    sync: Arc<SyncService>,
    room_id: &str,
    event_id: &str,
) -> Result<Option<PushNotification>> {
    let room_id =
        RoomId::parse(room_id).map_err(|e| CoreError::Sync(format!("invalid room id: {e}")))?;
    let event_id =
        EventId::parse(event_id).map_err(|e| CoreError::Sync(format!("invalid event id: {e}")))?;

    let notifications = NotificationClient::new(
        client.clone(),
        NotificationProcessSetup::SingleProcess { sync_service: sync },
    )
    .await
    .map_err(|e| CoreError::Sync(e.to_string()))?;

    let item = match notifications
        .get_notification(&room_id, &event_id)
        .await
        .map_err(|e| CoreError::Sync(e.to_string()))?
    {
        NotificationStatus::Event(item) => item,
        _ => return Ok(None),
    };

    let is_invite = matches!(item.event, NotificationEvent::Invite(_));
    let content = match &item.raw_event {
        RawNotificationEvent::Timeline(raw) => preview_kind(raw).unwrap_or(PreviewKind::Other),
        RawNotificationEvent::Invite(_) => PreviewKind::Invite,
    };

    Ok(Some(PushNotification {
        room_id: room_id.to_string(),
        event_id: event_id.to_string(),
        room_name: item.room_computed_display_name.clone(),
        room_avatar: item.room_avatar_url.clone(),
        sender_id: item.event.sender().to_string(),
        sender_name: item.sender_display_name.clone(),
        sender_avatar: item.sender_avatar_url.clone(),
        is_direct: item.is_direct_message_room,
        is_invite,
        content,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::client::{build_client, Server};

    #[tokio::test]
    async fn pusher_needs_session() {
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Url("https://matrix.example.org"), dir.path(), None)
            .await
            .unwrap();

        let result = register_pusher(&client, "token", "Pixel", "cs").await;

        assert!(matches!(result, Err(CoreError::NotLoggedIn)));
    }

    #[tokio::test]
    async fn pusher_needs_key() {
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Url("https://matrix.example.org"), dir.path(), None)
            .await
            .unwrap();

        assert!(matches!(
            register_pusher(&client, "", "Pixel", "cs").await,
            Err(CoreError::Sync(_))
        ));
    }

    #[test]
    fn gateway_uses_https() {
        assert!(PUSH_GATEWAY.starts_with("https://"));
        assert!(PUSH_GATEWAY.ends_with("/_matrix/push/v1/notify"));
    }
}
