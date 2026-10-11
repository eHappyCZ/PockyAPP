use matrix_sdk::{
    event_handler::EventHandlerDropGuard,
    ruma::{events::typing::SyncTypingEvent, OwnedRoomId, OwnedUserId, RoomId},
    Client, Room,
};
use tokio::sync::mpsc;

use crate::{
    error::{CoreError, Result},
    preferences::load_preferences,
    rooms::member_name,
};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TypingUser {
    pub user_id: String,
    pub name: Option<String>,
}

pub async fn watch_typing(
    client: &Client,
    mut on_update: impl FnMut(String, Vec<TypingUser>),
) -> Result<()> {
    if !load_preferences(client).await?.share_typing {
        return Ok(());
    }
    let own = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();

    let (sender, mut receiver) = mpsc::unbounded_channel::<(Room, Vec<OwnedUserId>)>();
    let handle = client.add_event_handler(move |event: SyncTypingEvent, room: Room| {
        let sender = sender.clone();
        let own = own.clone();
        async move {
            let users = event
                .content
                .user_ids
                .into_iter()
                .filter(|id| *id != own)
                .collect();
            let _ = sender.send((room, users));
        }
    });
    let _guard: EventHandlerDropGuard = client.event_handler_drop_guard(handle);

    while let Some((room, ids)) = receiver.recv().await {
        let mut users = Vec::with_capacity(ids.len());
        for id in ids {
            users.push(TypingUser {
                name: member_name(&room, &id).await,
                user_id: id.to_string(),
            });
        }
        on_update(room.room_id().to_string(), users);
    }

    Ok(())
}

pub async fn send_typing(client: &Client, room_id: &str, typing: bool) -> Result<()> {
    if !load_preferences(client).await?.share_typing {
        return Ok(());
    }
    let room = joined_room(client, room_id)?;
    room.typing_notice(typing).await?;
    Ok(())
}

fn joined_room(client: &Client, room_id: &str) -> Result<Room> {
    let room_id: OwnedRoomId =
        RoomId::parse(room_id).map_err(|e| CoreError::Timeline(format!("invalid room id: {e}")))?;
    client
        .get_room(&room_id)
        .ok_or_else(|| CoreError::Timeline("unknown room".to_owned()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        auth::restore_client,
        preferences::{save_preferences, Preferences},
        session::Session,
    };

    async fn client(dir: &tempfile::TempDir) -> Client {
        let session = Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "token".to_owned(),
            refresh_token: None,
        };
        restore_client(&session, dir.path(), None).await.unwrap()
    }

    #[tokio::test]
    async fn typing_is_silent_when_not_shared() {
        let dir = tempfile::tempdir().unwrap();
        let client = client(&dir).await;
        let prefs = Preferences {
            share_typing: false,
            ..Preferences::default()
        };
        save_preferences(&client, &prefs).await.unwrap();

        let mut updates = 0;
        watch_typing(&client, |_, _| updates += 1).await.unwrap();

        assert_eq!(updates, 0);
        assert!(send_typing(&client, "!nope:example.org", true)
            .await
            .is_ok());
    }

    #[tokio::test]
    async fn typing_needs_known_room() {
        let dir = tempfile::tempdir().unwrap();
        let client = client(&dir).await;

        let result = send_typing(&client, "!nope:example.org", true).await;

        assert!(matches!(result, Err(CoreError::Timeline(msg)) if msg.contains("unknown room")));
    }
}
