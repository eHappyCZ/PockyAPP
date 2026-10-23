use matrix_sdk::{ruma::MilliSecondsSinceUnixEpoch, Client};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::Sha256;

use crate::error::{CoreError, Result};

const KEY: &[u8] = b"pocky.app_lock";
const ITERATIONS: u32 = 600_000;
const LEGACY_ITERATIONS: u32 = 100_000;
const FREE_ATTEMPTS: u32 = 5;
const FIRST_BLOCK_MS: u64 = 30_000;
const MAX_BLOCK_MS: u64 = 60 * 60 * 1000;

pub const PIN_LENGTH: usize = 6;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AppLockSettings {
    pub enabled: bool,
    pub biometrics: bool,
    pub timeout_seconds: u32,
    pub blocked_until_ms: u64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UnlockResult {
    Unlocked,
    WrongPin { attempts_left: u32 },
    Blocked { until_ms: u64 },
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
struct StoredLock {
    salt: String,
    hash: String,
    iterations: u32,
    biometrics: bool,
    timeout_seconds: u32,
    failed: u32,
    blocked_until_ms: u64,
}

impl StoredLock {
    fn enabled(&self) -> bool {
        !self.hash.is_empty()
    }

    fn effective_iterations(&self) -> u32 {
        if self.iterations == 0 {
            LEGACY_ITERATIONS
        } else {
            self.iterations
        }
    }

    fn settings(&self) -> AppLockSettings {
        AppLockSettings {
            enabled: self.enabled(),
            biometrics: self.enabled() && self.biometrics,
            timeout_seconds: self.timeout_seconds,
            blocked_until_ms: self.blocked_until_ms,
        }
    }

    fn check(&mut self, pin: &str, now_ms: u64) -> UnlockResult {
        if !self.enabled() {
            return UnlockResult::Unlocked;
        }
        if now_ms < self.blocked_until_ms {
            return UnlockResult::Blocked {
                until_ms: self.blocked_until_ms,
            };
        }
        let iterations = self.effective_iterations();
        if same_hash(&hash_pin(pin, &self.salt, iterations), &self.hash) {
            self.failed = 0;
            self.blocked_until_ms = 0;
            if iterations < ITERATIONS {
                self.hash = hash_pin(pin, &self.salt, ITERATIONS);
                self.iterations = ITERATIONS;
            }
            return UnlockResult::Unlocked;
        }

        self.failed += 1;
        if self.failed < FREE_ATTEMPTS {
            return UnlockResult::WrongPin {
                attempts_left: FREE_ATTEMPTS - self.failed,
            };
        }
        let doublings = (self.failed - FREE_ATTEMPTS).min(10);
        let block = (FIRST_BLOCK_MS << doublings).min(MAX_BLOCK_MS);
        self.blocked_until_ms = now_ms + block;
        UnlockResult::Blocked {
            until_ms: self.blocked_until_ms,
        }
    }
}

fn valid_pin(pin: &str) -> bool {
    pin.len() == PIN_LENGTH && pin.chars().all(|c| c.is_ascii_digit())
}

pub fn pin_allowed(pin: &str) -> bool {
    if !valid_pin(pin) {
        return false;
    }
    let digits: Vec<i32> = pin.bytes().map(|b| i32::from(b - b'0')).collect();
    let steps: Vec<i32> = digits.windows(2).map(|pair| pair[1] - pair[0]).collect();
    let constant = steps.iter().all(|&step| step == steps[0]);
    !(constant && steps[0].abs() <= 1)
}

fn hash_pin(pin: &str, salt: &str, iterations: u32) -> String {
    let mut out = [0u8; 32];
    pbkdf2::pbkdf2_hmac::<Sha256>(pin.as_bytes(), salt.as_bytes(), iterations, &mut out);
    hex(&out)
}

fn same_hash(a: &str, b: &str) -> bool {
    let (a, b) = (a.as_bytes(), b.as_bytes());
    a.len() == b.len() && a.iter().zip(b).fold(0u8, |diff, (x, y)| diff | (x ^ y)) == 0
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

fn now_ms() -> u64 {
    u64::from(MilliSecondsSinceUnixEpoch::now().get())
}

async fn load(client: &Client) -> Result<StoredLock> {
    let stored = client
        .state_store()
        .get_custom_value(KEY)
        .await
        .map_err(matrix_sdk::Error::from)?;
    Ok(stored
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default())
}

async fn save(client: &Client, lock: &StoredLock) -> Result<()> {
    let bytes = serde_json::to_vec(lock).expect("app lock is always serializable");
    client
        .state_store()
        .set_custom_value_no_read(KEY, bytes)
        .await
        .map_err(matrix_sdk::Error::from)?;
    Ok(())
}

pub async fn app_lock_settings(client: &Client) -> Result<AppLockSettings> {
    Ok(load(client).await?.settings())
}

pub async fn enable_app_lock(
    client: &Client,
    pin: &str,
    biometrics: bool,
    timeout_seconds: u32,
) -> Result<()> {
    if !pin_allowed(pin) {
        return Err(CoreError::InvalidPin);
    }
    let mut salt = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut salt);
    let salt = hex(&salt);
    let lock = StoredLock {
        hash: hash_pin(pin, &salt, ITERATIONS),
        iterations: ITERATIONS,
        salt,
        biometrics,
        timeout_seconds,
        failed: 0,
        blocked_until_ms: 0,
    };
    save(client, &lock).await
}

pub async fn update_app_lock(
    client: &Client,
    biometrics: bool,
    timeout_seconds: u32,
) -> Result<()> {
    let mut lock = load(client).await?;
    if !lock.enabled() {
        return Ok(());
    }
    lock.biometrics = biometrics;
    lock.timeout_seconds = timeout_seconds;
    save(client, &lock).await
}

pub async fn unlock_app(client: &Client, pin: &str) -> Result<UnlockResult> {
    let mut lock = load(client).await?;
    let result = lock.check(pin, now_ms());
    save(client, &lock).await?;
    Ok(result)
}

pub async fn unlock_app_with_biometrics(client: &Client) -> Result<()> {
    let mut lock = load(client).await?;
    if lock.biometrics {
        lock.failed = 0;
        lock.blocked_until_ms = 0;
        save(client, &lock).await?;
    }
    Ok(())
}

pub async fn disable_app_lock(client: &Client, pin: &str) -> Result<UnlockResult> {
    let mut lock = load(client).await?;
    let result = lock.check(pin, now_ms());
    if result == UnlockResult::Unlocked {
        save(client, &StoredLock::default()).await?;
    } else {
        save(client, &lock).await?;
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lock(pin: &str) -> StoredLock {
        StoredLock {
            hash: hash_pin(pin, "sul", ITERATIONS),
            salt: "sul".to_owned(),
            iterations: ITERATIONS,
            ..StoredLock::default()
        }
    }

    #[test]
    fn legacy_hash_is_upgraded_after_unlock() {
        let mut lock = StoredLock {
            hash: hash_pin("246810", "sul", LEGACY_ITERATIONS),
            salt: "sul".to_owned(),
            ..StoredLock::default()
        };
        assert_eq!(lock.check("246810", 0), UnlockResult::Unlocked);
        assert_eq!(lock.iterations, ITERATIONS);
        assert_eq!(lock.hash, hash_pin("246810", "sul", ITERATIONS));
        assert_eq!(lock.check("246810", 0), UnlockResult::Unlocked);
    }

    #[test]
    fn hashes_compare_by_content() {
        assert!(same_hash("abc", "abc"));
        assert!(!same_hash("abc", "abd"));
        assert!(!same_hash("abc", "ab"));
    }

    #[test]
    fn pin_must_be_six_digits() {
        assert!(valid_pin("123456"));
        assert!(!valid_pin("12345"));
        assert!(!valid_pin("12345a"));
        assert!(!valid_pin("1234567"));
    }

    #[test]
    fn trivial_pins_are_refused() {
        assert!(!pin_allowed("000000"));
        assert!(!pin_allowed("123456"));
        assert!(!pin_allowed("654321"));
        assert!(pin_allowed("135790"));
        assert!(pin_allowed("246810"));
        assert!(pin_allowed("112233"));
    }

    #[test]
    fn right_pin_unlocks_and_resets_failures() {
        let mut lock = lock("246810");
        assert_eq!(
            lock.check("000000", 0),
            UnlockResult::WrongPin { attempts_left: 4 }
        );
        assert_eq!(lock.check("246810", 0), UnlockResult::Unlocked);
        assert_eq!(lock.failed, 0);
    }

    #[test]
    fn too_many_failures_block_with_growing_wait() {
        let mut lock = lock("246810");
        for _ in 0..4 {
            lock.check("000000", 0);
        }
        assert_eq!(
            lock.check("000000", 0),
            UnlockResult::Blocked { until_ms: 30_000 }
        );
        assert_eq!(
            lock.check("246810", 10_000),
            UnlockResult::Blocked { until_ms: 30_000 }
        );
        assert_eq!(
            lock.check("000000", 30_000),
            UnlockResult::Blocked { until_ms: 90_000 }
        );
        assert_eq!(lock.check("246810", 90_000), UnlockResult::Unlocked);
    }

    #[test]
    fn disabled_lock_never_asks() {
        let mut lock = StoredLock::default();
        assert!(!lock.settings().enabled);
        assert_eq!(lock.check("", 0), UnlockResult::Unlocked);
    }
}
