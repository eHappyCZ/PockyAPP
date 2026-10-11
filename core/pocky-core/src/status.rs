use matrix_sdk::{
    event_handler::EventHandlerDropGuard,
    ruma::{
        events::{
            AnyGlobalAccountDataEvent, AnyGlobalAccountDataEventContent, GlobalAccountDataEventType,
        },
        serde::Raw,
        MilliSecondsSinceUnixEpoch,
    },
    Client,
};
use serde::{Deserialize, Serialize};
use tokio::sync::mpsc;

use crate::{
    error::{CoreError, Result},
    preferences::load_preferences,
};

const LEGACY_KEY: &[u8] = b"pocky.status";
const EVENT_TYPE: &str = "com.pokecio.status";
pub const MAX_STATUS_CHARS: usize = 60;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct OwnStatus {
    pub text: String,
    pub until_ms: Option<u64>,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct StatusContent {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    text: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    until_ms: Option<u64>,
}

impl StatusContent {
    fn into_status(self) -> Option<OwnStatus> {
        let text = self.text.filter(|text| !text.trim().is_empty())?;
        Some(OwnStatus {
            text,
            until_ms: self.until_ms,
        })
    }
}

impl From<Option<&OwnStatus>> for StatusContent {
    fn from(status: Option<&OwnStatus>) -> Self {
        match status {
            Some(status) => StatusContent {
                text: Some(status.text.clone()),
                until_ms: status.until_ms,
            },
            None => StatusContent::default(),
        }
    }
}

pub async fn own_status(client: &Client) -> Result<Option<OwnStatus>> {
    let status = match stored_status(client).await? {
        Some(status) => status,
        None => legacy_status(client).await?,
    };
    Ok(status.filter(|status| is_active(status, now_ms())))
}

pub async fn set_status(client: &Client, text: &str, until_ms: Option<u64>) -> Result<OwnStatus> {
    let text = clean(text)?;
    let status = OwnStatus { text, until_ms };
    upload(client, Some(&status)).await?;
    remove_legacy(client).await?;
    publish(client, Some(&status.text)).await?;
    Ok(status)
}

pub async fn clear_status(client: &Client) -> Result<()> {
    upload(client, None).await?;
    remove_legacy(client).await?;
    publish(client, None).await
}

pub async fn expire_status(client: &Client, now_ms: u64) -> Result<bool> {
    let local = match stored_status(client).await? {
        Some(status) => status,
        None => legacy_status(client).await?,
    };
    match local {
        Some(status) if !is_active(&status, now_ms) => {}
        _ => return Ok(false),
    }
    if let Some(remote) = fetch_remote(client).await? {
        if is_active(&remote, now_ms) {
            return Ok(false);
        }
    }
    clear_status(client).await?;
    Ok(true)
}

pub async fn watch_own_status(
    client: &Client,
    mut on_update: impl FnMut(Option<OwnStatus>),
) -> Result<()> {
    migrate_legacy(client).await?;

    let (sender, mut receiver) = mpsc::unbounded_channel::<Option<OwnStatus>>();
    let handle = client.add_event_handler(move |raw: Raw<AnyGlobalAccountDataEvent>| {
        let sender = sender.clone();
        async move {
            if raw.get_field::<String>("type").ok().flatten().as_deref() != Some(EVENT_TYPE) {
                return;
            }
            let content = raw
                .get_field::<StatusContent>("content")
                .ok()
                .flatten()
                .unwrap_or_default();
            let _ = sender.send(content.into_status());
        }
    });
    let _guard: EventHandlerDropGuard = client.event_handler_drop_guard(handle);

    on_update(own_status(client).await?);
    while let Some(status) = receiver.recv().await {
        on_update(status.filter(|status| is_active(status, now_ms())));
    }

    Ok(())
}

pub(crate) async fn current_text(client: &Client) -> Option<String> {
    own_status(client)
        .await
        .ok()
        .flatten()
        .map(|status| status.text)
}

fn is_active(status: &OwnStatus, now_ms: u64) -> bool {
    status.until_ms.is_none_or(|until| until > now_ms)
}

fn now_ms() -> u64 {
    u64::from(MilliSecondsSinceUnixEpoch::now().get())
}

fn event_type() -> GlobalAccountDataEventType {
    EVENT_TYPE.into()
}

fn parse(raw: Raw<AnyGlobalAccountDataEventContent>) -> Option<OwnStatus> {
    raw.deserialize_as_unchecked::<StatusContent>()
        .ok()?
        .into_status()
}

async fn stored_status(client: &Client) -> Result<Option<Option<OwnStatus>>> {
    let raw = client.account().account_data_raw(event_type()).await?;
    Ok(raw.map(parse))
}

async fn fetch_remote(client: &Client) -> Result<Option<OwnStatus>> {
    let raw = client.account().fetch_account_data(event_type()).await?;
    Ok(raw.and_then(parse))
}

async fn upload(client: &Client, status: Option<&OwnStatus>) -> Result<()> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }
    let content = Raw::new(&StatusContent::from(status))
        .map_err(matrix_sdk::Error::from)?
        .cast_unchecked();
    client
        .account()
        .set_account_data_raw(event_type(), content)
        .await?;
    Ok(())
}

