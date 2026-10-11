use matrix_sdk::Client;

use crate::{
    error::{CoreError, Result},
    picture::prepare_avatar,
};

pub const MAX_NAME_CHARS: usize = 64;

pub async fn set_display_name(client: &Client, name: &str) -> Result<String> {
    let name = clean_name(name)?;
    ensure_session(client)?;
    client.account().set_display_name(Some(&name)).await?;
    Ok(name)
}

pub async fn set_avatar(client: &Client, bytes: &[u8]) -> Result<String> {
    ensure_session(client)?;
    let prepared = prepare_avatar(bytes)?;
    let mime: mime::Mime = prepared
        .mime
        .parse()
        .map_err(|_| CoreError::Media("invalid mime type".to_owned()))?;
    let url = client
        .account()
        .upload_avatar(&mime, prepared.bytes)
        .await?;
    Ok(url.to_string())
}

pub async fn remove_avatar(client: &Client) -> Result<()> {
    ensure_session(client)?;
    client.account().set_avatar_url(None).await?;
    Ok(())
}

fn clean_name(name: &str) -> Result<String> {
    let name = name.split_whitespace().collect::<Vec<_>>().join(" ");
    if name.is_empty() {
        return Err(CoreError::InvalidName("empty".to_owned()));
    }
    if name.chars().count() > MAX_NAME_CHARS {
        return Err(CoreError::InvalidName("too long".to_owned()));
    }
    Ok(name)
}

fn ensure_session(client: &Client) -> Result<()> {
    if client.session_meta().is_none() {
        return Err(CoreError::NotLoggedIn);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::client::{build_client, Server};

    #[test]
    fn name_is_trimmed_and_collapsed() {
        assert_eq!(clean_name("  Karel   Z. ").unwrap(), "Karel Z.");
    }

    #[test]
    fn rejects_empty_and_long_names() {
        assert!(matches!(clean_name("   "), Err(CoreError::InvalidName(_))));
        assert!(matches!(
            clean_name(&"a".repeat(65)),
            Err(CoreError::InvalidName(_))
        ));
        assert!(clean_name(&"ž".repeat(64)).is_ok());
    }

    #[tokio::test]
    async fn profile_changes_need_session() {
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Url("https://matrix.example.org"), dir.path(), None)
            .await
            .unwrap();

        assert!(matches!(
            set_display_name(&client, "Karel").await,
            Err(CoreError::NotLoggedIn)
        ));
        assert!(matches!(
            remove_avatar(&client).await,
            Err(CoreError::NotLoggedIn)
        ));
    }
}
