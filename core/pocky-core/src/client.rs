#[cfg(not(target_family = "wasm"))]
use std::path::Path;

use matrix_sdk::{Client, ClientBuilder};

#[cfg(not(target_family = "wasm"))]
pub type StoreLocation = Path;

#[cfg(target_family = "wasm")]
pub type StoreLocation = str;

use crate::error::Result;

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
}
