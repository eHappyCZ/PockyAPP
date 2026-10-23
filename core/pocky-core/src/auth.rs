use matrix_sdk::{
    ruma::{
        api::{
            client::{
                account::register,
                uiaa::{
                    AuthData, AuthType, Dummy, MatrixUserIdentifier, Password, RegistrationToken,
                    UiaaInfo, UserIdentifier,
                },
            },
            error::ErrorKind,
        },
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

const MAX_REGISTRATION_STEPS: usize = 6;

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

const MIN_PASSWORD_CHARS: usize = 8;
const MAX_USERNAME_CHARS: usize = 64;

pub async fn register(
    client: &Client,
    username: &str,
    password: &str,
    invite_code: &str,
    device_name: &str,
) -> Result<Session> {
    let username = clean_username(username)?;
    if password.chars().count() < MIN_PASSWORD_CHARS {
        return Err(CoreError::WeakPassword);
    }
    let invite_code = invite_code.trim();
    if invite_code.is_empty() {
        return Err(CoreError::InvalidInviteCode);
    }

    let mut request = register::v3::Request::new();
    request.username = Some(username);
    request.password = Some(password.to_owned());
    request.initial_device_display_name = Some(device_name.to_owned());

    let mut token_sent = false;
    for _ in 0..MAX_REGISTRATION_STEPS {
        let err = match client.matrix_auth().register(request.clone()).await {
            Ok(_) => return Session::from_client(client).ok_or(CoreError::NotLoggedIn),
            Err(err) => err,
        };
        let Some(info) = err.as_uiaa_response() else {
            return Err(registration_error(err));
        };
        if token_sent && !info.completed.contains(&AuthType::RegistrationToken) {
            return Err(CoreError::InvalidInviteCode);
        }

        let session = info.session.clone();
        let stage = next_stage(info)?;
        request.auth = Some(match stage {
            AuthType::RegistrationToken => {
                token_sent = true;
                let mut data = RegistrationToken::new(invite_code.to_owned());
                data.session = session;
                AuthData::RegistrationToken(data)
            }
            _ => {
                let mut data = Dummy::new();
                data.session = session;
                AuthData::Dummy(data)
            }
        });
    }
    Err(CoreError::RegistrationUnavailable)
}

fn clean_username(input: &str) -> Result<String> {
    let name = input.trim().trim_start_matches('@');
    let name = name.split(':').next().unwrap_or_default().to_lowercase();
    let allowed = |c: char| c.is_ascii_lowercase() || c.is_ascii_digit() || "._=-/".contains(c);
    if name.is_empty() || name.chars().count() > MAX_USERNAME_CHARS || !name.chars().all(allowed) {
        return Err(CoreError::InvalidName("username".to_owned()));
    }
    Ok(name)
}

fn next_stage(info: &UiaaInfo) -> Result<AuthType> {
    let supported = |stage: &AuthType| matches!(stage, AuthType::RegistrationToken | AuthType::Dummy);
    info.flows
        .iter()
        .filter(|flow| flow.stages.iter().all(supported))
        .filter(|flow| info.completed.iter().all(|done| flow.stages.contains(done)))
        .find_map(|flow| {
            flow.stages
                .iter()
                .find(|stage| !info.completed.contains(stage))
                .cloned()
        })
        .ok_or(CoreError::RegistrationUnavailable)
}

fn registration_error(err: matrix_sdk::Error) -> CoreError {
    match err.client_api_error_kind() {
        Some(ErrorKind::UserInUse) => CoreError::UsernameTaken,
        Some(ErrorKind::InvalidUsername) => CoreError::InvalidName("username".to_owned()),
        Some(ErrorKind::Forbidden) => CoreError::RegistrationUnavailable,
        _ => err.into(),
    }
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


    fn info(json: &str) -> UiaaInfo {
        serde_json::from_str(json).unwrap()
    }

    #[test]
    fn usernames_are_cleaned() {
        assert_eq!(clean_username(" @Karel:pokecio.com ").unwrap(), "karel");
        assert_eq!(clean_username("jana.nova").unwrap(), "jana.nova");
        assert!(clean_username("").is_err());
        assert!(clean_username("jana nova").is_err());
        assert!(clean_username("jaňa").is_err());
    }

    #[test]
    fn registration_walks_through_the_stages() {
        let start = info(
            r#"{"flows":[{"stages":["m.login.registration_token","m.login.dummy"]}],"session":"s"}"#,
        );
        assert_eq!(next_stage(&start).unwrap(), AuthType::RegistrationToken);

        let after_token = info(
            r#"{"flows":[{"stages":["m.login.registration_token","m.login.dummy"]}],
                "completed":["m.login.registration_token"],"session":"s"}"#,
        );
        assert_eq!(next_stage(&after_token).unwrap(), AuthType::Dummy);
    }

    #[test]
    fn unsupported_stages_are_refused() {
        let captcha = info(r#"{"flows":[{"stages":["m.login.recaptcha"]}],"session":"s"}"#);
        assert!(matches!(
            next_stage(&captcha),
            Err(CoreError::RegistrationUnavailable)
        ));

        let either = info(
            r#"{"flows":[{"stages":["m.login.recaptcha"]},{"stages":["m.login.dummy"]}],"session":"s"}"#,
        );
        assert_eq!(next_stage(&either).unwrap(), AuthType::Dummy);
    }
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
