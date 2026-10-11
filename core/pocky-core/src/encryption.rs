use std::collections::HashSet;

use futures_util::StreamExt;
use matrix_sdk::{
    encryption::{
        recovery::{RecoveryError, RecoveryState as SdkRecoveryState},
        secret_storage::SecretStorageError,
        BackupDownloadStrategy, CrossSigningResetAuthType, EncryptionSettings,
    },
    ruma::{
        api::client::{
            account::change_password,
            uiaa::{AuthData, MatrixUserIdentifier, Password, UserIdentifier},
        },
        assign, RoomId,
    },
    Client,
};

use crate::error::{CoreError, Result};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RecoveryState {
    Unknown,
    Enabled,
    Disabled,
    Incomplete,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EncryptionStatus {
    pub recovery: RecoveryState,
    pub backup_on_server: bool,
    pub backup_enabled: bool,
    pub secret_storage: bool,
    pub has_identity: bool,
    pub device_verified: bool,
    pub other_devices: u32,
    pub verified_other_devices: u32,
}

impl EncryptionStatus {
    pub fn can_recover(&self) -> bool {
        self.secret_storage && !self.device_verified
    }

    pub fn can_verify_with_device(&self) -> bool {
        self.has_identity && !self.device_verified && self.verified_other_devices > 0
    }

    pub fn can_enable_recovery(&self) -> bool {
        !self.secret_storage
            && !self.backup_on_server
            && (self.device_verified || !self.has_identity)
    }
}

pub const MIN_PASSPHRASE_CHARS: usize = 8;
pub const RECOMMENDED_PASSPHRASE_CHARS: usize = 16;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum PassphraseStrength {
    TooShort,
    NoSymbol,
    Weak,
    Fair,
    Strong,
}

impl PassphraseStrength {
    pub fn is_acceptable(self) -> bool {
        self >= Self::Fair
    }
}

fn is_symbol(c: char) -> bool {
    !c.is_alphanumeric() && !c.is_whitespace()
}

pub fn passphrase_strength(passphrase: &str) -> PassphraseStrength {
    let chars: Vec<char> = passphrase.trim().chars().collect();
    if chars.len() < MIN_PASSPHRASE_CHARS {
        return PassphraseStrength::TooShort;
    }
    if !chars.iter().copied().any(is_symbol) {
        return PassphraseStrength::NoSymbol;
    }

    let distinct = chars
        .iter()
        .flat_map(|c| c.to_lowercase())
        .collect::<HashSet<_>>()
        .len();
    if distinct < 5 {
        return PassphraseStrength::Weak;
    }

    let words = passphrase
        .split(|c: char| !c.is_alphanumeric())
        .filter(|word| word.chars().count() >= 3)
        .count();
    let classes = [
        chars.iter().any(|c| c.is_lowercase()),
        chars.iter().any(|c| c.is_uppercase()),
        chars.iter().any(|c| c.is_numeric()),
        chars.iter().copied().any(is_symbol),
    ]
    .into_iter()
    .filter(|&present| present)
    .count();

    if chars.len() >= RECOMMENDED_PASSPHRASE_CHARS && (words >= 3 || classes >= 3) {
        PassphraseStrength::Strong
    } else {
        PassphraseStrength::Fair
    }
}

fn checked_passphrase<'a>(passphrase: &'a str, account_password: Option<&str>) -> Result<&'a str> {
    let passphrase = passphrase.trim();
    if !passphrase_strength(passphrase).is_acceptable() {
        return Err(CoreError::WeakPassphrase);
    }
    if account_password.is_some_and(|password| password.trim() == passphrase) {
        return Err(CoreError::WeakPassphrase);
    }
    Ok(passphrase)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LogoutRisk {
    pub last_device: bool,
    pub recovery_enabled: bool,
}

impl LogoutRisk {
    pub fn loses_history(&self) -> bool {
        self.last_device && !self.recovery_enabled
    }
}

pub(crate) fn settings() -> EncryptionSettings {
    EncryptionSettings {
        auto_enable_cross_signing: false,
        backup_download_strategy: BackupDownloadStrategy::AfterDecryptionFailure,
        auto_enable_backups: false,
    }
}

