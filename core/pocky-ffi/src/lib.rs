#![recursion_limit = "256"]

use std::{
    future::Future,
    path::PathBuf,
    sync::{Arc, LazyLock, Mutex},
};

use pocky_core::{Client, CoreError, Server, Session, SyncService};
use tokio::runtime::Runtime;

use crate::{
    devices::AccountDevice,
    encryption::{EncryptionInfo, EncryptionListener, LogoutWarning},
    location::{GeoLocation, LiveLength, LiveLocationListener, OwnLiveLocation},
    people::{Contact, Invitation},
    preferences::{DevicePreferences, LocationArea},
    presence::{OwnStatusListener, PresenceInfo, StatusMessage, TypingListener},
    push::NotificationInfo,
    room_info::RoomInfo,
    rooms::{RoomListListener, SyncStatusListener},
    task::TaskHandle,
    timeline::RoomTimeline,
    trust::{IdentityChangeListener, UserTrust},
    verification::{Verification, VerificationRequestListener},
};

#[cfg(target_os = "android")]
mod android;
mod app_lock;
mod changelog;
mod devices;
mod encryption;
mod location;
mod people;
mod preferences;
mod presence;
mod push;
mod room_info;
mod rooms;
mod task;
mod timeline;
mod trust;
mod update;
mod verification;

uniffi::setup_scaffolding!();

pub(crate) static RUNTIME: LazyLock<Runtime> = LazyLock::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .thread_name("pocky-core")
        .enable_all()
        .build()
        .expect("failed to start tokio runtime")
});

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum PockyError {
    #[error("invalid username or password")]
    InvalidCredentials,
    #[error("account has been deactivated")]
    UserDeactivated,
    #[error("too many requests, try again later")]
    RateLimited,
    #[error("homeserver could not be found")]
    ServerNotFound,
    #[error("network error: {0}")]
    Network(String),
    #[error("not logged in")]
    NotLoggedIn,
    #[error("stored session is invalid: {0}")]
    InvalidSession(String),
    #[error("invalid recovery key or passphrase")]
    InvalidRecoveryKey,
    #[error("recovery passphrase is too weak")]
    WeakPassphrase,
    #[error("new password is too weak")]
    WeakPassword,
    #[error("PIN must have six digits")]
    InvalidPin,
    #[error("invalid name")]
    InvalidName,
    #[error("file is too large, limit {limit} bytes")]
    FileTooLarge { limit: u64 },
    #[error("live location sharing has ended")]
    LiveLocationEnded,
    #[error("{0}")]
    Other(String),
}

impl From<CoreError> for PockyError {
    fn from(err: CoreError) -> Self {
        match err {
            CoreError::InvalidCredentials => Self::InvalidCredentials,
            CoreError::UserDeactivated => Self::UserDeactivated,
            CoreError::RateLimited => Self::RateLimited,
            CoreError::ServerNotFound => Self::ServerNotFound,
            CoreError::Network(msg) => Self::Network(msg),
            CoreError::NotLoggedIn => Self::NotLoggedIn,
            CoreError::InvalidSession(msg) => Self::InvalidSession(msg),
            CoreError::InvalidRecoveryKey => Self::InvalidRecoveryKey,
            CoreError::WeakPassphrase => Self::WeakPassphrase,
            CoreError::WeakPassword => Self::WeakPassword,
            CoreError::InvalidPin => Self::InvalidPin,
            CoreError::InvalidName(_) => Self::InvalidName,
            CoreError::FileTooLarge(limit) => Self::FileTooLarge { limit },
            CoreError::LiveLocationEnded => Self::LiveLocationEnded,
            other => Self::Other(other.to_string()),
        }
    }
}

impl From<tokio::task::JoinError> for PockyError {
    fn from(err: tokio::task::JoinError) -> Self {
        Self::Other(err.to_string())
    }
}

pub(crate) async fn run<T, F>(future: F) -> Result<T, PockyError>
where
    T: Send + 'static,
    F: Future<Output = pocky_core::Result<T>> + Send + 'static,
{
    Ok(RUNTIME.spawn(future).await??)
}

