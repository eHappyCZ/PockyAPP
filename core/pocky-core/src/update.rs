use std::{fmt::Write as _, path::Path, time::Duration};

use matrix_sdk::reqwest;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use tokio::{
    fs,
    io::{AsyncReadExt, AsyncWriteExt},
};

use crate::error::{CoreError, Result};

pub const UPDATE_MANIFEST: &str = "https://pokecio.com/updates/android.json";
const TRUSTED_ORIGIN: &str = "https://pokecio.com/";
const TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UpdateInfo {
    pub version_code: u32,
    pub version_name: String,
    pub published: Option<String>,
    pub url: String,
    pub sha256: String,
    pub size: Option<u64>,
    pub added: Vec<String>,
    pub fixed: Vec<String>,
}

#[derive(Deserialize)]
struct Manifest {
    #[serde(rename = "versionCode")]
    version_code: u32,
    #[serde(rename = "versionName")]
    version_name: String,
    published: Option<String>,
    url: String,
    sha256: String,
    size: Option<u64>,
    #[serde(rename = "minSdk")]
    min_sdk: Option<u32>,
    #[serde(default)]
    notes: std::collections::HashMap<String, Notes>,
}

#[derive(Deserialize, Default, Clone)]
struct Notes {
    #[serde(default)]
    new: Vec<String>,
    #[serde(default)]
    fixed: Vec<String>,
}

pub fn parse_update(
    json: &str,
    current_code: u32,
    sdk: u32,
    lang: &str,
) -> Result<Option<UpdateInfo>> {
    let manifest: Manifest =
        serde_json::from_str(json).map_err(|e| CoreError::Update(e.to_string()))?;

    if !manifest.url.starts_with(TRUSTED_ORIGIN) {
        return Err(CoreError::Update("untrusted download url".to_owned()));
    }
    let sha256 = manifest.sha256.to_ascii_lowercase();
    if sha256.len() != 64 || !sha256.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(CoreError::Update("invalid checksum".to_owned()));
    }
    if manifest.version_code <= current_code || manifest.min_sdk.is_some_and(|min| sdk < min) {
        return Ok(None);
    }

    let notes = manifest
        .notes
        .get(lang)
        .or_else(|| manifest.notes.get("en"))
        .cloned()
        .unwrap_or_default();

    Ok(Some(UpdateInfo {
        version_code: manifest.version_code,
        version_name: manifest.version_name,
        published: manifest.published,
        url: manifest.url,
        sha256,
        size: manifest.size,
        added: notes.new,
        fixed: notes.fixed,
    }))
}

fn http() -> Result<reqwest::Client> {
    reqwest::Client::builder()
        .timeout(TIMEOUT)
        .user_agent("PockyAPP")
        .build()
        .map_err(|e| CoreError::Network(e.to_string()))
}

pub async fn check_update(current_code: u32, sdk: u32, lang: &str) -> Result<Option<UpdateInfo>> {
    let response = http()?
        .get(UPDATE_MANIFEST)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|e| CoreError::Network(e.to_string()))?;
    let body = response
        .text()
        .await
        .map_err(|e| CoreError::Network(e.to_string()))?;
    parse_update(&body, current_code, sdk, lang)
}

pub async fn download_update(
    update: &UpdateInfo,
    target: &Path,
    mut on_progress: impl FnMut(u64, Option<u64>),
) -> Result<()> {
    let partial = target.with_extension("part");
    let mut response = reqwest::Client::builder()
        .user_agent("PockyAPP")
        .connect_timeout(TIMEOUT)
        .redirect(trusted_redirects())
        .build()
        .map_err(|e| CoreError::Network(e.to_string()))?
        .get(&update.url)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|e| CoreError::Network(e.to_string()))?;

    let total = response.content_length().or(update.size);
    let mut file = fs::File::create(&partial).await?;
    let mut hasher = Sha256::new();
    let mut received = 0u64;

    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|e| CoreError::Network(e.to_string()))?
    {
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
        received += chunk.len() as u64;
        on_progress(received, total);
    }
    file.flush().await?;
    drop(file);

    if hex(&hasher.finalize()) != update.sha256 {
        let _ = fs::remove_file(&partial).await;
        return Err(CoreError::Update("checksum mismatch".to_owned()));
    }
    fs::rename(&partial, target).await?;
    Ok(())
}

pub async fn verify_file(path: &Path, sha256: &str) -> Result<bool> {
    let mut file = fs::File::open(path).await?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer).await?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(hex(&hasher.finalize()) == sha256.to_ascii_lowercase())
}

fn trusted_redirects() -> reqwest::redirect::Policy {
    reqwest::redirect::Policy::custom(|attempt| {
        if attempt.previous().len() >= 5 {
            attempt.error("too many redirects")
        } else if attempt.url().as_str().starts_with(TRUSTED_ORIGIN) {
            attempt.follow()
        } else {
            attempt.error("redirect leaves trusted origin")
        }
    })
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().fold(String::with_capacity(64), |mut out, b| {
        let _ = write!(out, "{b:02x}");
        out
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const SUM: &str = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    fn manifest(code: u32, url: &str) -> String {
        format!(
            r#"{{
                "versionCode": {code},
                "versionName": "0.9.5 - BETA",
                "published": "2026-10-20",
                "url": "{url}",
                "sha256": "{SUM}",
                "size": 1000,
                "minSdk": 28,
                "notes": {{
                    "cs": {{ "new": ["Nová mapa"], "fixed": ["Oprava fotek"] }},
                    "en": {{ "new": ["New map"], "fixed": ["Photo fix"] }}
                }}
            }}"#
        )
    }

    #[test]
    fn newer_version_is_offered_in_language() {
        let json = manifest(905, "https://pokecio.com/download/pockyapp-0.9.5.apk");
        let update = parse_update(&json, 904, 36, "cs").unwrap().unwrap();
        assert_eq!(update.version_code, 905);
        assert_eq!(update.added, vec!["Nová mapa"]);
        assert_eq!(update.fixed, vec!["Oprava fotek"]);

        let english = parse_update(&json, 904, 36, "de").unwrap().unwrap();
        assert_eq!(english.added, vec!["New map"]);
    }

    #[test]
    fn same_or_older_version_is_ignored() {
        let json = manifest(904, "https://pokecio.com/download/a.apk");
        assert!(parse_update(&json, 904, 36, "cs").unwrap().is_none());
        assert!(parse_update(&json, 910, 36, "cs").unwrap().is_none());
    }

    #[test]
    fn too_old_android_is_ignored() {
        let json = manifest(905, "https://pokecio.com/download/a.apk");
        assert!(parse_update(&json, 904, 27, "cs").unwrap().is_none());
    }

    #[test]
    fn foreign_url_is_rejected() {
        let json = manifest(905, "https://evil.example/pockyapp.apk");
        assert!(parse_update(&json, 904, 36, "cs").is_err());
        let sneaky = manifest(905, "https://pokecio.com.evil.example/a.apk");
        assert!(parse_update(&sneaky, 904, 36, "cs").is_err());
    }

    #[tokio::test]
    async fn file_checksum_matches() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("empty.apk");
        fs::write(&path, b"").await.unwrap();
        assert!(verify_file(&path, SUM).await.unwrap());
        fs::write(&path, b"x").await.unwrap();
        assert!(!verify_file(&path, SUM).await.unwrap());
    }
}