pub async fn encryption_status(client: &Client) -> Result<EncryptionStatus> {
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let own_device_id = client.device_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let encryption = client.encryption();
    encryption.wait_for_e2ee_initialization_tasks().await;
    let _ = encryption.request_user_identity(&user_id).await;

    let identity = encryption
        .get_user_identity(&user_id)
        .await
        .map_err(|e| CoreError::Encryption(e.to_string()))?;

    let devices = encryption
        .get_user_devices(&user_id)
        .await
        .map_err(|e| CoreError::Encryption(e.to_string()))?;

    let mut device_verified = false;
    let mut other_devices = 0;
    let mut verified_other_devices = 0;
    for device in devices.devices() {
        if device.device_id() == own_device_id {
            device_verified = device.is_cross_signed_by_owner();
        } else {
            other_devices += 1;
            if device.is_cross_signed_by_owner() {
                verified_other_devices += 1;
            }
        }
    }

    Ok(EncryptionStatus {
        recovery: match encryption.recovery().state() {
            SdkRecoveryState::Enabled => RecoveryState::Enabled,
            SdkRecoveryState::Disabled => RecoveryState::Disabled,
            SdkRecoveryState::Incomplete => RecoveryState::Incomplete,
            _ => RecoveryState::Unknown,
        },
        backup_enabled: encryption.backups().are_enabled().await,
        backup_on_server: encryption
            .backups()
            .exists_on_server()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))?,
        secret_storage: encryption
            .secret_storage()
            .is_enabled()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))?,
        has_identity: identity.is_some(),
        device_verified,
        other_devices,
        verified_other_devices,
    })
}

pub async fn recover(client: &Client, key: &str) -> Result<EncryptionStatus> {
    let key = key.trim();
    if key.is_empty() {
        return Err(CoreError::InvalidRecoveryKey);
    }

    client
        .encryption()
        .recovery()
        .recover(key)
        .await
        .map_err(recovery_error)?;

    encryption_status(client).await
}

pub async fn logout_risk(client: &Client) -> Result<LogoutRisk> {
    let recovery = client.encryption().recovery();
    let last_device = recovery
        .is_last_device()
        .await
        .map_err(|e| CoreError::Encryption(e.to_string()))?;

    Ok(LogoutRisk {
        last_device,
        recovery_enabled: recovery.state() == SdkRecoveryState::Enabled,
    })
}

pub async fn enable_recovery(client: &Client, password: &str, passphrase: &str) -> Result<String> {
    let passphrase = checked_passphrase(passphrase, Some(password))?;
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let encryption = client.encryption();

    if let Err(err) = encryption.bootstrap_cross_signing_if_needed(None).await {
        let Some(info) = err.as_uiaa_response() else {
            return Err(err.into());
        };
        let mut auth = Password::new(
            UserIdentifier::Matrix(MatrixUserIdentifier::new(user_id.to_string())),
            password.to_owned(),
        );
        auth.session = info.session.clone();
        encryption
            .bootstrap_cross_signing(Some(AuthData::Password(auth)))
            .await?;
    }

    encryption
        .recovery()
        .enable()
        .with_passphrase(passphrase)
        .wait_for_backups_to_upload()
        .await
        .map_err(recovery_error)
}

pub async fn reset_identity(client: &Client, password: &str, passphrase: &str) -> Result<String> {
    let passphrase = checked_passphrase(passphrase, Some(password))?;
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let recovery = client.encryption().recovery();

    if let Some(handle) = recovery.reset_identity().await.map_err(recovery_error)? {
        let auth = match handle.auth_type() {
            CrossSigningResetAuthType::Uiaa(info) => {
                let mut auth = Password::new(
                    UserIdentifier::Matrix(MatrixUserIdentifier::new(user_id.to_string())),
                    password.to_owned(),
                );
                auth.session = info.session.clone();
                AuthData::Password(auth)
            }
            CrossSigningResetAuthType::OAuth(_) => {
                handle.cancel().await;
                return Err(CoreError::Encryption(
                    "identity reset needs approval in the browser".to_owned(),
                ));
            }
        };
        if let Err(err) = handle.reset(Some(auth)).await {
            handle.cancel().await;
            return Err(recovery_error(err));
        }
    }

    recovery
        .enable()
        .with_passphrase(passphrase)
        .wait_for_backups_to_upload()
        .await
        .map_err(recovery_error)
}

pub async fn change_recovery_passphrase(client: &Client, passphrase: &str) -> Result<String> {
    let passphrase = checked_passphrase(passphrase, None)?;
    let encryption = client.encryption();
    if encryption.recovery().state() != SdkRecoveryState::Enabled {
        return Err(CoreError::Encryption("recovery is not enabled".to_owned()));
    }

    encryption
        .recovery()
        .reset_key()
        .with_passphrase(passphrase)
        .await
        .map_err(recovery_error)
}

