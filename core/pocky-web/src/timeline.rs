use std::{cell::RefCell, rc::Rc};

use futures_util::future::{AbortHandle, Abortable};
use js_sys::Function;
use pocky_core::Timeline;
use wasm_bindgen::prelude::*;

use crate::{error, notify};

#[wasm_bindgen]
pub struct RoomTimeline {
    inner: Rc<Timeline>,
    watchers: RefCell<Vec<AbortHandle>>,
}

impl RoomTimeline {
    pub(crate) fn new(timeline: Timeline) -> Self {
        Self {
            inner: Rc::new(timeline),
            watchers: RefCell::new(Vec::new()),
        }
    }
}

#[wasm_bindgen]
impl RoomTimeline {
    pub async fn watch(&self, on_diffs: Function) {
        let timeline = self.inner.clone();
        let (handle, registration) = AbortHandle::new_pair();
        self.watchers.borrow_mut().push(handle);

        let watch = async move {
            pocky_core::watch_timeline(&timeline, |diffs| notify(&on_diffs, &diffs)).await;
        };
        let _ = Abortable::new(watch, registration).await;
    }

    pub fn close(&self) {
        for handle in self.watchers.borrow_mut().drain(..) {
            handle.abort();
        }
    }

    #[wasm_bindgen(js_name = paginateBackwards)]
    pub async fn paginate_backwards(&self) -> Result<bool, JsValue> {
        let timeline = self.inner.clone();
        pocky_core::paginate_backwards(&timeline)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = sendText)]
    pub async fn send_text(&self, text: String) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::send_text(&timeline, &text).await.map_err(error)
    }

    #[wasm_bindgen(js_name = sendReply)]
    pub async fn send_reply(&self, text: String, in_reply_to: String) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::send_reply(&timeline, &text, &in_reply_to)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = editText)]
    pub async fn edit_text(&self, item_key: String, text: String) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::edit_text(&timeline, &item_key, &text)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = toggleReaction)]
    pub async fn toggle_reaction(&self, item_key: String, key: String) -> Result<bool, JsValue> {
        let timeline = self.inner.clone();
        pocky_core::toggle_reaction(&timeline, &item_key, &key)
            .await
            .map_err(error)
    }

    pub async fn redact(&self, item_key: String) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::redact(&timeline, &item_key)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = sendImage)]
    pub async fn send_image(
        &self,
        bytes: Vec<u8>,
        caption: Option<String>,
        full_quality: bool,
    ) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::send_image(&timeline, &bytes, caption.as_deref(), full_quality)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = sendFile)]
    pub async fn send_file(
        &self,
        bytes: Vec<u8>,
        filename: String,
        mime: String,
    ) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::send_file(&timeline, bytes, &filename, &mime)
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = sendLocation)]
    pub async fn send_location(
        &self,
        latitude: f64,
        longitude: f64,
        accuracy_m: Option<f64>,
        precision: String,
    ) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        let point = pocky_core::GeoPoint {
            latitude,
            longitude,
            accuracy_m,
        };
        pocky_core::send_location(&timeline, point, crate::location::precision(&precision))
            .await
            .map_err(error)
    }

    #[wasm_bindgen(js_name = markAsRead)]
    pub async fn mark_as_read(&self) -> Result<(), JsValue> {
        let timeline = self.inner.clone();
        pocky_core::mark_as_read(&timeline).await.map_err(error)
    }
}
