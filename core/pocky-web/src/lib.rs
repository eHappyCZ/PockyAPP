#![cfg(target_family = "wasm")]
#![recursion_limit = "256"]

mod devices;
mod encryption;
mod location;
mod people;
mod presence;
mod rooms;
mod timeline;
mod verification;

use std::{cell::RefCell, rc::Rc};

use js_sys::Function;
use pocky_core::{Client, CoreError, Server, Session, SyncService};
use wasm_bindgen::prelude::*;

pub use devices::{Device, LogoutWarning};
pub use encryption::Encryption;
pub use location::{LiveLocation, Point};
pub use people::{Invite, Member, PersonInfo, RoomInfo};
pub use presence::{PresenceInfo, Privacy, Status, Typist};
pub use rooms::{LastMessage, MessageContent, Room, SyncStatus};
pub use timeline::RoomTimeline;
pub use verification::{Emoji, Step, Verification};

#[wasm_bindgen(start)]
fn start() {
    console_error_panic_hook::set_once();
}

#[wasm_bindgen(js_name = coreVersion)]
pub fn core_version() -> String {
    pocky_core::version().to_owned()
}

#[wasm_bindgen(js_name = parseGeoUri)]
pub fn parse_geo_uri(uri: String) -> JsValue {
    pocky_core::parse_geo_uri(&uri)
        .map(Point::from)
        .and_then(|point| serde_wasm_bindgen::to_value(&point).ok())
        .unwrap_or(JsValue::UNDEFINED)
}

