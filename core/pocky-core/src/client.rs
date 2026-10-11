#[cfg(not(target_family = "wasm"))]
use std::path::Path;

use matrix_sdk::{
    ruma::api::client::session::get_login_types::v3::LoginType, Client, ClientBuilder,
};

#[cfg(not(target_family = "wasm"))]
pub type StoreLocation = Path;

#[cfg(target_family = "wasm")]
pub type StoreLocation = str;

use crate::error::{CoreError, Result};

pub const SERVER_NAME: &str = "pokecio.com";

#[derive(Debug, Clone, Copy)]
pub enum Server<'a> {
    Discover(&'a str),
    Url(&'a str),
}

fn builder(server: Server<'_>) -> ClientBuilder {
    let builder = match server {
        Server::Discover(name) => Client::builder().server_name_or_homeserver_url(name),
        Server::Url(url) => Client::builder().homeserver_url(url),
    };
    builder
        .with_enable_automatic_back_pagination(true)
        .with_encryption_settings(crate::encryption::settings())
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServerCheck {
    pub server: String,
    pub homeserver_url: String,
}

pub fn normalize_server(input: &str) -> String {
    let trimmed = input.trim().trim_end_matches('/');
    let without_user = trimmed
        .strip_prefix('@')
        .and_then(|rest| rest.split_once(':'))
        .map_or(trimmed, |(_, server)| server);
    if without_user.is_empty() {
        SERVER_NAME.to_owned()
    } else {
        without_user.to_lowercase()
    }
}

pub async fn check_server(input: &str) -> Result<ServerCheck> {
    let server = normalize_server(input);
    let client = builder(Server::Discover(&server)).build().await?;
    let login_types = client.matrix_auth().get_login_types().await?;
    let password = login_types
        .flows
        .iter()
        .any(|flow| matches!(flow, LoginType::Password(_)));
    if !password {
        return Err(CoreError::PasswordLoginUnsupported);
    }
    Ok(ServerCheck {
        homeserver_url: client.homeserver().to_string(),
        server,
    })
}

pub async fn build_client(
    server: Server<'_>,
    store: &StoreLocation,
    passphrase: Option<&str>,
) -> Result<Client> {
    #[cfg(not(target_family = "wasm"))]
    let builder = builder(server).sqlite_store(store, passphrase);
    #[cfg(target_family = "wasm")]
    let builder = builder(server).indexeddb_store(store, passphrase);

    Ok(builder.build().await?)
}

#[cfg(not(target_family = "wasm"))]
pub async fn delete_store(data_dir: &Path) -> Result<()> {
    match tokio::fs::remove_dir_all(data_dir).await {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(e) => Err(e.into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_server_input() {
        assert_eq!(normalize_server("  "), "pokecio.com");
        assert_eq!(normalize_server("Example.org/"), "example.org");
        assert_eq!(normalize_server("@karel:example.org"), "example.org");
        assert_eq!(
            normalize_server("https://matrix.example.org/"),
            "https://matrix.example.org"
        );
    }

    #[tokio::test]
    async fn deletes_store_directory() {
        let dir = tempfile::tempdir().unwrap();
        let store = dir.path().join("store");
        build_client(Server::Url("https://matrix.example.org"), &store, None)
            .await
            .unwrap();
        assert!(store.exists());

        delete_store(&store).await.unwrap();
        assert!(!store.exists());

        delete_store(&store).await.unwrap();
    }

    #[tokio::test]
    async fn builds_client_from_url() {
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Url("https://matrix.example.org"), dir.path(), None)
            .await
            .unwrap();

        assert_eq!(client.homeserver().as_str(), "https://matrix.example.org/");
        assert!(client.session_meta().is_none());
    }

    #[tokio::test]
    #[ignore = "needs network"]
    async fn live_check_server() {
        let pokecio = check_server("pokecio.com").await.unwrap();
        println!("pokecio.com -> {}", pokecio.homeserver_url);
        assert_eq!(pokecio.homeserver_url, "https://matrix.pokecio.com/");

        let matrix_org = check_server("matrix.org").await;
        println!("matrix.org -> {matrix_org:?}");

        let missing = check_server("example.invalid").await;
        println!("example.invalid -> {missing:?}");
        assert!(missing.is_err());
    }
}
