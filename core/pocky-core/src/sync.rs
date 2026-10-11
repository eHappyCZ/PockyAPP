use matrix_sdk::Client;
use matrix_sdk_ui::sync_service::{self, SyncService};

use crate::error::{CoreError, Result};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SyncState {
    Idle,
    Running,
    Offline,
    Error,
    Terminated,
}

impl From<&sync_service::State> for SyncState {
    fn from(state: &sync_service::State) -> Self {
        match state {
            sync_service::State::Idle => Self::Idle,
            sync_service::State::Running => Self::Running,
            sync_service::State::Offline => Self::Offline,
            sync_service::State::Error(_) => Self::Error,
            sync_service::State::Terminated => Self::Terminated,
        }
    }
}

pub async fn create_sync_service(client: &Client) -> Result<SyncService> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }

    crate::presence::apply_presence_preference(client).await?;
    let now = u64::from(matrix_sdk::ruma::MilliSecondsSinceUnixEpoch::now().get());
    let _ = crate::status::expire_status(client, now).await;
    let migrating = client.clone();
    matrix_sdk::executor::spawn(async move {
        let _ = crate::status::migrate_legacy(&migrating).await;
    });

    client
        .event_cache()
        .subscribe()
        .map_err(|e| CoreError::Sync(e.to_string()))?;

    SyncService::builder(client.clone())
        .with_offline_mode()
        .build()
        .await
        .map_err(|e| CoreError::Sync(e.to_string()))
}

pub async fn watch_sync_state(service: &SyncService, mut on_state: impl FnMut(SyncState)) {
    let mut states = service.state();
    on_state(SyncState::from(&states.get()));

    while let Some(state) = states.next().await {
        on_state(SyncState::from(&state));
    }
}
