use matrix_sdk::{ruma::api::error::ErrorKind, ClientBuildError, HttpError};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum CoreError {
    #[error("invalid username or password")]
    InvalidCredentials,
    #[error("account has been deactivated")]
    UserDeactivated,
    #[error("too many requests, try again later")]
    RateLimited,
    #[error("homeserver could not be found")]
    ServerNotFound,
    #[error("the server does not allow signing in with a password")]
    PasswordLoginUnsupported,
    #[error("network error: {0}")]
    Network(String),
    #[error("not logged in")]
    NotLoggedIn,
    #[error("stored session is invalid: {0}")]
    InvalidSession(String),
    #[error("sync error: {0}")]
    Sync(String),
    #[error("timeline error: {0}")]
    Timeline(String),
    #[error("media error: {0}")]
    Media(String),
    #[error("encryption error: {0}")]
    Encryption(String),
    #[error("invalid recovery key or passphrase")]
    InvalidRecoveryKey,
    #[error("recovery passphrase is too weak")]
    WeakPassphrase,
    #[error("that username is already taken")]
    UsernameTaken,
    #[error("invalid invitation code")]
    InvalidInviteCode,
    #[error("registration is not available on this server")]
    RegistrationUnavailable,
    #[error("new password is too weak")]
    WeakPassword,
    #[error("PIN must have six digits")]
    InvalidPin,
    #[error("invalid name: {0}")]
    InvalidName(String),
    #[error("file is larger than the server limit of {0} bytes")]
    FileTooLarge(u64),
    #[error("update error: {0}")]
    Update(String),
    #[error("live location sharing has ended")]
    LiveLocationEnded,
    #[error("storage error: {0}")]
    Storage(#[from] std::io::Error),
    #[error("failed to build client: {0}")]
    ClientBuild(ClientBuildError),
    #[error(transparent)]
    Matrix(matrix_sdk::Error),
}

pub type Result<T> = std::result::Result<T, CoreError>;

fn is_network(err: &HttpError) -> bool {
    match err {
        HttpError::Reqwest(_) => true,
        HttpError::Cached(inner) => is_network(inner),
        _ => false,
    }
}

impl From<ClientBuildError> for CoreError {
    fn from(err: ClientBuildError) -> Self {
        match err {
            ClientBuildError::Http(ref http) if is_network(http) => Self::Network(err.to_string()),
            ClientBuildError::AutoDiscovery(_) | ClientBuildError::InvalidServerName => {
                Self::ServerNotFound
            }
            other => Self::ClientBuild(other),
        }
    }
}

impl From<matrix_sdk::Error> for CoreError {
    fn from(err: matrix_sdk::Error) -> Self {
        let rejected_auth = err
            .as_uiaa_response()
            .and_then(|info| info.auth_error.as_ref())
            .is_some_and(|auth| auth.kind == ErrorKind::Forbidden);
        if rejected_auth {
            return Self::InvalidCredentials;
        }

        match err.client_api_error_kind() {
            Some(ErrorKind::Forbidden) => return Self::InvalidCredentials,
            Some(ErrorKind::UserDeactivated) => return Self::UserDeactivated,
            Some(ErrorKind::LimitExceeded(_)) => return Self::RateLimited,
            Some(ErrorKind::WeakPassword) => return Self::WeakPassword,
            _ => {}
        }

        match err {
            matrix_sdk::Error::Http(ref http) if is_network(http) => Self::Network(err.to_string()),
            matrix_sdk::Error::AuthenticationRequired => Self::NotLoggedIn,
            other => Self::Matrix(other),
        }
    }
}

impl From<HttpError> for CoreError {
    fn from(err: HttpError) -> Self {
        matrix_sdk::Error::from(err).into()
    }
}
