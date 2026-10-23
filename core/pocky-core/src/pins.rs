use matrix_sdk::{
    ruma::{events::StateEventType, EventId},
    Client,
};

use crate::{
    error::{CoreError, Result},
    rooms::{known_room, member_name, preview_kind, PreviewKind},
};

#[derive(Debug, Clone, PartialEq, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PinnedMessage {
    pub event_id: String,
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub timestamp_ms: u64,
    pub content: PreviewKind,
}

pub async fn pinned_messages(client: &Client, room_id: &str) -> Result<Vec<PinnedMessage>> {
    let room = known_room(client, room_id)?;
    let ids = room.pinned_event_ids().unwrap_or_default();

    let mut messages = Vec::with_capacity(ids.len());
    for id in ids {
        let Ok(event) = room.event(&id, None).await else {
            continue;
        };
        let raw = event.raw();
        let Ok(parsed) = raw.deserialize() else {
            continue;
        };
        let sender = parsed.sender().to_owned();
        messages.push(PinnedMessage {
            event_id: id.to_string(),
            sender_name: member_name(&room, &sender).await,
            sender_id: sender.to_string(),
            timestamp_ms: u64::from(parsed.origin_server_ts().get()),
            content: preview_kind(raw).unwrap_or(PreviewKind::Other),
        });
    }
    Ok(messages)
}

pub async fn can_pin_messages(client: &Client, room_id: &str) -> Result<bool> {
    let room = known_room(client, room_id)?;
    let own = client.user_id().ok_or(CoreError::NotLoggedIn)?;
    let levels = room.power_levels_or_default().await;
    Ok(levels.user_can_send_state(own, StateEventType::RoomPinnedEvents))
}

pub async fn pin_message(client: &Client, room_id: &str, event_id: &str) -> Result<()> {
    let room = known_room(client, room_id)?;
    room.pin_event(&parse_event(event_id)?).await?;
    Ok(())
}

pub async fn unpin_message(client: &Client, room_id: &str, event_id: &str) -> Result<()> {
    let room = known_room(client, room_id)?;
    room.unpin_event(&parse_event(event_id)?).await?;
    Ok(())
}

fn parse_event(event_id: &str) -> Result<matrix_sdk::ruma::OwnedEventId> {
    EventId::parse(event_id.trim()).map_err(|e| CoreError::Sync(format!("invalid event id: {e}")))
}
