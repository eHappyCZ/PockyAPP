use crate::{run, PockyClient, PockyError};

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppLock {
    pub enabled: bool,
    pub biometrics: bool,
    pub timeout_seconds: u32,
    pub blocked_until_ms: u64,
}

impl From<pocky_core::AppLockSettings> for AppLock {
    fn from(settings: pocky_core::AppLockSettings) -> Self {
        Self {
            enabled: settings.enabled,
            biometrics: settings.biometrics,
            timeout_seconds: settings.timeout_seconds,
            blocked_until_ms: settings.blocked_until_ms,
        }
    }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum UnlockOutcome {
    Unlocked,
    WrongPin { attempts_left: u32 },
    Blocked { until_ms: u64 },
}

impl From<pocky_core::UnlockResult> for UnlockOutcome {
    fn from(result: pocky_core::UnlockResult) -> Self {
        match result {
            pocky_core::UnlockResult::Unlocked => Self::Unlocked,
            pocky_core::UnlockResult::WrongPin { attempts_left } => {
                Self::WrongPin { attempts_left }
            }
            pocky_core::UnlockResult::Blocked { until_ms } => Self::Blocked { until_ms },
        }
    }
}

#[uniffi::export]
pub fn app_lock_pin_length() -> u32 {
    pocky_core::PIN_LENGTH as u32
}

#[uniffi::export]
pub fn app_lock_pin_allowed(pin: String) -> bool {
    pocky_core::pin_allowed(&pin)
}

#[uniffi::export]
impl PockyClient {
    pub async fn app_lock(&self) -> Result<AppLock, PockyError> {
        let client = self.client()?;
        let settings = run(async move { pocky_core::app_lock_settings(&client).await }).await?;
        Ok(settings.into())
    }

    pub async fn enable_app_lock(
        &self,
        pin: String,
        biometrics: bool,
        timeout_seconds: u32,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move {
            pocky_core::enable_app_lock(&client, &pin, biometrics, timeout_seconds).await
        })
        .await
    }

    pub async fn update_app_lock(
        &self,
        biometrics: bool,
        timeout_seconds: u32,
    ) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::update_app_lock(&client, biometrics, timeout_seconds).await })
            .await
    }

    pub async fn unlock_app(&self, pin: String) -> Result<UnlockOutcome, PockyError> {
        let client = self.client()?;
        let result = run(async move { pocky_core::unlock_app(&client, &pin).await }).await?;
        Ok(result.into())
    }

    pub async fn unlock_app_with_biometrics(&self) -> Result<(), PockyError> {
        let client = self.client()?;
        run(async move { pocky_core::unlock_app_with_biometrics(&client).await }).await
    }

    pub async fn disable_app_lock(&self, pin: String) -> Result<UnlockOutcome, PockyError> {
        let client = self.client()?;
        let result = run(async move { pocky_core::disable_app_lock(&client, &pin).await }).await?;
        Ok(result.into())
    }
}
