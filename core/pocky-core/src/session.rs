use std::fmt;

use matrix_sdk::{
    authentication::matrix::MatrixSession,
    ruma::{OwnedDeviceId, OwnedUserId},
    Client, SessionMeta, SessionTokens,
};

use serde::{Deserialize, Serialize};

use crate::error::{CoreError, Result};

#[derive(Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Session {
    pub homeserver_url: String,
    pub user_id: String,
    pub device_id: String,
    pub access_token: String,
    pub refresh_token: Option<String>,
}

impl fmt::Debug for Session {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Session")
            .field("homeserver_url", &self.homeserver_url)
            .field("user_id", &self.user_id)
            .field("device_id", &self.device_id)
            .finish_non_exhaustive()
    }
}

impl Session {
    pub fn to_json(&self) -> String {
        serde_json::to_string(self).expect("session is always serializable")
    }

    pub fn from_json(json: &str) -> Result<Self> {
        serde_json::from_str(json).map_err(|e| CoreError::InvalidSession(e.to_string()))
    }

    pub(crate) fn from_client(client: &Client) -> Option<Self> {
        let session = client.matrix_auth().session()?;

        Some(Self {
            homeserver_url: client.homeserver().to_string(),
            user_id: session.meta.user_id.to_string(),
            device_id: session.meta.device_id.to_string(),
            access_token: session.tokens.access_token,
            refresh_token: session.tokens.refresh_token,
        })
    }

    pub(crate) fn to_matrix(&self) -> Result<MatrixSession> {
        let user_id = OwnedUserId::try_from(self.user_id.as_str())
            .map_err(|e| CoreError::InvalidSession(e.to_string()))?;

        if self.device_id.is_empty() {
            return Err(CoreError::InvalidSession("empty device id".to_owned()));
        }
        if self.access_token.is_empty() {
            return Err(CoreError::InvalidSession("empty access token".to_owned()));
        }

        Ok(MatrixSession {
            meta: SessionMeta {
                user_id,
                device_id: OwnedDeviceId::from(self.device_id.as_str()),
            },
            tokens: SessionTokens {
                access_token: self.access_token.clone(),
                refresh_token: self.refresh_token.clone(),
            },
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Session {
        Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "secret-access".to_owned(),
            refresh_token: Some("secret-refresh".to_owned()),
        }
    }

    #[test]
    fn debug_hides_tokens() {
        let out = format!("{:?}", sample());

        assert!(out.contains("@alice:example.org"));
        assert!(!out.contains("secret-access"));
        assert!(!out.contains("secret-refresh"));
    }

    #[test]
    fn converts_to_matrix_session() {
        let matrix = sample().to_matrix().unwrap();

        assert_eq!(matrix.meta.user_id, "@alice:example.org");
        assert_eq!(matrix.meta.device_id, "ABCDEFGH");
        assert_eq!(matrix.tokens.access_token, "secret-access");
    }

    #[test]
    fn json_roundtrip() {
        let session = sample();
        let restored = Session::from_json(&session.to_json()).unwrap();

        assert_eq!(restored, session);
    }

    #[test]
    fn rejects_broken_json() {
        assert!(matches!(
            Session::from_json("{\"user_id\": 1}"),
            Err(CoreError::InvalidSession(_))
        ));
    }

    #[test]
    fn rejects_invalid_user_id() {
        let session = Session {
            user_id: "alice".to_owned(),
            ..sample()
        };

        assert!(matches!(
            session.to_matrix(),
            Err(CoreError::InvalidSession(_))
        ));
    }
}
