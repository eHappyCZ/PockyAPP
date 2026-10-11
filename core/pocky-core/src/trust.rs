use std::collections::BTreeMap;

use futures_util::{pin_mut, StreamExt};
use matrix_sdk::{
    encryption::identities::UserIdentity,
    ruma::{OwnedUserId, UserId},
    Client,
};
use matrix_sdk_crypto::IdentityState;

use crate::{
    error::{CoreError, Result},
    rooms::{known_room, member_name},
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TrustLevel {
    NoIdentity,
    Unverified,
    Verified,
    VerificationBroken,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct IdentityWarning {
    pub user_id: String,
    pub name: Option<String>,
    pub was_verified: bool,
}

pub async fn user_trust(client: &Client, user_id: &str) -> Result<TrustLevel> {
    let Some(identity) = identity(client, user_id).await? else {
        return Ok(TrustLevel::NoIdentity);
    };
    Ok(if identity.has_verification_violation() {
        TrustLevel::VerificationBroken
    } else if identity.is_verified() {
        TrustLevel::Verified
    } else {
        TrustLevel::Unverified
    })
}

pub async fn acknowledge_identity(client: &Client, user_id: &str) -> Result<()> {
    let identity = identity(client, user_id)
        .await?
        .ok_or_else(|| CoreError::Encryption("user has no identity".to_owned()))?;
    let result = if identity.has_verification_violation() {
        identity.withdraw_verification().await
    } else {
        identity.pin().await
    };
    result.map_err(|e| CoreError::Encryption(e.to_string()))
}

pub async fn watch_identity_warnings(
    client: &Client,
    room_id: &str,
    mut on_update: impl FnMut(Vec<IdentityWarning>),
) -> Result<()> {
    let room = known_room(client, room_id)?;
    let changes = room
        .subscribe_to_identity_status_changes()
        .await
        .map_err(|e| CoreError::Encryption(e.to_string()))?;
    pin_mut!(changes);

    let mut warnings: BTreeMap<OwnedUserId, bool> = BTreeMap::new();
    let mut first = true;
    while let Some(batch) = changes.next().await {
        let before = warnings.clone();
        for change in batch {
            match change.changed_to {
                IdentityState::PinViolation => {
                    warnings.insert(change.user_id, false);
                }
                IdentityState::VerificationViolation => {
                    warnings.insert(change.user_id, true);
                }
                IdentityState::Pinned | IdentityState::Verified => {
                    warnings.remove(&change.user_id);
                }
            }
        }
        if !first && warnings == before {
            continue;
        }
        first = false;

        let mut list = Vec::with_capacity(warnings.len());
        for (user_id, was_verified) in &warnings {
            list.push(IdentityWarning {
                user_id: user_id.to_string(),
                name: member_name(&room, user_id).await,
                was_verified: *was_verified,
            });
        }
        on_update(list);
    }

    Ok(())
}

pub async fn user_display_name(client: &Client, user_id: &str) -> Option<String> {
    let user_id = parse_user(user_id).ok()?;
    let profile = client
        .account()
        .fetch_user_profile_of(&user_id)
        .await
        .ok()?;
    profile
        .get("displayname")
        .and_then(|value| value.as_str())
        .map(ToOwned::to_owned)
}

pub(crate) async fn identity(client: &Client, user_id: &str) -> Result<Option<UserIdentity>> {
    let user_id = parse_user(user_id)?;
    let encryption = client.encryption();
    if let Ok(Some(identity)) = encryption.get_user_identity(&user_id).await {
        return Ok(Some(identity));
    }
    encryption
        .request_user_identity(&user_id)
        .await
        .map_err(|e| CoreError::Encryption(e.to_string()))
}

fn parse_user(user_id: &str) -> Result<OwnedUserId> {
    UserId::parse(user_id).map_err(|e| CoreError::Encryption(format!("invalid user id: {e}")))
}