#[derive(uniffi::Object)]
pub struct PockyClient {
    inner: Mutex<Option<Client>>,
    sync: Arc<tokio::sync::Mutex<Option<Arc<SyncService>>>>,
    open_room: Arc<Mutex<Option<String>>>,
    data_dir: PathBuf,
}

impl PockyClient {
    fn new(client: Client, data_dir: PathBuf) -> Arc<Self> {
        Arc::new(Self {
            inner: Mutex::new(Some(client)),
            sync: Arc::new(tokio::sync::Mutex::new(None)),
            open_room: Arc::new(Mutex::new(None)),
            data_dir,
        })
    }

    async fn sync_service(&self) -> Result<Arc<SyncService>, PockyError> {
        let mut sync = self.sync.lock().await;
        if let Some(service) = sync.as_ref() {
            return Ok(service.clone());
        }

        let client = self.client()?;
        let service = run(async move { pocky_core::create_sync_service(&client).await }).await?;
        let service = Arc::new(service);
        *sync = Some(service.clone());
        Ok(service)
    }

    async fn shutdown_sync(&self) {
        let Some(service) = self.sync.lock().await.take() else {
            return;
        };

        let _ = RUNTIME
            .spawn(async move {
                service.stop().await;
            })
            .await;
    }

    fn client(&self) -> Result<Client, PockyError> {
        self.inner
            .lock()
            .unwrap()
            .clone()
            .ok_or(PockyError::NotLoggedIn)
    }
}

impl Drop for PockyClient {
    fn drop(&mut self) {
        let _guard = RUNTIME.enter();
        if let Ok(mut sync) = self.sync.try_lock() {
            drop(sync.take());
        }
        drop(self.inner.get_mut().unwrap().take());
    }
}

#[uniffi::export]
impl PockyClient {
    pub fn homeserver(&self) -> Result<String, PockyError> {
        Ok(self.client()?.homeserver().to_string())
    }

    pub fn user_id(&self) -> Option<String> {
        let client = self.client().ok()?;
        client.user_id().map(ToString::to_string)
    }

    pub fn is_logged_in(&self) -> bool {
        self.client()
            .map(|client| client.session_meta().is_some())
            .unwrap_or(false)
    }

    pub fn export_session(&self) -> Option<String> {
        let client = self.client().ok()?;
        pocky_core::current_session(&client).map(|session| session.to_json())
    }

    pub async fn login(
        &self,
        username: String,
        password: String,
        device_name: String,
    ) -> Result<String, PockyError> {
        let client = self.client()?;
        let session =
            run(
                async move { pocky_core::login(&client, &username, &password, &device_name).await },
            )
            .await?;

        Ok(session.to_json())
    }

