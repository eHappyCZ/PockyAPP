use std::rc::Rc;

use js_sys::Function;
use pocky_core::{SasEmoji, VerificationFlow, VerificationPhase};
use serde::Serialize;
use wasm_bindgen::prelude::*;

use crate::{error, notify};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Emoji {
    pub index: u8,
    pub symbol: String,
    pub description: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(
    tag = "step",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum Step {
    Requested,
    Incoming,
    Ready,
    Starting,
    Emojis { emojis: Vec<Emoji> },
    Confirmed,
    Done,
    Cancelled { by_us: bool, reason: String },
}

impl From<SasEmoji> for Emoji {
    fn from(emoji: SasEmoji) -> Self {
        Self {
            index: emoji.index,
            symbol: emoji.symbol,
            description: emoji.description,
        }
    }
}

impl From<VerificationPhase> for Step {
    fn from(phase: VerificationPhase) -> Self {
        match phase {
            VerificationPhase::Requested => Self::Requested,
            VerificationPhase::Incoming => Self::Incoming,
            VerificationPhase::Ready => Self::Ready,
            VerificationPhase::Starting => Self::Starting,
            VerificationPhase::Emojis(emojis) => Self::Emojis {
                emojis: emojis.into_iter().map(Into::into).collect(),
            },
            VerificationPhase::Confirmed => Self::Confirmed,
            VerificationPhase::Done => Self::Done,
            VerificationPhase::Cancelled { by_us, reason } => Self::Cancelled { by_us, reason },
        }
    }
}

#[wasm_bindgen]
pub struct Verification {
    flow: Rc<VerificationFlow>,
}

impl Verification {
    pub(crate) fn new(flow: VerificationFlow) -> Self {
        Self {
            flow: Rc::new(flow),
        }
    }
}

#[wasm_bindgen]
impl Verification {
    #[wasm_bindgen(js_name = flowId)]
    pub fn flow_id(&self) -> String {
        self.flow.flow_id().to_owned()
    }

    #[wasm_bindgen(js_name = isIncoming)]
    pub fn is_incoming(&self) -> bool {
        self.flow.is_incoming()
    }

    pub async fn watch(&self, on_step: Function) {
        let flow = self.flow.clone();
        flow.watch(|phase| notify(&on_step, &Step::from(phase)))
            .await;
    }

    pub async fn accept(&self) -> Result<(), JsValue> {
        let flow = self.flow.clone();
        flow.accept().await.map_err(error)
    }

    pub async fn confirm(&self) -> Result<(), JsValue> {
        let flow = self.flow.clone();
        flow.confirm().await.map_err(error)
    }

    pub async fn mismatch(&self) -> Result<(), JsValue> {
        let flow = self.flow.clone();
        flow.mismatch().await.map_err(error)
    }

    pub async fn cancel(&self) -> Result<(), JsValue> {
        let flow = self.flow.clone();
        flow.cancel().await.map_err(error)
    }
}
