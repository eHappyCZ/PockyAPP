use std::sync::Arc;

use pocky_core::{SasEmoji, VerificationFlow, VerificationPhase};

use crate::{run, task::TaskHandle, PockyError, RUNTIME};

#[derive(Debug, Clone, uniffi::Record)]
pub struct VerificationEmoji {
    pub index: u8,
    pub symbol: String,
    pub description: String,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum VerificationStep {
    Requested,
    Incoming,
    Ready,
    Starting,
    Emojis { emojis: Vec<VerificationEmoji> },
    Confirmed,
    Done,
    Cancelled { by_us: bool, reason: String },
}

#[uniffi::export(callback_interface)]
pub trait VerificationListener: Send + Sync {
    fn on_step(&self, step: VerificationStep);
}

#[uniffi::export(callback_interface)]
pub trait VerificationRequestListener: Send + Sync {
    fn on_request(&self, verification: Arc<Verification>);
}

#[derive(uniffi::Object)]
pub struct Verification {
    flow: Arc<VerificationFlow>,
}

impl Verification {
    pub(crate) fn flow(&self) -> Arc<VerificationFlow> {
        self.flow.clone()
    }

    pub(crate) fn new(flow: VerificationFlow) -> Arc<Self> {
        Arc::new(Self {
            flow: Arc::new(flow),
        })
    }
}

#[uniffi::export]
impl Verification {
    pub fn flow_id(&self) -> String {
        self.flow.flow_id().to_owned()
    }

    pub fn is_incoming(&self) -> bool {
        self.flow.is_incoming()
    }

    pub fn is_self_verification(&self) -> bool {
        self.flow.is_self_verification()
    }

    pub fn other_user_id(&self) -> String {
        self.flow.other_user_id()
    }

    pub fn observe(&self, listener: Box<dyn VerificationListener>) -> Arc<TaskHandle> {
        let flow = self.flow.clone();
        let handle = RUNTIME.spawn(async move {
            flow.watch(|phase| listener.on_step(phase.into())).await;
        });
        Arc::new(TaskHandle::new(handle))
    }

    pub async fn accept(&self) -> Result<(), PockyError> {
        let flow = self.flow.clone();
        run(async move { flow.accept().await }).await
    }

    pub async fn confirm(&self) -> Result<(), PockyError> {
        let flow = self.flow.clone();
        run(async move { flow.confirm().await }).await
    }

    pub async fn mismatch(&self) -> Result<(), PockyError> {
        let flow = self.flow.clone();
        run(async move { flow.mismatch().await }).await
    }

    pub async fn cancel(&self) -> Result<(), PockyError> {
        let flow = self.flow.clone();
        run(async move { flow.cancel().await }).await
    }
}

impl From<VerificationPhase> for VerificationStep {
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

impl From<SasEmoji> for VerificationEmoji {
    fn from(emoji: SasEmoji) -> Self {
        Self {
            index: emoji.index,
            symbol: emoji.symbol,
            description: emoji.description,
        }
    }
}