pub async fn change_password(
    client: &Client,
    current: &str,
    new_password: &str,
    sign_out_others: bool,
) -> Result<()> {
    if new_password.chars().count() < 8 || new_password == current {
        return Err(CoreError::WeakPassword);
    }
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let request = |auth: Option<AuthData>| {
        assign!(change_password::v3::Request::new(new_password.to_owned()), {
            auth,
            logout_devices: sign_out_others,
        })
    };

    let session = match client.send(request(None)).await {
        Ok(_) => return Ok(()),
        Err(err) => match err.as_uiaa_response() {
            Some(info) => info.session.clone(),
            None => return Err(err.into()),
        },
    };

    let mut auth = Password::new(
        UserIdentifier::Matrix(MatrixUserIdentifier::new(user_id.to_string())),
        current.to_owned(),
    );
    auth.session = session;
    client.send(request(Some(AuthData::Password(auth)))).await?;
    Ok(())
}

pub async fn download_room_keys(client: &Client, room_id: &RoomId) {
    let backups = client.encryption().backups();
    if backups.are_enabled().await {
        let _ = backups.download_room_keys_for_room(room_id).await;
    }
}

async fn download_all_room_keys(client: &Client) {
    for room in client.joined_rooms() {
        if room.encryption_state().is_encrypted() {
            download_room_keys(client, room.room_id()).await;
        }
    }
}

pub async fn watch_encryption(client: &Client, mut on_status: impl FnMut(EncryptionStatus)) {
    let encryption = client.encryption();
    let mut verification = encryption.verification_state();
    let mut backup = Box::pin(encryption.backups().state_stream());
    let mut backup_enabled = encryption.backups().are_enabled().await;

    loop {
        if let Ok(status) = encryption_status(client).await {
            if status.backup_enabled && !backup_enabled {
                let background = client.clone();
                matrix_sdk::executor::spawn(
                    async move { download_all_room_keys(&background).await },
                );
            }
            backup_enabled = status.backup_enabled;
            on_status(status);
        }

        tokio::select! {
            state = verification.next() => if state.is_none() { break },
            state = backup.next() => if state.is_none() { break },
        }
    }
}

