use matrix_sdk::{
    ruma::{
        api::client::uiaa::{AuthData, MatrixUserIdentifier, Password, UserIdentifier},
        UserId,
    },
    Client, SessionChange,
};
use tokio::sync::broadcast::error::RecvError;

use crate::{
    client::{build_client, Server, StoreLocation},
    error::{CoreError, Result},
    session::Session,
};

pub async fn login(
    client: &Client,
    username: &str,
    password: &str,
    device_name: &str,
) -> Result<Session> {
    let username = username.trim();
    if username.is_empty() || password.is_empty() {
        return Err(CoreError::InvalidCredentials);
    }

    client
        .matrix_auth()
        .login_username(username, password)
        .initial_device_display_name(device_name)
        .send()
        .await?;

    Session::from_client(client).ok_or(CoreError::NotLoggedIn)
}

pub async fn restore_session(client: &Client, session: &Session) -> Result<()> {
    if client.session_meta().is_some() {
        return Ok(());
    }

    client.restore_session(session.to_matrix()?).await?;
    Ok(())
}

pub async fn restore_client(
    session: &Session,
    data_dir: &StoreLocation,
    passphrase: Option<&str>,
) -> Result<Client> {
    let client = build_client(Server::Url(&session.homeserver_url), data_dir, passphrase).await?;
    restore_session(&client, session).await?;
    Ok(client)
}

pub async fn logout(client: &Client) -> Result<()> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }

    client.logout().await?;
    Ok(())
}

pub async fn delete_account(client: &Client, password: &str) -> Result<()> {
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    if password.is_empty() {
        return Err(CoreError::InvalidCredentials);
    }
    let account = client.account();

    let session = match account.deactivate(None, None, true).await {
        Ok(_) => return Ok(()),
        Err(err) => match err.as_uiaa_response() {
            Some(info) => info.session.clone(),
            None => return Err(err.into()),
        },
    };

    let mut auth = Password::new(
        UserIdentifier::Matrix(MatrixUserIdentifier::new(user_id.to_string())),
        password.to_owned(),
    );
    auth.session = session;
    account
        .deactivate(None, Some(AuthData::Password(auth)), true)
        .await?;
    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SessionEvent {
    Expired,
    Refreshed,
}

pub async fn watch_session(client: &Client, mut on_event: impl FnMut(SessionEvent)) {
    let mut changes = client.subscribe_to_session_changes();
    loop {
        match changes.recv().await {
            Ok(SessionChange::UnknownToken(_)) => on_event(SessionEvent::Expired),
            Ok(SessionChange::TokensRefreshed) => on_event(SessionEvent::Refreshed),
            Err(RecvError::Lagged(_)) => continue,
            Err(RecvError::Closed) => break,
        }
    }
}

pub fn current_session(client: &Client) -> Option<Session> {
    Session::from_client(client)
}

pub async fn display_name(client: &Client) -> Result<String> {
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let name = client.account().get_display_name().await.ok().flatten();

    Ok(readable_name(name.as_deref(), &user_id))
}

fn readable_name(name: Option<&str>, user_id: &UserId) -> String {
    name.map(str::trim)
        .filter(|name| !name.is_empty() && *name != user_id.as_str())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| user_id.localpart().to_owned())
}

pub async fn avatar_url(client: &Client) -> Result<Option<String>> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }
    let account = client.account();
    let url = match account.get_avatar_url().await {
        Ok(url) => url,
        Err(_) => account.get_cached_avatar_url().await.ok().flatten(),
    };
    Ok(url.map(|url| url.to_string()))
}

#[cfg(test)]
mod tests {
    use std::path::Path;

    use super::*;
    use crate::client::SERVER_NAME;

    #[test]
    fn full_user_id_is_not_a_display_name() {
        let user_id = UserId::parse("@pokeapp:pokecio.com").unwrap();
        assert_eq!(
            readable_name(Some("@pokeapp:pokecio.com"), &user_id),
            "pokeapp"
        );
        assert_eq!(readable_name(Some("  "), &user_id), "pokeapp");
        assert_eq!(readable_name(None, &user_id), "pokeapp");
        assert_eq!(readable_name(Some(" Karel "), &user_id), "Karel");
    }

    fn sample_session() -> Session {
        Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "token".to_owned(),
            refresh_token: None,
        }
    }

    async fn offline_client(dir: &Path) -> Client {
        build_client(Server::Url("https://matrix.example.org"), dir, None)
            .await
            .unwrap()
    }

    #[tokio::test]
    async fn login_rejects_empty_credentials() {
        let dir = tempfile::tempdir().unwrap();
        let client = offline_client(dir.path()).await;

        let empty_user = login(&client, "  ", "pass", "test").await;
        let empty_pass = login(&client, "alice", "", "test").await;

        assert!(matches!(empty_user, Err(CoreError::InvalidCredentials)));
        assert!(matches!(empty_pass, Err(CoreError::InvalidCredentials)));
    }

    #[tokio::test]
    async fn restores_session_offline() {
        let dir = tempfile::tempdir().unwrap();
        let session = sample_session();

        let client = restore_client(&session, dir.path(), Some("pass"))
            .await
            .unwrap();

        assert_eq!(current_session(&client), Some(session));
    }

    #[tokio::test]
    async fn logout_requires_session() {
        let dir = tempfile::tempdir().unwrap();
        let client = offline_client(dir.path()).await;

        assert!(matches!(logout(&client).await, Err(CoreError::NotLoggedIn)));
    }

    #[tokio::test]
    async fn display_name_requires_session() {
        let dir = tempfile::tempdir().unwrap();
        let client = offline_client(dir.path()).await;

        assert!(matches!(
            display_name(&client).await,
            Err(CoreError::NotLoggedIn)
        ));
    }

    #[tokio::test]
    #[ignore = "needs network"]
    async fn live_wrong_password_is_rejected() {
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();

        let result = login(&client, "pocky-neexistuje", "spatneheslo", "PockyAPP test").await;

        assert!(
            matches!(result, Err(CoreError::InvalidCredentials)),
            "{result:?}"
        );
    }

    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_login_and_logout() {
        let user = std::env::var("POCKY_TEST_USER").expect("POCKY_TEST_USER");
        let password = std::env::var("POCKY_TEST_PASSWORD").expect("POCKY_TEST_PASSWORD");
        let dir = tempfile::tempdir().unwrap();

        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        assert_eq!(client.homeserver().as_str(), "https://matrix.pokecio.com/");

        let session = login(&client, &user, &password, "PockyAPP test")
            .await
            .unwrap();
        assert!(session.user_id.ends_with(":pokecio.com"));

        logout(&client).await.unwrap();
        assert!(client.whoami().await.is_err());
    }
}