pub(crate) async fn migrate_legacy(client: &Client) -> Result<()> {
    let Some(legacy) = legacy_status(client).await? else {
        return Ok(());
    };
    let remote = client.account().fetch_account_data(event_type()).await?;
    if remote.is_none() && is_active(&legacy, now_ms()) {
        upload(client, Some(&legacy)).await?;
    }
    remove_legacy(client).await
}

async fn publish(client: &Client, text: Option<&str>) -> Result<()> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }
    let share = load_preferences(client).await?.share_presence;
    let message = Some(text.unwrap_or_default().to_owned());
    client
        .set_presence(crate::presence::state(share), message, true)
        .await?;
    Ok(())
}

async fn legacy_status(client: &Client) -> Result<Option<OwnStatus>> {
    let stored = client
        .state_store()
        .get_custom_value(LEGACY_KEY)
        .await
        .map_err(matrix_sdk::Error::from)?;
    Ok(stored.and_then(|bytes| serde_json::from_slice(&bytes).ok()))
}

async fn remove_legacy(client: &Client) -> Result<()> {
    client
        .state_store()
        .remove_custom_value(LEGACY_KEY)
        .await
        .map_err(matrix_sdk::Error::from)?;
    Ok(())
}

fn clean(text: &str) -> Result<String> {
    let text = text.split_whitespace().collect::<Vec<_>>().join(" ");
    if text.is_empty() {
        return Err(CoreError::InvalidName("empty status".to_owned()));
    }
    if text.chars().count() > MAX_STATUS_CHARS {
        return Err(CoreError::InvalidName("status too long".to_owned()));
    }
    Ok(text)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::client::{build_client, Server};

    #[test]
    fn status_text_is_cleaned_and_limited() {
        assert_eq!(clean("  Jdu   na pivo 🍺 ").unwrap(), "Jdu na pivo 🍺");
        assert!(matches!(clean("   "), Err(CoreError::InvalidName(_))));
        assert!(clean(&"ž".repeat(60)).is_ok());
        assert!(matches!(
            clean(&"ž".repeat(61)),
            Err(CoreError::InvalidName(_))
        ));
    }

    #[test]
    fn content_round_trips_and_empty_means_cleared() {
        let status = OwnStatus {
            text: "V práci".to_owned(),
            until_ms: Some(1_000),
        };
        let json = serde_json::to_string(&StatusContent::from(Some(&status))).unwrap();
        assert_eq!(json, r#"{"text":"V práci","until_ms":1000}"#);
        let raw = Raw::from_json_string(json).unwrap();
        assert_eq!(parse(raw), Some(status));

        let cleared = serde_json::to_string(&StatusContent::from(None)).unwrap();
        assert_eq!(cleared, "{}");
        assert_eq!(parse(Raw::from_json_string(cleared).unwrap()), None);
        assert_eq!(
            parse(Raw::from_json_string(r#"{"text":"  "}"#.to_owned()).unwrap()),
            None
        );
    }

    #[test]
    fn status_expires_at_its_deadline() {
        let status = OwnStatus {
            text: "Na obědě".to_owned(),
            until_ms: Some(1_000),
        };
        assert!(is_active(&status, 999));
        assert!(!is_active(&status, 1_000));
        assert!(is_active(
            &OwnStatus {
                until_ms: None,
                ..status
            },
            u64::MAX
        ));
    }

    #[tokio::test]
    async fn legacy_status_is_read_until_migrated() {
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Url("https://matrix.example.org"), dir.path(), None)
            .await
            .unwrap();
        let status = OwnStatus {
            text: "V práci".to_owned(),
            until_ms: None,
        };
        client
            .state_store()
            .set_custom_value_no_read(LEGACY_KEY, serde_json::to_vec(&status).unwrap())
            .await
            .unwrap();

        assert_eq!(own_status(&client).await.unwrap(), Some(status));
        assert!(!expire_status(&client, u64::MAX).await.unwrap());
        remove_legacy(&client).await.unwrap();
        assert_eq!(own_status(&client).await.unwrap(), None);
    }
}