    pub async fn display_name(&self) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::display_name(&client).await }).await
    }

    pub async fn avatar_url(&self) -> Result<Option<String>, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::avatar_url(&client).await }).await
    }

    pub async fn max_upload_size(&self) -> Result<u64, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::max_upload_size(&client).await }).await
    }

    pub async fn room_info(&self, room_id: String) -> Result<RoomInfo, PockyError> {
        let client = self.client()?;
        let details = run(async move { pocky_core::room_details(&client, &room_id).await }).await?;
        Ok(details.into())
    }

    pub async fn set_room_name(&self, room_id: String, name: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_room_name(&client, &room_id, &name).await }).await
    }

    pub async fn set_room_topic(&self, room_id: String, topic: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_room_topic(&client, &room_id, &topic).await }).await
    }

    pub async fn set_room_avatar(&self, room_id: String, bytes: Vec<u8>) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_room_avatar(&client, &room_id, &bytes).await }).await
    }

    pub async fn invite_people(
        &self,
        room_id: String,
        user_ids: Vec<String>,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::invite_people(&client, &room_id, &user_ids).await }).await
    }

    pub async fn remove_member(&self, room_id: String, user_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::remove_member(&client, &room_id, &user_id).await }).await
    }

    pub async fn devices(&self) -> Result<Vec<AccountDevice>, PockyError> {
        let client = self.client()?;
        let devices = run(async move { pocky_core::list_devices(&client).await }).await?;
        Ok(devices.into_iter().map(Into::into).collect())
    }

    pub async fn sign_out_device(
        &self,
        device_id: String,
        password: String,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::sign_out_device(&client, &device_id, &password).await }).await
    }

    pub async fn search_people(&self, term: String) -> Result<Vec<Contact>, PockyError> {
        let client = self.client()?;
        let people = run(async move { pocky_core::search_people(&client, &term).await }).await?;
        Ok(people.into_iter().map(Into::into).collect())
    }

    pub async fn open_direct_chat(&self, user_id: String) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::open_direct_chat(&client, &user_id).await }).await
    }

    pub async fn create_group(
        &self,
        name: String,
        members: Vec<String>,
    ) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::create_group(&client, &name, &members).await }).await
    }

    pub async fn invite_info(&self, room_id: String) -> Result<Invitation, PockyError> {
        let client = self.client()?;
        let info = run(async move { pocky_core::invite_info(&client, &room_id).await }).await?;
        Ok(info.into())
    }

    pub async fn accept_invite(&self, room_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::accept_invite(&client, &room_id).await }).await
    }

    pub async fn decline_invite(&self, room_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::decline_invite(&client, &room_id).await }).await
    }

    pub async fn set_display_name(&self, name: String) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_display_name(&client, &name).await }).await
    }

    pub async fn set_avatar(&self, bytes: Vec<u8>) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_avatar(&client, &bytes).await }).await
    }

    pub async fn remove_avatar(&self) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::remove_avatar(&client).await }).await
    }

    pub async fn preferences(&self) -> Result<DevicePreferences, PockyError> {
        let client = self.client()?;
        let prefs = run(async move { pocky_core::load_preferences(&client).await }).await?;
        Ok(prefs.into())
    }

    pub async fn save_preferences(&self, preferences: DevicePreferences) -> Result<(), PockyError> {
        let client = self.client()?;
        let prefs = preferences.into();
        run(async move { pocky_core::save_preferences(&client, &prefs).await }).await
    }

    pub async fn own_status(&self) -> Result<Option<StatusMessage>, PockyError> {
        let client = self.client()?;
        let status = run(async move { pocky_core::own_status(&client).await }).await?;
        Ok(status.map(Into::into))
    }

    pub async fn set_status(
        &self,
        text: String,
        until_ms: Option<u64>,
    ) -> Result<StatusMessage, PockyError> {
        let client = self.client()?;
        let status =
            run(async move { pocky_core::set_status(&client, &text, until_ms).await }).await?;
        Ok(status.into())
    }

    pub async fn clear_status(&self) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::clear_status(&client).await }).await
    }

    pub async fn expire_status(&self, now_ms: u64) -> Result<bool, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::expire_status(&client, now_ms).await }).await
    }

    pub async fn presence(&self, user_id: String) -> Result<PresenceInfo, PockyError> {
        let client = self.client()?;
        let presence =
            run(async move { pocky_core::fetch_presence(&client, &user_id).await }).await?;
        Ok(presence.into())
    }

    pub fn observe_own_status(
        &self,
        listener: Box<dyn OwnStatusListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let client = self.client()?;
        let handle = RUNTIME.spawn(async move {
            let _ = pocky_core::watch_own_status(&client, |status| {
                listener.on_update(status.map(Into::into));
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub fn observe_typing(
        &self,
        listener: Box<dyn TypingListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let client = self.client()?;
        let handle = RUNTIME.spawn(async move {
            let _ = pocky_core::watch_typing(&client, |room_id, users| {
                listener.on_update(room_id, users.into_iter().map(Into::into).collect());
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn send_typing(&self, room_id: String, typing: bool) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::send_typing(&client, &room_id, typing).await }).await
    }

    pub async fn register_pusher(
        &self,
        push_key: String,
        device_name: String,
        lang: String,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::register_pusher(&client, &push_key, &device_name, &lang).await })
            .await
    }

    pub async fn unregister_pusher(&self, push_key: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::unregister_pusher(&client, &push_key).await }).await
    }

    pub async fn notification(
        &self,
        room_id: String,
        event_id: String,
    ) -> Result<Option<NotificationInfo>, PockyError> {
        let client = self.client()?;
        let sync = self.sync_service().await?;
        let found = run(async move {
            pocky_core::fetch_notification(&client, sync, &room_id, &event_id).await
        })
        .await?;
        Ok(found.map(Into::into))
    }

    pub async fn set_favourite(&self, room_id: String, favourite: bool) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_favourite(&client, &room_id, favourite).await }).await
    }

    pub async fn set_muted(&self, room_id: String, muted: bool) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::set_muted(&client, &room_id, muted).await }).await
    }

    pub async fn mark_room_read(&self, room_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::mark_room_read(&client, &room_id).await }).await
    }

    pub async fn leave_room(&self, room_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::leave_room(&client, &room_id).await }).await
    }

    pub async fn start_live_location(
        &self,
        room_id: String,
        length: LiveLength,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::start_live_location(&client, &room_id, length.into()).await })
            .await
    }

    pub async fn send_live_location(
        &self,
        room_id: String,
        location: GeoLocation,
        area: LocationArea,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move {
            pocky_core::send_beacon(&client, &room_id, location.into(), area.into()).await
        })
        .await
    }

    pub async fn own_live_locations(&self) -> Result<Vec<OwnLiveLocation>, PockyError> {
        let client = self.client()?;
        let shares = RUNTIME
            .spawn(async move { pocky_core::own_live_shares(&client).await })
            .await?;
        Ok(shares.into_iter().map(Into::into).collect())
    }

    pub fn observe_live_locations(
        &self,
        listener: Box<dyn LiveLocationListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let client = self.client()?;
        let handle = RUNTIME.spawn(async move {
            pocky_core::watch_live_locations(&client, |people| {
                listener.on_update(people.into_iter().map(Into::into).collect());
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn stop_live_location(&self, room_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::stop_live_location(&client, &room_id).await }).await
    }

    pub async fn start_sync(&self) -> Result<(), PockyError> {
        let service = self.sync_service().await?;
        RUNTIME
            .spawn(async move {
                service.start().await;
            })
            .await?;
        Ok(())
    }

    pub async fn stop_sync(&self) {
        let Some(service) = self.sync.lock().await.clone() else {
            return;
        };

        let _ = RUNTIME
            .spawn(async move {
                service.stop().await;
            })
            .await;
    }

    pub async fn observe_rooms(
        &self,
        listener: Box<dyn RoomListListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let service = self.sync_service().await?;
        let handle = RUNTIME.spawn(async move {
            let _ = pocky_core::watch_rooms(&service, |rooms| {
                listener.on_update(rooms.into_iter().map(Into::into).collect());
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn observe_sync_status(
        &self,
        listener: Box<dyn SyncStatusListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let service = self.sync_service().await?;
        let handle = RUNTIME.spawn(async move {
            pocky_core::watch_sync_state(&service, |state| listener.on_update(state.into())).await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn encryption_status(&self) -> Result<EncryptionInfo, PockyError> {
        let client = self.client()?;
        let status = run(async move { pocky_core::encryption_status(&client).await }).await?;
        Ok(status.into())
    }

    pub async fn recover(&self, key: String) -> Result<EncryptionInfo, PockyError> {
        let client = self.client()?;
        let status = run(async move { pocky_core::recover(&client, &key).await }).await?;
        Ok(status.into())
    }

    pub fn observe_encryption(
        &self,
        listener: Box<dyn EncryptionListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let client = self.client()?;
        let handle = RUNTIME.spawn(async move {
            pocky_core::watch_encryption(&client, |status| listener.on_update(status.into())).await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn logout_warning(&self) -> Result<LogoutWarning, PockyError> {
        let client = self.client()?;
        let risk = run(async move { pocky_core::logout_risk(&client).await }).await?;
        Ok(risk.into())
    }

    pub async fn enable_recovery(
        &self,
        password: String,
        passphrase: String,
    ) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::enable_recovery(&client, &password, &passphrase).await }).await
    }

    pub async fn reset_identity(
        &self,
        password: String,
        passphrase: String,
    ) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::reset_identity(&client, &password, &passphrase).await }).await
    }

    pub async fn change_recovery_passphrase(
        &self,
        passphrase: String,
    ) -> Result<String, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::change_recovery_passphrase(&client, &passphrase).await }).await
    }

    pub async fn change_password(
        &self,
        current: String,
        new_password: String,
        sign_out_others: bool,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move {
            pocky_core::change_password(&client, &current, &new_password, sign_out_others).await
        })
        .await
    }

    pub async fn request_self_verification(&self) -> Result<Arc<Verification>, PockyError> {
        let client = self.client()?;
        let flow =
            run(async move { pocky_core::VerificationFlow::request_self(&client).await }).await?;
        Ok(Verification::new(flow))
    }

    pub async fn request_user_verification(
        &self,
        user_id: String,
    ) -> Result<Arc<Verification>, PockyError> {
        let client = self.client()?;
        let sync = self.sync.lock().await.clone();
        let open_room = self.open_room.lock().unwrap().clone();
        let flow = run(async move {
            let flow = pocky_core::VerificationFlow::request_user(&client, &user_id).await?;
            if let Some(sync) = sync {
                flow.follow_room(&sync, open_room.as_deref()).await;
            }
            Ok(flow)
        })
        .await?;
        Ok(Verification::new(flow))
    }

    pub async fn user_trust(&self, user_id: String) -> Result<UserTrust, PockyError> {
        let client = self.client()?;
        let level = run(async move { pocky_core::user_trust(&client, &user_id).await }).await?;
        Ok(level.into())
    }

    pub async fn user_display_name(&self, user_id: String) -> Option<String> {
        let client = self.client().ok()?;
        RUNTIME
            .spawn(async move { pocky_core::user_display_name(&client, &user_id).await })
            .await
            .ok()
            .flatten()
    }

    pub async fn acknowledge_identity(&self, user_id: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::acknowledge_identity(&client, &user_id).await }).await
    }

    pub fn observe_identity_changes(
        &self,
        room_id: String,
        listener: Box<dyn IdentityChangeListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let client = self.client()?;
        let handle = RUNTIME.spawn(async move {
            let _ = pocky_core::watch_identity_warnings(&client, &room_id, |warnings| {
                listener.on_update(warnings.into_iter().map(Into::into).collect());
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub fn observe_verification_requests(
        &self,
        listener: Box<dyn VerificationRequestListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let client = self.client()?;
        let sync = self.sync.clone();
        let open_room = self.open_room.clone();
        let handle = RUNTIME.spawn(async move {
            pocky_core::watch_verification_requests(&client, |flow| {
                let verification = Verification::new(flow);
                let flow = verification.flow();
                let sync = sync.clone();
                let open_room = open_room.lock().unwrap().clone();
                RUNTIME.spawn(async move {
                    if let Some(sync) = sync.lock().await.clone() {
                        flow.follow_room(&sync, open_room.as_deref()).await;
                    }
                });
                listener.on_request(verification);
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn load_media(
        &self,
        source: String,
        width: u32,
        height: u32,
    ) -> Result<Vec<u8>, PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::load_media(&client, &source, width, height).await }).await
    }

    pub async fn timeline(&self, room_id: String) -> Result<Arc<RoomTimeline>, PockyError> {
        let client = self.client()?;
        let sync = self.sync.lock().await.clone();
        *self.open_room.lock().unwrap() = Some(room_id.clone());
        let timeline =
            run(async move { pocky_core::open_timeline(&client, sync.as_deref(), &room_id).await })
                .await?;

        Ok(RoomTimeline::new(timeline))
    }

    pub async fn room_media(&self, room_id: String) -> Result<Arc<RoomTimeline>, PockyError> {
        let client = self.client()?;
        let timeline = run(async move { pocky_core::open_media(&client, &room_id).await }).await?;

        Ok(RoomTimeline::new(timeline))
    }

    pub async fn forward_message(
        &self,
        from_room: String,
        event_id: String,
        to_room: String,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move {
            pocky_core::forward_message(&client, &from_room, &event_id, &to_room).await
        })
        .await
    }

    pub async fn room_history(&self, room_id: String) -> Result<Arc<RoomTimeline>, PockyError> {
        let client = self.client()?;
        let timeline =
            run(async move { pocky_core::open_history(&client, &room_id).await }).await?;

        Ok(RoomTimeline::new(timeline))
    }

    pub async fn delete_account(&self, password: String) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::delete_account(&client, &password).await }).await?;
        self.shutdown_sync().await;

        if let Some(client) = self.inner.lock().unwrap().take() {
            let _guard = RUNTIME.enter();
            drop(client);
        }

        let data_dir = self.data_dir.clone();
        run(async move { pocky_core::delete_store(&data_dir).await }).await
    }

    pub async fn logout(&self) -> Result<(), PockyError> {
        self.shutdown_sync().await;
        let client = self.client()?;
        let server_logout = run(async move { pocky_core::logout(&client).await }).await;

        if let Some(client) = self.inner.lock().unwrap().take() {
            let _guard = RUNTIME.enter();
            drop(client);
        }

        let data_dir = self.data_dir.clone();
        run(async move { pocky_core::delete_store(&data_dir).await }).await?;

        match server_logout {
            Ok(()) | Err(PockyError::NotLoggedIn) => Ok(()),
            Err(err) => Err(err),
        }
    }
}

#[uniffi::export]
pub fn core_version() -> String {
    pocky_core::version().to_owned()
}

#[uniffi::export]
pub async fn create_client(
    data_dir: String,
    passphrase: Option<String>,
) -> Result<Arc<PockyClient>, PockyError> {
    build(
        Server::Discover(pocky_core::SERVER_NAME),
        data_dir,
        passphrase,
    )
    .await
}

#[uniffi::export]
pub async fn restore_client(
    session: String,
    data_dir: String,
    passphrase: Option<String>,
) -> Result<Arc<PockyClient>, PockyError> {
    let session = Session::from_json(&session)?;
    let path = PathBuf::from(data_dir);

    let store = path.clone();
    let client = run(async move {
        pocky_core::restore_client(&session, &store, passphrase.as_deref()).await
    })
    .await?;

    Ok(PockyClient::new(client, path))
}

async fn build(
    server: Server<'static>,
    data_dir: String,
    passphrase: Option<String>,
) -> Result<Arc<PockyClient>, PockyError> {
    let path = PathBuf::from(data_dir);

    let store = path.clone();
    let client = run(async move {
        pocky_core::delete_store(&store).await?;
        pocky_core::build_client(server, &store, passphrase.as_deref()).await
    })
    .await?;

    Ok(PockyClient::new(client, path))
}

#[cfg(test)]
mod tests {
    use super::*;

    const TEST_SERVER: Server<'static> = Server::Url("https://matrix.example.org");

    fn block_on<F: Future>(future: F) -> F::Output {
        tokio::runtime::Builder::new_current_thread()
            .build()
            .unwrap()
            .block_on(future)
    }

    fn sample_session() -> String {
        Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "token".to_owned(),
            refresh_token: None,
        }
        .to_json()
    }

    fn store_path(dir: &tempfile::TempDir) -> String {
        dir.path().join("store").to_string_lossy().into_owned()
    }

    #[test]
    fn client_can_be_dropped_outside_runtime() {
        let dir = tempfile::tempdir().unwrap();
        let client = block_on(build(TEST_SERVER, store_path(&dir), None)).unwrap();

        assert!(!client.is_logged_in());
        drop(client);
    }

    #[test]
    fn restores_and_exports_session() {
        let dir = tempfile::tempdir().unwrap();
        let session = sample_session();

        let client = block_on(restore_client(session.clone(), store_path(&dir), None)).unwrap();

        assert!(client.is_logged_in());
        assert_eq!(client.user_id().as_deref(), Some("@alice:example.org"));
        assert_eq!(client.export_session(), Some(session));
    }

    #[test]
    fn new_client_starts_with_empty_store() {
        let dir = tempfile::tempdir().unwrap();
        let path = store_path(&dir);
        let old = block_on(build(TEST_SERVER, path.clone(), None)).unwrap();
        drop(old);

        let client = block_on(build(TEST_SERVER, path, Some("secret".to_owned())));

        assert!(client.is_ok());
    }

    struct StatusSink(Mutex<std::sync::mpsc::Sender<rooms::SyncStatus>>);

    impl SyncStatusListener for StatusSink {
        fn on_update(&self, status: rooms::SyncStatus) {
            let _ = self.0.lock().unwrap().send(status);
        }
    }

    struct RoomSink;

    impl RoomListListener for RoomSink {
        fn on_update(&self, _rooms: Vec<rooms::Room>) {}
    }

    #[test]
    fn reports_initial_sync_status() {
        let dir = tempfile::tempdir().unwrap();
        let client = block_on(restore_client(sample_session(), store_path(&dir), None)).unwrap();
        let (tx, rx) = std::sync::mpsc::channel();

        let handle =
            block_on(client.observe_sync_status(Box::new(StatusSink(Mutex::new(tx))))).unwrap();
        let status = rx.recv_timeout(std::time::Duration::from_secs(5)).unwrap();

        assert!(matches!(status, rooms::SyncStatus::Idle));
        handle.cancel();
        assert!(handle.is_finished());
    }

    #[test]
    fn room_list_requires_login() {
        let dir = tempfile::tempdir().unwrap();
        let client = block_on(build(TEST_SERVER, store_path(&dir), None)).unwrap();

        let result = block_on(client.observe_rooms(Box::new(RoomSink)));

        assert!(matches!(result, Err(PockyError::NotLoggedIn)));
    }

    #[test]
    fn logout_stops_sync() {
        let dir = tempfile::tempdir().unwrap();
        let path = store_path(&dir);
        let client = block_on(restore_client(sample_session(), path.clone(), None)).unwrap();
        let _rooms = block_on(client.observe_rooms(Box::new(RoomSink))).unwrap();

        let _ = block_on(client.logout());

        assert!(!std::path::Path::new(&path).exists());
        assert!(!client.is_logged_in());
    }

    #[test]
    fn timeline_for_unknown_room_fails() {
        let dir = tempfile::tempdir().unwrap();
        let client = block_on(restore_client(sample_session(), store_path(&dir), None)).unwrap();

        let result = block_on(client.timeline("!nope:example.org".to_owned()));

        assert!(matches!(result, Err(PockyError::Other(msg)) if msg.contains("unknown room")));
    }

    #[test]
    fn preferences_reset_after_logout() {
        let dir = tempfile::tempdir().unwrap();
        let path = store_path(&dir);
        let client = block_on(restore_client(sample_session(), path.clone(), None)).unwrap();
        let mut prefs = block_on(client.preferences()).unwrap();
        prefs.onboarding_done = true;
        block_on(client.save_preferences(prefs)).unwrap();
        drop(client);

        let restored = block_on(restore_client(sample_session(), path.clone(), None)).unwrap();
        assert!(block_on(restored.preferences()).unwrap().onboarding_done);
        let _ = block_on(restored.logout());

        let fresh = block_on(restore_client(sample_session(), path, None)).unwrap();
        assert!(!block_on(fresh.preferences()).unwrap().onboarding_done);
    }

    #[test]
    fn rejects_broken_session() {
        let dir = tempfile::tempdir().unwrap();
        let result = block_on(restore_client("{}".to_owned(), store_path(&dir), None));

        assert!(matches!(result, Err(PockyError::InvalidSession(_))));
    }

    #[test]
    fn logout_without_session_wipes_store() {
        let dir = tempfile::tempdir().unwrap();
        let path = store_path(&dir);
        let client = block_on(build(TEST_SERVER, path.clone(), None)).unwrap();
        assert!(std::path::Path::new(&path).exists());

        block_on(client.logout()).unwrap();

        assert!(!std::path::Path::new(&path).exists());
        assert!(!client.is_logged_in());
        assert!(matches!(client.homeserver(), Err(PockyError::NotLoggedIn)));
    }
}
