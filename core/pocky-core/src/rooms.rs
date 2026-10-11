use std::collections::HashSet;

use futures_util::{pin_mut, StreamExt};
use imbl::Vector;
use matrix_sdk::{
    latest_events::{LatestEventValue, LocalLatestEventValue},
    notification_settings::RoomNotificationMode,
    ruma::{
        api::client::receipt::create_receipt::v3::ReceiptType as CreateReceiptType,
        events::receipt::{ReceiptThread, ReceiptType},
        events::{
            room::message::MessageType, AnyMessageLikeEventContent, AnySyncMessageLikeEvent,
            AnySyncStateEvent, AnySyncTimelineEvent,
        },
        serde::Raw,
        EventId, OwnedRoomId, RoomId, UserId,
    },
    Client, Room,
};
use matrix_sdk_ui::{
    room_list_service::{
        filters::{new_filter_all, new_filter_deduplicate_versions, new_filter_non_left},
        RoomListItem,
    },
    sync_service::SyncService,
};
use tokio::sync::broadcast;

use crate::error::{CoreError, Result};

const PAGE_SIZE: usize = 200;
const PREVIEW_MAX_CHARS: usize = 200;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RoomSummary {
    pub id: String,
    pub name: String,
    pub avatar_url: Option<String>,
    pub last_message: Option<MessagePreview>,
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

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MessagePreview {
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub is_own: bool,
    pub kind: PreviewKind,
    pub delivery: Option<Delivery>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Delivery {
    Sending,
    Failed,
    Sent,
    Read,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize)]
#[serde(tag = "kind", content = "body", rename_all = "camelCase")]
pub enum PreviewKind {
    Text(String),
    Emote(String),
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

pub async fn watch_rooms(
    service: &SyncService,
    mut on_update: impl FnMut(Vec<RoomSummary>),
) -> Result<()> {
    let client = service.room_list_service().client().clone();
    let room_list = service
        .room_list_service()
        .all_rooms()
        .await
        .map_err(|e| CoreError::Sync(e.to_string()))?;

    let (stream, controller) = room_list.entries_with_dynamic_adapters(PAGE_SIZE);
    controller.set_filter(Box::new(new_filter_all(vec![
        Box::new(new_filter_non_left()),
        Box::new(new_filter_deduplicate_versions()),
    ])));
    pin_mut!(stream);

    let latest_events = client.latest_events().await;
    let settings = client.notification_settings().await;
    let mut setting_changes = settings.subscribe_to_changes();
    let mut listening = HashSet::<OwnedRoomId>::new();
    let mut rooms = Vector::<RoomListItem>::new();

    loop {
        tokio::select! {
            diffs = stream.next() => {
                let Some(diffs) = diffs else { break };
                for diff in diffs {
                    diff.apply(&mut rooms);
                }
            }
            changed = setting_changes.recv() => {
                if matches!(changed, Err(broadcast::error::RecvError::Closed)) {
                    break;
                }
            }
        }

        for room in &rooms {
            if listening.insert(room.room_id().to_owned()) {
                let _ = latest_events.listen_to_room(room.room_id()).await;
            }
        }

        let mut summaries = Vec::with_capacity(rooms.len());
        for room in &rooms {
            let muted = settings
                .get_user_defined_room_notification_mode(room.room_id())
                .await
                == Some(RoomNotificationMode::Mute);
            summaries.push(summarize(room, muted).await);
        }
        on_update(pinned_first(summaries));
    }

    Ok(())
}

fn pinned_first(mut summaries: Vec<RoomSummary>) -> Vec<RoomSummary> {
    summaries.sort_by_key(|summary| !summary.is_favourite);
    summaries
}

async fn summarize(room: &Room, muted: bool) -> RoomSummary {
    let counts = room.unread_notification_counts();
    let latest = room.latest_event();

    RoomSummary {
        id: room.room_id().to_string(),
        name: room_name(room).await,
        avatar_url: room_avatar(room),
        last_message: preview(room, &latest).await,
        timestamp_ms: latest.timestamp().map(|ts| ts.get().into()),
        unread_messages: room.num_unread_messages(),
        unread_mentions: counts.highlight_count.max(room.num_unread_mentions()),
        is_encrypted: room.encryption_state().is_encrypted(),
        is_direct: room.direct_targets_length() > 0 || looks_direct(room),
        direct_user_id: direct_user(room),
        is_invite: room.state() == matrix_sdk::RoomState::Invited,
        is_favourite: room.is_favourite(),
        is_muted: muted,
    }
}

fn looks_direct(room: &Room) -> bool {
    room.name().is_none()
        && room.active_members_count() <= 2
        && room.clone_info().heroes().len() == 1
}

pub(crate) fn direct_user(room: &Room) -> Option<String> {
    let targets = room.direct_targets();
    if targets.len() == 1 {
        return targets
            .iter()
            .next()
            .and_then(|target| target.as_user_id())
            .map(ToString::to_string);
    }
    if targets.is_empty() && looks_direct(room) {
        return room
            .clone_info()
            .heroes()
            .first()
            .map(|hero| hero.user_id.to_string());
    }
    None
}

pub(crate) fn room_avatar(room: &Room) -> Option<String> {
    if let Some(url) = room.avatar_url() {
        return Some(url.to_string());
    }
    if room.name().is_some() {
        return None;
    }

    let info = room.clone_info();
    match info.heroes() {
        [hero] => hero.avatar_url.as_ref().map(ToString::to_string),
        _ => None,
    }
}

async fn room_name(room: &Room) -> String {
    match room.cached_display_name() {
        Some(name) => name.to_string(),
        None => match room.display_name().await {
            Ok(name) => name.to_string(),
            Err(_) => room.room_id().to_string(),
        },
    }
}

async fn preview(room: &Room, latest: &LatestEventValue) -> Option<MessagePreview> {
    let own_id = room.own_user_id().to_owned();

    let (sender, kind, delivery) = match latest {
        LatestEventValue::None => return None,
        LatestEventValue::Remote(event) => {
            let kind = if event.kind.is_utd() {
                PreviewKind::Encrypted
            } else {
                preview_kind(event.raw())?
            };
            let sender = event.sender()?;
            let delivery = match event.event_id() {
                Some(event_id) if sender == own_id => Some(remote_delivery(room, event_id).await),
                _ => None,
            };
            (sender, kind, delivery)
        }
        LatestEventValue::RemoteInvite { inviter, .. } => (
            inviter.clone().unwrap_or(own_id.clone()),
            PreviewKind::Invite,
            None,
        ),
        LatestEventValue::LocalIsSending(value) => (
            own_id.clone(),
            local_preview_kind(value)?,
            Some(Delivery::Sending),
        ),
        LatestEventValue::LocalCannotBeSent(value) => (
            own_id.clone(),
            local_preview_kind(value)?,
            Some(Delivery::Failed),
        ),
        LatestEventValue::LocalHasBeenSent { value, .. } => (
            own_id.clone(),
            local_preview_kind(value)?,
            Some(Delivery::Sent),
        ),
    };

    Some(MessagePreview {
        sender_name: member_name(room, &sender).await,
        is_own: sender == own_id,
        sender_id: sender.to_string(),
        kind,
        delivery,
    })
}

async fn remote_delivery(room: &Room, event_id: &EventId) -> Delivery {
    let own_id = room.own_user_id();
    for thread in [ReceiptThread::Unthreaded, ReceiptThread::Main] {
        let receipts = room
            .load_event_receipts(ReceiptType::Read, &thread, event_id)
            .await
            .unwrap_or_default();
        if receipts.iter().any(|(user, _)| user != own_id) {
            return Delivery::Read;
        }
    }
    Delivery::Sent
}

pub async fn set_favourite(client: &Client, room_id: &str, favourite: bool) -> Result<()> {
    known_room(client, room_id)?
        .set_is_favourite(favourite, None)
        .await?;
    Ok(())
}

pub async fn set_muted(client: &Client, room_id: &str, muted: bool) -> Result<()> {
    let room = known_room(client, room_id)?;
    let settings = client.notification_settings().await;
    let result = if muted {
        settings
            .set_room_notification_mode(room.room_id(), RoomNotificationMode::Mute)
            .await
    } else {
        settings
            .delete_user_defined_room_rules(room.room_id())
            .await
    };
    result.map_err(|e| CoreError::Sync(e.to_string()))
}

pub async fn mark_room_read(client: &Client, room_id: &str) -> Result<()> {
    let room = known_room(client, room_id)?;
    if let Some(event_id) = room.latest_event().event_id() {
        room.send_single_receipt(CreateReceiptType::Read, ReceiptThread::Unthreaded, event_id)
            .await?;
    }
    if room.is_marked_unread() {
        room.set_unread_flag(false).await?;
    }
    Ok(())
}

pub async fn leave_room(client: &Client, room_id: &str) -> Result<()> {
    known_room(client, room_id)?.leave().await?;
    Ok(())
}

pub(crate) fn known_room(client: &Client, room_id: &str) -> Result<Room> {
    let room_id =
        RoomId::parse(room_id).map_err(|e| CoreError::Sync(format!("invalid room id: {e}")))?;
    client
        .get_room(&room_id)
        .ok_or_else(|| CoreError::Sync("unknown room".to_owned()))
}

pub(crate) async fn member_name(room: &Room, user_id: &UserId) -> Option<String> {
    let member = room.get_member_no_sync(user_id).await.ok().flatten()?;
    member.display_name().map(ToOwned::to_owned)
}

pub(crate) fn preview_kind(raw: &Raw<AnySyncTimelineEvent>) -> Option<PreviewKind> {
    let event = match raw.deserialize().ok()? {
        AnySyncTimelineEvent::MessageLike(event) => event,
        AnySyncTimelineEvent::State(AnySyncStateEvent::BeaconInfo(_)) => {
            return Some(PreviewKind::Location)
        }
        AnySyncTimelineEvent::State(_) => return None,
    };

    let kind = match event {
        AnySyncMessageLikeEvent::RoomMessage(message) => {
            message_kind(&message.as_original()?.content.msgtype)?
        }
        AnySyncMessageLikeEvent::RoomEncrypted(_) => PreviewKind::Encrypted,
        AnySyncMessageLikeEvent::Sticker(_) => PreviewKind::Sticker,
        AnySyncMessageLikeEvent::UnstablePollStart(_) => PreviewKind::Poll,
        _ => PreviewKind::Other,
    };

    Some(kind)
}

fn local_preview_kind(value: &LocalLatestEventValue) -> Option<PreviewKind> {
    let kind = match value.content.deserialize().ok()? {
        AnyMessageLikeEventContent::RoomMessage(content) => message_kind(&content.msgtype)?,
        AnyMessageLikeEventContent::Sticker(_) => PreviewKind::Sticker,
        _ => PreviewKind::Other,
    };

    Some(kind)
}

fn message_kind(msgtype: &MessageType) -> Option<PreviewKind> {
    let kind = match msgtype {
        MessageType::Text(text) => PreviewKind::Text(shorten(&text.body)),
        MessageType::Notice(notice) => PreviewKind::Text(shorten(&notice.body)),
        MessageType::Emote(emote) => PreviewKind::Emote(shorten(&emote.body)),
        MessageType::Image(_) => PreviewKind::Image,
        MessageType::Video(_) => PreviewKind::Video,
        MessageType::Audio(_) => PreviewKind::Audio,
        MessageType::File(_) => PreviewKind::File,
        MessageType::Location(_) => PreviewKind::Location,
        MessageType::VerificationRequest(_) => return None,
        other => PreviewKind::Text(shorten(other.body())),
    };
    Some(kind)
}

pub(crate) fn shorten(body: &str) -> String {
    let line = body
        .lines()
        .find(|l| !l.trim().is_empty())
        .unwrap_or("")
        .trim();
    match line.char_indices().nth(PREVIEW_MAX_CHARS) {
        Some((idx, _)) => format!("{}…", &line[..idx]),
        None => line.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn summary(id: &str, favourite: bool) -> RoomSummary {
        RoomSummary {
            id: id.to_owned(),
            name: id.to_owned(),
            avatar_url: None,
            last_message: None,
            timestamp_ms: None,
            unread_messages: 0,
            unread_mentions: 0,
            is_encrypted: false,
            is_direct: false,
            direct_user_id: None,
            is_invite: false,
            is_favourite: favourite,
            is_muted: false,
        }
    }

    #[test]
    fn favourites_go_first_and_keep_order() {
        let sorted = pinned_first(vec![
            summary("a", false),
            summary("b", true),
            summary("c", false),
            summary("d", true),
        ]);

        let ids: Vec<_> = sorted.iter().map(|room| room.id.as_str()).collect();
        assert_eq!(ids, ["b", "d", "a", "c"]);
    }

    #[tokio::test]
    async fn room_actions_need_known_room() {
        let dir = tempfile::tempdir().unwrap();
        let session = crate::session::Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "token".to_owned(),
            refresh_token: None,
        };
        let client = crate::auth::restore_client(&session, dir.path(), None)
            .await
            .unwrap();

        assert!(matches!(
            mark_room_read(&client, "!nope:example.org").await,
            Err(CoreError::Sync(msg)) if msg.contains("unknown room")
        ));
        assert!(matches!(
            set_favourite(&client, "not a room", true).await,
            Err(CoreError::Sync(msg)) if msg.contains("invalid room id")
        ));
    }

    fn raw(value: serde_json::Value) -> Raw<AnySyncTimelineEvent> {
        Raw::from_json_string(value.to_string()).unwrap()
    }

    fn message(content: serde_json::Value) -> Raw<AnySyncTimelineEvent> {
        raw(json!({
            "type": "m.room.message",
            "event_id": "$1",
            "sender": "@alice:example.org",
            "origin_server_ts": 1,
            "content": content,
        }))
    }

    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_room_list() {
        use std::time::Duration;

        use crate::{build_client, create_sync_service, login, logout, Server, SERVER_NAME};

        let _ = tracing_subscriber::fmt()
            .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
            .with_test_writer()
            .try_init();

        let user = std::env::var("POCKY_TEST_USER").expect("POCKY_TEST_USER");
        let password = std::env::var("POCKY_TEST_PASSWORD").expect("POCKY_TEST_PASSWORD");
        let dir = tempfile::tempdir().unwrap();

        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP test")
            .await
            .unwrap();

        let service = create_sync_service(&client).await.unwrap();
        service.start().await;

        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
        let watcher = Box::pin(watch_rooms(&service, |rooms| {
            let _ = tx.send(rooms);
        }));

        let collect = async {
            let mut last = Vec::new();
            while let Ok(Some(rooms)) =
                tokio::time::timeout(Duration::from_secs(10), rx.recv()).await
            {
                last = rooms;
            }
            last
        };

        let rooms = tokio::select! {
            result = watcher => panic!("watcher stopped: {result:?}"),
            rooms = collect => rooms,
        };
        assert!(!rooms.is_empty(), "no rooms received");
        println!("rooms: {}", rooms.len());

        for room in &rooms {
            println!(
                "{} | unread {} | encrypted {} | {:?}",
                room.name,
                room.unread_messages,
                room.is_encrypted,
                room.last_message.as_ref().map(|m| &m.kind)
            );
        }

        service.stop().await;
        logout(&client).await.unwrap();
    }

    #[test]
    fn text_preview() {
        let event = message(json!({ "msgtype": "m.text", "body": "ahoj" }));

        assert_eq!(
            preview_kind(&event),
            Some(PreviewKind::Text("ahoj".to_owned()))
        );
    }

    #[test]
    fn image_preview() {
        let event = message(json!({ "msgtype": "m.image", "body": "a.png", "url": "mxc://x/y" }));

        assert_eq!(preview_kind(&event), Some(PreviewKind::Image));
    }

    #[test]
    fn encrypted_preview() {
        let event = raw(json!({
            "type": "m.room.encrypted",
            "event_id": "$1",
            "sender": "@alice:example.org",
            "origin_server_ts": 1,
            "content": {
                "algorithm": "m.megolm.v1.aes-sha2",
                "ciphertext": "abc",
                "device_id": "DEV",
                "sender_key": "key",
                "session_id": "sess",
            },
        }));

        assert_eq!(preview_kind(&event), Some(PreviewKind::Encrypted));
    }

    #[test]
    fn live_location_preview() {
        let event = raw(json!({
            "type": "org.matrix.msc3672.beacon_info",
            "event_id": "$1",
            "sender": "@alice:example.org",
            "origin_server_ts": 1,
            "state_key": "@alice:example.org",
            "content": {
                "description": "",
                "live": true,
                "timeout": 60000,
                "org.matrix.msc3488.ts": 1,
                "org.matrix.msc3488.asset": { "type": "m.self" },
            },
        }));

        assert_eq!(preview_kind(&event), Some(PreviewKind::Location));
    }

    #[test]
    fn state_event_has_no_preview() {
        let event = raw(json!({
            "type": "m.room.topic",
            "event_id": "$1",
            "sender": "@alice:example.org",
            "origin_server_ts": 1,
            "state_key": "",
            "content": { "topic": "x" },
        }));

        assert_eq!(preview_kind(&event), None);
    }

    #[test]
    fn shorten_takes_first_line_and_limits_length() {
        assert_eq!(shorten("\n  první řádek \ndruhý"), "první řádek");

        let long = "č".repeat(PREVIEW_MAX_CHARS + 10);
        let short = shorten(&long);
        assert_eq!(short.chars().count(), PREVIEW_MAX_CHARS + 1);
        assert!(short.ends_with('…'));
    }
}
