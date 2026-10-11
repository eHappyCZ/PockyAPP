use matrix_sdk::Client;
use serde::{Deserialize, Serialize};

use crate::error::Result;

const KEY: &[u8] = b"pocky.preferences";

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ChatStyle {
    #[default]
    Classic,
    Aura,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum NotificationPreview {
    Always,
    #[default]
    WhenUnlocked,
    Never,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum LocationPrecision {
    Exact,
    Neighborhood,
    #[default]
    City,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct Preferences {
    pub onboarding_done: bool,
    pub chat_style: ChatStyle,
    pub notification_preview: NotificationPreview,
    pub location_precision: LocationPrecision,
    pub share_presence: bool,
    pub share_typing: bool,
    pub send_read_receipts: bool,
    pub block_screenshots: bool,
}

impl Default for Preferences {
    fn default() -> Self {
        Self {
            onboarding_done: false,
            chat_style: ChatStyle::default(),
            notification_preview: NotificationPreview::default(),
            location_precision: LocationPrecision::default(),
            share_presence: false,
            share_typing: true,
            send_read_receipts: true,
            block_screenshots: true,
        }
    }
}

pub async fn load_preferences(client: &Client) -> Result<Preferences> {
    let stored = client
        .state_store()
        .get_custom_value(KEY)
        .await
        .map_err(matrix_sdk::Error::from)?;

    Ok(stored
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default())
}

pub async fn save_preferences(client: &Client, preferences: &Preferences) -> Result<()> {
    let previous = load_preferences(client).await?;
    let bytes = serde_json::to_vec(preferences).expect("preferences are always serializable");
    client
        .state_store()
        .set_custom_value_no_read(KEY, bytes)
        .await
        .map_err(matrix_sdk::Error::from)?;

    let changed = previous.share_presence != preferences.share_presence;
    crate::presence::share_presence(client, preferences.share_presence, changed).await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::client::{build_client, Server};

    async fn client(dir: &tempfile::TempDir) -> Client {
        build_client(
            Server::Url("https://matrix.example.org"),
            dir.path(),
            Some("secret"),
        )
        .await
        .unwrap()
    }

    #[tokio::test]
    async fn defaults_when_nothing_stored() {
        let dir = tempfile::tempdir().unwrap();
        let prefs = load_preferences(&client(&dir).await).await.unwrap();

        assert!(!prefs.onboarding_done);
        assert_eq!(
            prefs.notification_preview,
            NotificationPreview::WhenUnlocked
        );
        assert_eq!(prefs.location_precision, LocationPrecision::City);
        assert!(!prefs.share_presence);
        assert!(prefs.share_typing);
        assert!(prefs.send_read_receipts);
        assert!(prefs.block_screenshots);
    }

    #[tokio::test]
    async fn survives_client_restart() {
        let dir = tempfile::tempdir().unwrap();
        let prefs = Preferences {
            onboarding_done: true,
            chat_style: ChatStyle::Aura,
            notification_preview: NotificationPreview::Never,
            location_precision: LocationPrecision::Exact,
            share_presence: true,
            share_typing: false,
            send_read_receipts: false,
            block_screenshots: false,
        };

        save_preferences(&client(&dir).await, &prefs).await.unwrap();
        let loaded = load_preferences(&client(&dir).await).await.unwrap();

        assert_eq!(loaded, prefs);
    }

    #[test]
    fn missing_fields_fall_back_to_defaults() {
        let prefs: Preferences = serde_json::from_str(r#"{"chat_style":"aura"}"#).unwrap();

        assert_eq!(prefs.chat_style, ChatStyle::Aura);
        assert!(!prefs.onboarding_done);
        assert!(prefs.share_typing);
        assert!(prefs.send_read_receipts);
        assert!(prefs.block_screenshots);
    }
}
