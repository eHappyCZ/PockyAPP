use matrix_sdk::{
    ruma::{
        api::client::room::{create_room, Visibility},
        events::{room::encryption::RoomEncryptionEventContent, InitialStateEvent},
        OwnedUserId, UserId,
    },
    Client,
};

use crate::{
    error::{CoreError, Result},
    rooms::{direct_user, known_room},
};

const SEARCH_LIMIT: u64 = 20;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Person {
    pub user_id: String,
    pub display_name: Option<String>,
    pub avatar_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InviteInfo {
    pub room_id: String,
    pub inviter_id: String,
    pub inviter_name: Option<String>,
    pub inviter_avatar: Option<String>,
}

pub async fn search_people(client: &Client, term: &str) -> Result<Vec<Person>> {
    let own = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let term = term.trim();
    if term.is_empty() {
        return Ok(Vec::new());
    }

    let mut people: Vec<Person> = client
        .search_users(term, SEARCH_LIMIT)
        .await?
        .results
        .into_iter()
        .map(|user| Person {
            user_id: user.user_id.to_string(),
            display_name: user.display_name,
            avatar_url: user.avatar_url.map(|url| url.to_string()),
        })
        .collect();

    if let Some(exact) = exact_user(term, &own) {
        if !people.iter().any(|person| person.user_id == exact.as_str()) {
            if let Ok(profile) = client.account().fetch_user_profile_of(&exact).await {
                let text = |field: &str| {
                    profile
                        .get(field)
                        .and_then(|v| v.as_str())
                        .map(ToOwned::to_owned)
                };
                people.insert(
                    0,
                    Person {
                        user_id: exact.to_string(),
                        display_name: text("displayname"),
                        avatar_url: text("avatar_url"),
                    },
                );
            }
        }
    }

    people.retain(|person| person.user_id != own.as_str());
    Ok(people)
}

pub async fn open_direct_chat(client: &Client, user_id: &str) -> Result<String> {
    let user_id = parse_user(user_id)?;
    if client.user_id() == Some(&*user_id) {
        return Err(CoreError::Sync("cannot chat with yourself".to_owned()));
    }

    if let Some(room) = client.get_dm_room(&user_id) {
        return Ok(room.room_id().to_string());
    }
    let existing = client
        .joined_rooms()
        .into_iter()
        .find(|room| direct_user(room).as_deref() == Some(user_id.as_str()));
    if let Some(room) = existing {
        return Ok(room.room_id().to_string());
    }

    let room = client.create_dm(&user_id).await?;
    Ok(room.room_id().to_string())
}

pub async fn create_group(client: &Client, name: &str, members: &[String]) -> Result<String> {
    let name = name.split_whitespace().collect::<Vec<_>>().join(" ");
    if name.is_empty() {
        return Err(CoreError::InvalidName("empty".to_owned()));
    }
    let invite = members
        .iter()
        .map(|member| parse_user(member))
        .collect::<Result<Vec<_>>>()?;

    let mut request = create_room::v3::Request::new();
    request.name = Some(name);
    request.invite = invite;
    request.visibility = Visibility::Private;
    request.preset = Some(create_room::v3::RoomPreset::PrivateChat);
    request.initial_state = vec![InitialStateEvent::with_empty_state_key(
        RoomEncryptionEventContent::with_recommended_defaults(),
    )
    .to_raw_any()];

    let room = client.create_room(request).await?;
    Ok(room.room_id().to_string())
}

pub async fn accept_invite(client: &Client, room_id: &str) -> Result<()> {
    known_room(client, room_id)?.join().await?;
    Ok(())
}

pub async fn decline_invite(client: &Client, room_id: &str) -> Result<()> {
    known_room(client, room_id)?.leave().await?;
    Ok(())
}

pub async fn invite_info(client: &Client, room_id: &str) -> Result<InviteInfo> {
    let room = known_room(client, room_id)?;
    let details = room.invite_details().await?;
    let inviter = details.inviter.as_ref();

    Ok(InviteInfo {
        room_id: room.room_id().to_string(),
        inviter_id: details.inviter_id.to_string(),
        inviter_name: inviter.and_then(|member| member.display_name().map(ToOwned::to_owned)),
        inviter_avatar: inviter.and_then(|member| member.avatar_url().map(ToString::to_string)),
    })
}

fn exact_user(term: &str, own: &UserId) -> Option<OwnedUserId> {
    let candidate = if term.starts_with('@') {
        term.to_owned()
    } else if term
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || "._=-/".contains(c))
    {
        format!("@{}:{}", term.to_lowercase(), own.server_name())
    } else {
        return None;
    };
    UserId::parse(candidate).ok()
}

fn parse_user(user_id: &str) -> Result<OwnedUserId> {
    UserId::parse(user_id.trim()).map_err(|e| CoreError::Sync(format!("invalid user id: {e}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn own() -> OwnedUserId {
        UserId::parse("@me:pokecio.com").unwrap()
    }

    #[test]
    fn plain_name_becomes_local_user() {
        assert_eq!(
            exact_user("Karel", &own()).unwrap().as_str(),
            "@karel:pokecio.com"
        );
        assert_eq!(
            exact_user("@jana:matrix.org", &own()).unwrap().as_str(),
            "@jana:matrix.org"
        );
    }

    #[test]
    fn names_with_spaces_are_not_user_ids() {
        assert!(exact_user("Karel Z", &own()).is_none());
        assert!(exact_user("Čeněk", &own()).is_none());
    }

    #[test]
    fn rejects_broken_user_id() {
        assert!(matches!(parse_user("nobody"), Err(CoreError::Sync(_))));
    }

    #[tokio::test]
    async fn group_needs_name() {
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
            create_group(&client, "   ", &[]).await,
            Err(CoreError::InvalidName(_))
        ));
        assert!(matches!(
            open_direct_chat(&client, "@alice:example.org").await,
            Err(CoreError::Sync(_))
        ));
    }
}
