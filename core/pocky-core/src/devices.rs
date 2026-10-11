use matrix_sdk::{
    ruma::{
        api::client::uiaa::{AuthData, MatrixUserIdentifier, Password, UserIdentifier},
        OwnedDeviceId,
    },
    Client,
};

use crate::error::{CoreError, Result};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DeviceInfo {
    pub device_id: String,
    pub name: Option<String>,
    pub last_seen_ms: Option<u64>,
    pub is_current: bool,
    pub verified: bool,
}

pub async fn list_devices(client: &Client) -> Result<Vec<DeviceInfo>> {
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let current = client.device_id().map(ToOwned::to_owned);
    let response = client.devices().await?;

    let mut devices = Vec::with_capacity(response.devices.len());
    for device in response.devices {
        let verified = match client
            .encryption()
            .get_device(&user_id, &device.device_id)
            .await
        {
            Ok(Some(found)) => found.is_verified(),
            _ => false,
        };
        devices.push(DeviceInfo {
            is_current: current.as_ref() == Some(&device.device_id),
            device_id: device.device_id.to_string(),
            name: device.display_name.filter(|name| !name.trim().is_empty()),
            last_seen_ms: device.last_seen_ts.map(|ts| ts.get().into()),
            verified,
        });
    }
    Ok(sorted(devices))
}

pub async fn sign_out_device(client: &Client, device_id: &str, password: &str) -> Result<()> {
    let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    if client.device_id().map(|id| id.as_str()) == Some(device_id) {
        return Err(CoreError::Sync(
            "use logout for the current device".to_owned(),
        ));
    }
    let devices = [OwnedDeviceId::from(device_id)];

    if let Err(err) = client.delete_devices(&devices, None).await {
        let Some(info) = err.as_uiaa_response() else {
            return Err(err.into());
        };
        let mut auth = Password::new(
            UserIdentifier::Matrix(MatrixUserIdentifier::new(user_id.to_string())),
            password.to_owned(),
        );
        auth.session = info.session.clone();
        client
            .delete_devices(&devices, Some(AuthData::Password(auth)))
            .await?;
    }
    Ok(())
}

fn sorted(mut devices: Vec<DeviceInfo>) -> Vec<DeviceInfo> {
    devices.sort_by(|a, b| {
        b.is_current
            .cmp(&a.is_current)
            .then(b.last_seen_ms.cmp(&a.last_seen_ms))
    });
    devices
}

#[cfg(test)]
mod tests {
    use super::*;

    fn device(id: &str, current: bool, seen: Option<u64>) -> DeviceInfo {
        DeviceInfo {
            device_id: id.to_owned(),
            name: None,
            last_seen_ms: seen,
            is_current: current,
            verified: false,
        }
    }

    #[test]
    fn current_device_first_then_most_recent() {
        let list = sorted(vec![
            device("old", false, Some(10)),
            device("never", false, None),
            device("me", true, Some(5)),
            device("new", false, Some(99)),
        ]);

        let ids: Vec<_> = list.iter().map(|d| d.device_id.as_str()).collect();
        assert_eq!(ids, ["me", "new", "old", "never"]);
    }

    #[tokio::test]
    async fn current_device_cannot_be_signed_out_here() {
        let dir = tempfile::tempdir().unwrap();
        let session = crate::session::Session {
            homeserver_url: "https://matrix.example.org/".to_owned(),
            user_id: "@alice:example.org".to_owned(),
            device_id: "ABCDEFGH".to_owned(),
            access_token: "token".to_owned(),
            refresh_token: None,
        };
        let client = crate::auth::restore_client(&session, dir.path(), None)
            .await
            .unwrap();

        assert!(matches!(
            sign_out_device(&client, "ABCDEFGH", "secret").await,
            Err(CoreError::Sync(_))
        ));
    }
}
