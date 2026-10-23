use futures_util::future::{AbortHandle, Abortable};
use js_sys::Function;
use pocky_core::{Client, IdentityWarning, TrustLevel};
use serde::Serialize;
use wasm_bindgen::prelude::*;

use crate::notify;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Warning {
    pub user_id: String,
    pub name: Option<String>,
    pub was_verified: bool,
}

impl From<IdentityWarning> for Warning {
    fn from(warning: IdentityWarning) -> Self {
        Self {
            user_id: warning.user_id,
            name: warning.name,
            was_verified: warning.was_verified,
        }
    }
}

pub fn trust_name(level: TrustLevel) -> &'static str {
    match level {
        TrustLevel::NoIdentity => "noIdentity",
        TrustLevel::Unverified => "unverified",
        TrustLevel::Verified => "verified",
        TrustLevel::VerificationBroken => "verificationBroken",
    }
}

#[wasm_bindgen]
pub struct IdentityWatch {
    handle: AbortHandle,
}

impl IdentityWatch {
    pub(crate) fn start(client: Client, room_id: String, on_update: Function) -> Self {
        let (handle, registration) = AbortHandle::new_pair();
        let watch = async move {
            let _ = pocky_core::watch_identity_warnings(&client, &room_id, |warnings| {
                let warnings: Vec<Warning> = warnings.into_iter().map(Into::into).collect();
                notify(&on_update, &warnings);
            })
            .await;
        };
        wasm_bindgen_futures::spawn_local(async move {
            let _ = Abortable::new(watch, registration).await;
        });
        Self { handle }
    }
}

#[wasm_bindgen]
impl IdentityWatch {
    pub fn stop(&self) {
        self.handle.abort();
    }
}
