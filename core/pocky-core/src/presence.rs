use std::time::Duration;

use matrix_sdk::{
    ruma::{api::client::presence::get_presence, presence::PresenceState, UserId},
    Client,
};

use crate::{
    error::{CoreError, Result},
    preferences::load_preferences,
};

const IDLE_AFTER: Duration = Duration::from_secs(10 * 60);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Presence {
    Online,
    Unavailable,
    Offline,
    Unknown,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UserPresence {
    pub user_id: String,
    pub presence: Presence,
    pub last_active_ms: Option<u64>,
    pub status: Option<String>,
}

impl UserPresence {
    fn unknown(user_id: &UserId) -> Self {
        Self {
            user_id: user_id.to_string(),
            presence: Presence::Unknown,
            last_active_ms: None,
            status: None,
        }
    }
}

pub async fn apply_presence_preference(client: &Client) -> Result<()> {
    let share = load_preferences(client).await?.share_presence;
    client.set_presence(state(share), None, false).await?;
    Ok(())
}

pub(crate) async fn share_presence(client: &Client, share: bool, changed: bool) {
    if client.session_meta().is_none() {
        return;
    }
    let status = crate::status::current_text(client).await;
    let _ = client.set_presence(state(share), status, changed).await;
}

pub async fn fetch_presence(client: &Client, user_id: &str) -> Result<UserPresence> {
    let user_id =
        UserId::parse(user_id).map_err(|e| CoreError::Sync(format!("invalid user id: {e}")))?;
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }
    let share = load_preferences(client).await?.share_presence;

    let response = match client
        .send(get_presence::v3::Request::new(user_id.clone()))
        .await
    {
        Ok(response) => response,
        Err(_) => return Ok(UserPresence::unknown(&user_id)),
    };

    let status = response
        .status_msg
        .map(|text| text.trim().to_owned())
        .filter(|text| !text.is_empty());
    if !share {
        return Ok(UserPresence {
            status,
            ..UserPresence::unknown(&user_id)
        });
    }

    Ok(UserPresence {
        user_id: user_id.to_string(),
        presence: classify(
            &response.presence,
            response.currently_active,
            response.last_active_ago,
        ),
        last_active_ms: response.last_active_ago.map(|ago| ago.as_millis() as u64),
        status,
    })
}

pub(crate) fn state(share: bool) -> PresenceState {
    if share {
        PresenceState::Online
    } else {
        PresenceState::Offline
    }
}

fn classify(
    state: &PresenceState,
    active: Option<bool>,
    last_active: Option<Duration>,
) -> Presence {
    match state {
        PresenceState::Online
            if active == Some(false) && last_active.is_some_and(|ago| ago > IDLE_AFTER) =>
        {
            Presence::Unavailable
        }
        PresenceState::Online => Presence::Online,
        PresenceState::Unavailable => Presence::Unavailable,
        PresenceState::Offline => Presence::Offline,
        _ => Presence::Unknown,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{auth::restore_client, session::Session};

    fn session() -> Session {
        Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "token".to_owned(),
            refresh_token: None,
        }
    }

    #[test]
    fn idle_online_user_counts_as_unavailable() {
        let long_ago = Some(Duration::from_secs(3600));

        assert_eq!(
            classify(&PresenceState::Online, Some(true), None),
            Presence::Online
        );
        assert_eq!(
            classify(&PresenceState::Online, Some(false), long_ago),
            Presence::Unavailable
        );
        assert_eq!(
            classify(&PresenceState::Unavailable, None, None),
            Presence::Unavailable
        );
        assert_eq!(
            classify(&PresenceState::Offline, None, None),
            Presence::Offline
        );
    }

    #[tokio::test]
    async fn hidden_when_own_presence_is_not_shared() {
        let dir = tempfile::tempdir().unwrap();
        let client = restore_client(&session(), dir.path(), None).await.unwrap();

        let result = fetch_presence(&client, "@bob:example.org").await.unwrap();

        assert_eq!(result.presence, Presence::Unknown);
    }

    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_server_reports_presence() {
        use crate::{
            auth::{login, logout},
            client::{build_client, Server, SERVER_NAME},
            preferences::{save_preferences, Preferences},
        };

        let user = std::env::var("POCKY_TEST_USER").expect("POCKY_TEST_USER");
        let password = std::env::var("POCKY_TEST_PASSWORD").expect("POCKY_TEST_PASSWORD");
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP presence test")
            .await
            .unwrap();

        let prefs = Preferences {
            share_presence: true,
            ..Preferences::default()
        };
        save_preferences(&client, &prefs).await.unwrap();
        let own = client.user_id().unwrap().to_string();
        let result = fetch_presence(&client, &own).await.unwrap();
        println!("presence of {own}: {:?}", result.presence);

        logout(&client).await.unwrap();
        assert_eq!(
            result.presence,
            Presence::Online,
            "server does not report presence (disabled on homeserver?)"
        );
    }
}