#[wasm_bindgen(js_name = createClient)]
pub async fn create_client(
    store: String,
    passphrase: String,
    server: Option<String>,
) -> Result<PockyClient, JsValue> {
    let server = pocky_core::normalize_server(server.as_deref().unwrap_or_default());
    let client = pocky_core::build_client(Server::Discover(&server), &store, Some(&passphrase))
        .await
        .map_err(error)?;

    Ok(PockyClient::new(client))
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct ServerInfo {
    server: String,
    homeserver_url: String,
}

#[wasm_bindgen(js_name = defaultServer)]
pub fn default_server() -> String {
    pocky_core::SERVER_NAME.to_owned()
}

#[wasm_bindgen(js_name = checkServer)]
pub async fn check_server(server: String) -> Result<JsValue, JsValue> {
    let check = pocky_core::check_server(&server).await.map_err(error)?;
    let info = ServerInfo {
        server: check.server,
        homeserver_url: check.homeserver_url,
    };
    to_js(&info)
}

#[wasm_bindgen(js_name = restoreClient)]
pub async fn restore_client(
    session: String,
    store: String,
    passphrase: String,
) -> Result<PockyClient, JsValue> {
    let session = Session::from_json(&session).map_err(error)?;
    let client = pocky_core::restore_client(&session, &store, Some(&passphrase))
        .await
        .map_err(error)?;

    Ok(PockyClient::new(client))
}

#[wasm_bindgen]
pub struct PockyClient {
    client: Client,
    sync: RefCell<Option<Rc<SyncService>>>,
}

impl PockyClient {
    fn new(client: Client) -> Self {
        Self {
            client,
            sync: RefCell::new(None),
        }
    }

    async fn sync_service(&self) -> Result<Rc<SyncService>, JsValue> {
        if let Some(service) = self.sync.borrow().as_ref() {
            return Ok(service.clone());
        }

        let service = Rc::new(
            pocky_core::create_sync_service(&self.client)
                .await
                .map_err(error)?,
        );
        *self.sync.borrow_mut() = Some(service.clone());
        Ok(service)
    }
}

#[wasm_bindgen]
impl PockyClient {
    #[wasm_bindgen(js_name = userId)]
    pub fn user_id(&self) -> Option<String> {
        self.client.user_id().map(ToString::to_string)
    }

    #[wasm_bindgen(js_name = exportSession)]
    pub fn export_session(&self) -> Option<String> {
        pocky_core::current_session(&self.client).map(|session| session.to_json())
    }

    pub async fn login(
        &self,
        username: String,
        password: String,
        device_name: String,
    ) -> Result<String, JsValue> {
        let session = pocky_core::login(&self.client, &username, &password, &device_name)
            .await
            .map_err(error)?;
        Ok(session.to_json())
    }

    pub async fn logout(&self) -> Result<(), JsValue> {
        self.stop_sync().await;
        pocky_core::logout(&self.client).await.map_err(error)
    }

    #[wasm_bindgen(js_name = displayName)]
    pub async fn display_name(&self) -> Result<String, JsValue> {
        pocky_core::display_name(&self.client).await.map_err(error)
    }

    #[wasm_bindgen(js_name = startSync)]
    pub async fn start_sync(&self) -> Result<(), JsValue> {
        self.sync_service().await?.start().await;
        Ok(())
    }

    #[wasm_bindgen(js_name = stopSync)]
    pub async fn stop_sync(&self) {
        let service = self.sync.borrow().clone();
        if let Some(service) = service {
            service.stop().await;
        }
    }

    pub async fn recover(&self, key: String) -> Result<JsValue, JsValue> {
        let status = pocky_core::recover(&self.client, &key)
            .await
            .map_err(error)?;
        serde_wasm_bindgen::to_value(&Encryption::from(status)).map_err(Into::into)
    }

    #[wasm_bindgen(js_name = requestVerification)]
    pub async fn request_verification(&self) -> Result<Verification, JsValue> {
        let flow = pocky_core::VerificationFlow::request_self(&self.client)
            .await
            .map_err(error)?;
        Ok(Verification::new(flow))
    }

    #[wasm_bindgen(js_name = watchVerificationRequests)]
    pub async fn watch_verification_requests(&self, on_request: Function) {
        pocky_core::watch_verification_requests(&self.client, |flow| {
            let _ = on_request.call1(&JsValue::NULL, &Verification::new(flow).into());
        })
        .await;
    }

    #[wasm_bindgen(js_name = listDevices)]
    pub async fn list_devices(&self) -> Result<JsValue, JsValue> {
        let devices: Vec<Device> = pocky_core::list_devices(&self.client)
            .await
            .map_err(error)?
            .into_iter()
            .map(Into::into)
            .collect();
        serde_wasm_bindgen::to_value(&devices).map_err(Into::into)
    }

    #[wasm_bindgen(js_name = signOutDevice)]
    pub async fn sign_out_device(
        &self,
        device_id: String,
        password: String,
    ) -> Result<(), JsValue> {
        pocky_core::sign_out_device(&self.client, &device_id, &password)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = logoutWarning)]
    pub async fn logout_warning(&self) -> Result<JsValue, JsValue> {
        let risk = pocky_core::logout_risk(&self.client).await.map_err(error)?;
        serde_wasm_bindgen::to_value(&LogoutWarning::from(risk)).map_err(Into::into)
    }

    #[wasm_bindgen(js_name = fetchPresence)]
    pub async fn fetch_presence(&self, user_id: String) -> Result<JsValue, JsValue> {
        let info = pocky_core::fetch_presence(&self.client, &user_id)
            .await
            .map_err(error)?;
        to_js(&PresenceInfo::from(info))
    }

    #[wasm_bindgen(js_name = ownStatus)]
    pub async fn own_status(&self) -> Result<JsValue, JsValue> {
        let status = pocky_core::own_status(&self.client).await.map_err(error)?;
        to_js(&status.map(Status::from))
    }

    #[wasm_bindgen(js_name = setStatus)]
    pub async fn set_status(
        &self,
        text: String,
        until_ms: Option<f64>,
    ) -> Result<JsValue, JsValue> {
        let status = pocky_core::set_status(&self.client, &text, until_ms.map(|ms| ms as u64))
            .await
            .map_err(error)?;
        to_js(&Status::from(status))
    }

    #[wasm_bindgen(js_name = clearStatus)]
    pub async fn clear_status(&self) -> Result<(), JsValue> {
        pocky_core::clear_status(&self.client).await.map_err(error)
    }

    #[wasm_bindgen(js_name = watchTyping)]
    pub async fn watch_typing(&self, on_update: Function) -> Result<(), JsValue> {
        pocky_core::watch_typing(&self.client, |room_id, users| {
            let users: Vec<Typist> = users.into_iter().map(Into::into).collect();
            if let Ok(users) = serde_wasm_bindgen::to_value(&users) {
                let _ = on_update.call2(&JsValue::NULL, &JsValue::from(room_id), &users);
            }
        })
        .await
        .map_err(error)
    }

    #[wasm_bindgen(js_name = sendTyping)]
    pub async fn send_typing(&self, room_id: String, typing: bool) -> Result<(), JsValue> {
        pocky_core::send_typing(&self.client, &room_id, typing)
            .await
            .map_err(error)
    }

    pub async fn privacy(&self) -> Result<JsValue, JsValue> {
        let preferences = pocky_core::load_preferences(&self.client)
            .await
            .map_err(error)?;
        to_js(&Privacy {
            share_presence: preferences.share_presence,
            share_typing: preferences.share_typing,
            location_precision: location::precision_name(preferences.location_precision),
        })
    }

    #[wasm_bindgen(js_name = setPrivacy)]
    pub async fn set_privacy(
        &self,
        share_presence: bool,
        share_typing: bool,
    ) -> Result<(), JsValue> {
        let mut preferences = pocky_core::load_preferences(&self.client)
            .await
            .map_err(error)?;
        preferences.share_presence = share_presence;
        preferences.share_typing = share_typing;
        pocky_core::save_preferences(&self.client, &preferences)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = watchLiveLocations)]
    pub async fn watch_live_locations(&self, on_update: Function) {
        pocky_core::watch_live_locations(&self.client, |shares| {
            let shares: Vec<LiveLocation> = shares.into_iter().map(Into::into).collect();
            notify(&on_update, &shares);
        })
        .await;
    }

    #[wasm_bindgen(js_name = stopLiveLocation)]
    pub async fn stop_live_location(&self, room_id: String) -> Result<(), JsValue> {
        pocky_core::stop_live_location(&self.client, &room_id)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = watchSession)]
    pub async fn watch_session(&self, on_event: Function) {
        pocky_core::watch_session(&self.client, |event| {
            let name = match event {
                pocky_core::SessionEvent::Expired => "expired",
                pocky_core::SessionEvent::Refreshed => "refreshed",
            };
            let _ = on_event.call1(&JsValue::NULL, &JsValue::from(name));
        })
        .await;
    }

    #[wasm_bindgen(js_name = inviteInfo)]
    pub async fn invite_info(&self, room_id: String) -> Result<JsValue, JsValue> {
        let info = pocky_core::invite_info(&self.client, &room_id)
            .await
            .map_err(error)?;
        to_js(&Invite::from(info))
    }

    #[wasm_bindgen(js_name = acceptInvite)]
    pub async fn accept_invite(&self, room_id: String) -> Result<(), JsValue> {
        pocky_core::accept_invite(&self.client, &room_id)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = declineInvite)]
    pub async fn decline_invite(&self, room_id: String) -> Result<(), JsValue> {
        pocky_core::decline_invite(&self.client, &room_id)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = searchPeople)]
    pub async fn search_people(&self, term: String) -> Result<JsValue, JsValue> {
        let people: Vec<PersonInfo> = pocky_core::search_people(&self.client, &term)
            .await
            .map_err(error)?
            .into_iter()
            .map(Into::into)
            .collect();
        to_js(&people)
    }

    #[wasm_bindgen(js_name = openDirectChat)]
    pub async fn open_direct_chat(&self, user_id: String) -> Result<String, JsValue> {
        pocky_core::open_direct_chat(&self.client, &user_id)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = roomInfo)]
    pub async fn room_info(&self, room_id: String) -> Result<JsValue, JsValue> {
        let details = pocky_core::room_details(&self.client, &room_id)
            .await
            .map_err(error)?;
        to_js(&RoomInfo::from(details))
    }

    #[wasm_bindgen(js_name = setFavourite)]
    pub async fn set_favourite(&self, room_id: String, favourite: bool) -> Result<(), JsValue> {
        pocky_core::set_favourite(&self.client, &room_id, favourite)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = setMuted)]
    pub async fn set_muted(&self, room_id: String, muted: bool) -> Result<(), JsValue> {
        pocky_core::set_muted(&self.client, &room_id, muted)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = leaveRoom)]
    pub async fn leave_room(&self, room_id: String) -> Result<(), JsValue> {
        pocky_core::leave_room(&self.client, &room_id)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = watchEncryption)]
    pub async fn watch_encryption(&self, on_update: Function) {
        pocky_core::watch_encryption(&self.client, |status| {
            notify(&on_update, &Encryption::from(status));
        })
        .await;
    }

    #[wasm_bindgen(js_name = openTimeline)]
    pub async fn open_timeline(&self, room_id: String) -> Result<RoomTimeline, JsValue> {
        let service = self.sync_service().await?;
        let timeline = pocky_core::open_timeline(&self.client, Some(&service), &room_id)
            .await
            .map_err(error)?;
        Ok(RoomTimeline::new(timeline))
    }

    #[wasm_bindgen(js_name = openHistory)]
    pub async fn open_history(&self, room_id: String) -> Result<RoomTimeline, JsValue> {
        let timeline = pocky_core::open_history(&self.client, &room_id)
            .await
            .map_err(error)?;
        Ok(RoomTimeline::new(timeline))
    }

    #[wasm_bindgen(js_name = loadMedia)]
    pub async fn load_media(
        &self,
        source: String,
        width: u32,
        height: u32,
    ) -> Result<Vec<u8>, JsValue> {
        pocky_core::load_media(&self.client, &source, width, height)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = watchRooms)]
    pub async fn watch_rooms(&self, on_update: Function) -> Result<(), JsValue> {
        let service = self.sync_service().await?;
        pocky_core::watch_rooms(&service, |rooms| {
            let rooms: Vec<Room> = rooms.into_iter().map(Into::into).collect();
            notify(&on_update, &rooms);
        })
        .await
        .map_err(error)
    }

    #[wasm_bindgen(js_name = watchSyncStatus)]
    pub async fn watch_sync_status(&self, on_update: Function) -> Result<(), JsValue> {
        let service = self.sync_service().await?;
        pocky_core::watch_sync_state(&service, |state| {
            notify(&on_update, &SyncStatus::from(state));
        })
        .await;
        Ok(())
    }
}

