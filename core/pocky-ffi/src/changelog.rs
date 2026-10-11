#[derive(Debug, Clone, uniffi::Record)]
pub struct ReleaseNotes {
    pub version: String,
    pub version_code: u32,
    pub channel: String,
    pub date: String,
    pub added: Vec<String>,
    pub fixed: Vec<String>,
}

impl From<pocky_core::Release> for ReleaseNotes {
    fn from(release: pocky_core::Release) -> Self {
        Self {
            version: release.version,
            version_code: release.version_code,
            channel: release.channel,
            date: release.date,
            added: release.added,
            fixed: release.fixed,
        }
    }
}

#[uniffi::export]
pub fn changelog(lang: String, up_to_version_code: u32) -> Vec<ReleaseNotes> {
    pocky_core::changelog(&lang, up_to_version_code)
        .into_iter()
        .map(Into::into)
        .collect()
}