fn recovery_error(err: RecoveryError) -> CoreError {
    match err {
        RecoveryError::SecretStorage(SecretStorageError::SecretStorageKey(_)) => {
            CoreError::InvalidRecoveryKey
        }
        RecoveryError::Sdk(sdk) => sdk.into(),
        other => CoreError::Encryption(other.to_string()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn status() -> EncryptionStatus {
        EncryptionStatus {
            recovery: RecoveryState::Incomplete,
            backup_on_server: true,
            backup_enabled: false,
            secret_storage: true,
            has_identity: true,
            device_verified: false,
            other_devices: 1,
            verified_other_devices: 1,
        }
    }

    #[test]
    fn passphrase_strength_needs_length_and_a_symbol() {
        assert_eq!(passphrase_strength("kocka!"), PassphraseStrength::TooShort);
        assert_eq!(
            passphrase_strength("kockapes"),
            PassphraseStrength::NoSymbol
        );
        assert_eq!(
            passphrase_strength("modry kopec"),
            PassphraseStrength::NoSymbol
        );
        assert_eq!(passphrase_strength("aaaa!!!!"), PassphraseStrength::Weak);
        assert_eq!(passphrase_strength("kocka-pes"), PassphraseStrength::Fair);
        assert_eq!(
            passphrase_strength("Pokecio2026!"),
            PassphraseStrength::Fair
        );
        assert_eq!(
            passphrase_strength("modrý-kopec-ráno-káva"),
            PassphraseStrength::Strong
        );
    }

    #[test]
    fn passphrase_must_differ_from_account_password() {
        assert!(checked_passphrase("modrý-kopec-ráno-káva", None).is_ok());
        assert!(matches!(
            checked_passphrase("modrý-kopec-ráno-káva", Some("modrý-kopec-ráno-káva")),
            Err(CoreError::WeakPassphrase)
        ));
        assert!(matches!(
            checked_passphrase("heslo", None),
            Err(CoreError::WeakPassphrase)
        ));
    }

    #[test]
    fn unverified_device_with_secret_storage_can_recover() {
        assert!(status().can_recover());
        assert!(status().can_verify_with_device());
    }

    #[test]
    fn verified_device_needs_nothing() {
        let status = EncryptionStatus {
            device_verified: true,
            ..status()
        };

        assert!(!status.can_recover());
        assert!(!status.can_verify_with_device());
    }

    #[test]
    fn no_secret_storage_means_no_recovery_key() {
        let status = EncryptionStatus {
            secret_storage: false,
            verified_other_devices: 0,
            ..status()
        };

        assert!(!status.can_recover());
        assert!(!status.can_verify_with_device());
    }

    #[test]
    fn recovery_can_be_enabled_only_on_fresh_accounts() {
        let fresh = EncryptionStatus {
            recovery: RecoveryState::Disabled,
            backup_on_server: false,
            secret_storage: false,
            has_identity: false,
            device_verified: false,
            other_devices: 0,
            verified_other_devices: 0,
            ..status()
        };
        let unverified_with_identity = EncryptionStatus {
            has_identity: true,
            ..fresh.clone()
        };

        assert!(fresh.can_enable_recovery());
        assert!(!status().can_enable_recovery());
        assert!(!unverified_with_identity.can_enable_recovery());
    }

    #[test]
    fn logout_risk_only_without_recovery_on_last_device() {
        let risky = LogoutRisk {
            last_device: true,
            recovery_enabled: false,
        };

        assert!(risky.loses_history());
        assert!(!LogoutRisk {
            recovery_enabled: true,
            ..risky
        }
        .loses_history());
        assert!(!LogoutRisk {
            last_device: false,
            ..risky
        }
        .loses_history());
    }

    #[test]
    fn settings_never_create_identity_or_backup_on_their_own() {
        let settings = settings();

        assert!(!settings.auto_enable_cross_signing);
        assert!(!settings.auto_enable_backups);
        assert!(matches!(
            settings.backup_download_strategy,
            BackupDownloadStrategy::AfterDecryptionFailure
        ));
    }

    #[tokio::test]
    async fn empty_recovery_key_is_rejected_offline() {
        let dir = tempfile::tempdir().unwrap();
        let client = crate::build_client(
            crate::Server::Url("https://matrix.example.org"),
            dir.path(),
            None,
        )
        .await
        .unwrap();

        let result = recover(&client, "   ").await;

        assert!(matches!(result, Err(CoreError::InvalidRecoveryKey)));
    }

    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_wrong_recovery_key_is_rejected() {
        use crate::{build_client, login, logout, Server, SERVER_NAME};

        let user = std::env::var("POCKY_TEST_USER").expect("POCKY_TEST_USER");
        let password = std::env::var("POCKY_TEST_PASSWORD").expect("POCKY_TEST_PASSWORD");
        let dir = tempfile::tempdir().unwrap();

        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP test")
            .await
            .unwrap();

        let fake = "EsTj 3yST y93F SLpB jJsz eAXc 2XzA ygD3 w69H fGaN TKBj jXEd";
        let result = Box::pin(recover(&client, fake)).await;

        logout(&client).await.unwrap();
        assert!(
            matches!(result, Err(CoreError::InvalidRecoveryKey)),
            "{result:?}"
        );
    }

    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_encryption_status() {
        use crate::{build_client, login, logout, Server, SERVER_NAME};

        let user = std::env::var("POCKY_TEST_USER").expect("POCKY_TEST_USER");
        let password = std::env::var("POCKY_TEST_PASSWORD").expect("POCKY_TEST_PASSWORD");
        let dir = tempfile::tempdir().unwrap();

        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP test")
            .await
            .unwrap();

        let status = Box::pin(encryption_status(&client)).await.unwrap();

        println!("recovery state:          {:?}", status.recovery);
        println!("key backup on server:    {}", status.backup_on_server);
        println!("key backup enabled here: {}", status.backup_enabled);
        println!("secret storage (4S):     {}", status.secret_storage);
        println!("cross-signing identity:  {}", status.has_identity);
        println!("this device verified:    {}", status.device_verified);
        println!("other devices:           {}", status.other_devices);
        println!("verified other devices:  {}", status.verified_other_devices);
        println!("can recover with key:    {}", status.can_recover());
        println!(
            "can verify with device:  {}",
            status.can_verify_with_device()
        );

        logout(&client).await.unwrap();
    }
    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER2 and POCKY_TEST_PASSWORD2"]
    async fn live_change_password_and_back() {
        use crate::{build_client, login, logout, Server, SERVER_NAME};

        let user = std::env::var("POCKY_TEST_USER2").expect("POCKY_TEST_USER2");
        let password = std::env::var("POCKY_TEST_PASSWORD2").expect("POCKY_TEST_PASSWORD2");
        let temporary = format!("{password}-docasne");
        let dir = tempfile::tempdir().unwrap();

        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP test")
            .await
            .unwrap();

        let wrong = change_password(&client, "spatne-heslo", &temporary, false).await;
        assert!(
            matches!(wrong, Err(CoreError::InvalidCredentials)),
            "{wrong:?}"
        );

        change_password(&client, &password, &temporary, false)
            .await
            .expect("change to temporary password");
        change_password(&client, &temporary, &password, false)
            .await
            .expect(
                "change back, the account now has the original password with -docasne appended",
            );

        logout(&client).await.unwrap();
    }
}