fn to_js(value: &impl serde::Serialize) -> Result<JsValue, JsValue> {
    serde_wasm_bindgen::to_value(value).map_err(Into::into)
}

pub(crate) fn notify(callback: &Function, value: &impl serde::Serialize) {
    if let Ok(value) = serde_wasm_bindgen::to_value(value) {
        let _ = callback.call1(&JsValue::NULL, &value);
    }
}

pub(crate) fn error(err: CoreError) -> JsValue {
    let code = match err {
        CoreError::InvalidCredentials => "invalid_credentials",
        CoreError::UserDeactivated => "user_deactivated",
        CoreError::RateLimited => "rate_limited",
        CoreError::ServerNotFound => "server_not_found",
        CoreError::PasswordLoginUnsupported => "password_login_unsupported",
        CoreError::Network(_) => "network",
        CoreError::NotLoggedIn | CoreError::InvalidSession(_) => "session_expired",
        CoreError::InvalidRecoveryKey => "invalid_recovery_key",
        CoreError::WeakPassphrase => "weak_passphrase",
        CoreError::WeakPassword => "weak_password",
        CoreError::FileTooLarge(_) => "file_too_large",
        CoreError::InvalidName(_) => "invalid_name",
        _ => "unknown",
    };
    let js = js_sys::Error::new(&err.to_string());
    js.set_name(code);
    js.into()
}
