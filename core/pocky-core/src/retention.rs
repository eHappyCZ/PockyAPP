use matrix_sdk::{
    deserialized_responses::RawAnySyncOrStrippedState,
    ruma::events::StateEventType,
    Client,
};
use serde_json::{json, Value};

use crate::{
    error::{CoreError, Result},
    rooms::known_room,
};

const EVENT_TYPE: &str = "m.room.retention";
const MAX_LIFETIME_SECONDS: u64 = 365 * 24 * 60 * 60;

pub async fn message_lifetime(client: &Client, room_id: &str) -> Result<Option<u64>> {
    let room = known_room(client, room_id)?;
    let event = room
        .get_state_event(StateEventType::from(EVENT_TYPE), "")
        .await?;
    let content = match event {
        Some(RawAnySyncOrStrippedState::Sync(raw)) => raw.get_field::<Value>("content"),
        Some(RawAnySyncOrStrippedState::Stripped(raw)) => raw.get_field::<Value>("content"),
        None => return Ok(None),
    }
    .map_err(|e| CoreError::Sync(e.to_string()))?;
    Ok(content.as_ref().and_then(lifetime_seconds))
}

pub async fn can_set_message_lifetime(client: &Client, room_id: &str) -> Result<bool> {
    let room = known_room(client, room_id)?;
    let own = client.user_id().ok_or(CoreError::NotLoggedIn)?;
    let levels = room.power_levels_or_default().await;
    Ok(levels.user_can_send_state(own, StateEventType::from(EVENT_TYPE)))
}

pub async fn set_message_lifetime(
    client: &Client,
    room_id: &str,
    seconds: Option<u64>,
) -> Result<()> {
    let room = known_room(client, room_id)?;
    let content = match seconds {
        None => json!({}),
        Some(0) => return Err(CoreError::InvalidName("lifetime".to_owned())),
        Some(seconds) => {
            let milliseconds = seconds.min(MAX_LIFETIME_SECONDS) * 1000;
            json!({ "max_lifetime": milliseconds, "expire_on_clients": true })
        }
    };
    room.send_state_event_raw(EVENT_TYPE, "", content).await?;
    Ok(())
}

fn lifetime_seconds(content: &Value) -> Option<u64> {
    content
        .get("max_lifetime")
        .and_then(Value::as_u64)
        .filter(|&ms| ms > 0)
        .map(|ms| ms / 1000)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_lifetime_in_seconds() {
        assert_eq!(lifetime_seconds(&json!({ "max_lifetime": 86_400_000u64 })), Some(86_400));
        assert_eq!(lifetime_seconds(&json!({ "max_lifetime": 0 })), None);
        assert_eq!(lifetime_seconds(&json!({})), None);
    }
}
