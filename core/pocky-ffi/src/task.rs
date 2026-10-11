use std::sync::Mutex;

use tokio::task::JoinHandle;

#[derive(uniffi::Object)]
pub struct TaskHandle {
    handle: Mutex<Option<JoinHandle<()>>>,
}

impl TaskHandle {
    pub(crate) fn new(handle: JoinHandle<()>) -> Self {
        Self {
            handle: Mutex::new(Some(handle)),
        }
    }
}

#[uniffi::export]
impl TaskHandle {
    pub fn cancel(&self) {
        if let Some(handle) = self.handle.lock().unwrap().take() {
            handle.abort();
        }
    }

    pub fn is_finished(&self) -> bool {
        self.handle
            .lock()
            .unwrap()
            .as_ref()
            .is_none_or(JoinHandle::is_finished)
    }
}

impl Drop for TaskHandle {
    fn drop(&mut self) {
        if let Some(handle) = self.handle.get_mut().unwrap().take() {
            handle.abort();
        }
    }
}
