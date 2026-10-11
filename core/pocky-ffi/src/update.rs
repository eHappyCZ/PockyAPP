use std::path::PathBuf;

use pocky_core::UpdateInfo;

use crate::{run, PockyError};

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppUpdate {
    pub version_code: u32,
    pub version_name: String,
    pub published: Option<String>,
    pub url: String,
    pub sha256: String,
    pub size: Option<u64>,
    pub added: Vec<String>,
    pub fixed: Vec<String>,
}

#[uniffi::export(callback_interface)]
pub trait DownloadListener: Send + Sync {
    fn on_progress(&self, received: u64, total: Option<u64>);
}

impl From<UpdateInfo> for AppUpdate {
    fn from(info: UpdateInfo) -> Self {
        Self {
            version_code: info.version_code,
            version_name: info.version_name,
            published: info.published,
            url: info.url,
            sha256: info.sha256,
            size: info.size,
            added: info.added,
            fixed: info.fixed,
        }
    }
}

impl From<AppUpdate> for UpdateInfo {
    fn from(update: AppUpdate) -> Self {
        Self {
            version_code: update.version_code,
            version_name: update.version_name,
            published: update.published,
            url: update.url,
            sha256: update.sha256,
            size: update.size,
            added: update.added,
            fixed: update.fixed,
        }
    }
}

#[uniffi::export]
pub async fn check_for_update(
    current_code: u32,
    sdk: u32,
    lang: String,
) -> Result<Option<AppUpdate>, PockyError> {
    let update =
        run(async move { pocky_core::check_update(current_code, sdk, &lang).await }).await?;
    Ok(update.map(Into::into))
}

#[uniffi::export]
pub async fn download_app_update(
    update: AppUpdate,
    target: String,
    listener: Box<dyn DownloadListener>,
) -> Result<(), PockyError> {
    let info = UpdateInfo::from(update);
    run(async move {
        pocky_core::download_update(&info, &PathBuf::from(target), |received, total| {
            listener.on_progress(received, total);
        })
        .await
    })
    .await
}
