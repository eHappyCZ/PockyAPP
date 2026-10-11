use matrix_sdk::{
    media::{MediaFormat, MediaRequestParameters, MediaThumbnailSettings},
    ruma::events::room::MediaSource,
    Client,
};

use crate::error::{CoreError, Result};

pub async fn load_media(client: &Client, source: &str, width: u32, height: u32) -> Result<Vec<u8>> {
    let source = parse_source(source)?;
    let format = match &source {
        MediaSource::Plain(_) if width > 0 && height > 0 => {
            MediaFormat::Thumbnail(MediaThumbnailSettings::new(width.into(), height.into()))
        }
        _ => MediaFormat::File,
    };

    client
        .media()
        .get_media_content(&MediaRequestParameters { source, format }, true)
        .await
        .map_err(|e| CoreError::Media(e.to_string()))
}

pub(crate) fn parse_source(source: &str) -> Result<MediaSource> {
    if source.starts_with("mxc://") {
        return Ok(MediaSource::Plain(source.into()));
    }

    serde_json::from_str(source).map_err(|e| CoreError::Media(format!("invalid source: {e}")))
}

pub(crate) fn source_string(source: &MediaSource) -> String {
    match source {
        MediaSource::Plain(uri) => uri.to_string(),
        encrypted => serde_json::to_string(encrypted).expect("media source is serializable"),
    }
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn plain_source_roundtrip() {
        let source = parse_source("mxc://example.org/abc").unwrap();

        assert!(matches!(source, MediaSource::Plain(_)));
        assert_eq!(source_string(&source), "mxc://example.org/abc");
    }

    #[test]
    fn encrypted_source_roundtrip() {
        let json = json!({
            "file": {
                "url": "mxc://example.org/enc",
                "key": {
                    "kty": "oct",
                    "key_ops": ["encrypt", "decrypt"],
                    "alg": "A256CTR",
                    "k": "qcHVMSgYg-71CauWBezXI5qkaRb0LuIy-Wx5kIaHMIA",
                    "ext": true
                },
                "iv": "X85+XgHN+HEAAAAAAAAAAA",
                "hashes": { "sha256": "5qG4fFnbbVdlAW/R8B3H0vTm+xKMOv1V/5fCtQ1wMy8" },
                "v": "v2"
            }
        })
        .to_string();

        let source = parse_source(&json).unwrap();
        assert!(matches!(source, MediaSource::Encrypted(_)));

        let again = parse_source(&source_string(&source)).unwrap();
        assert!(matches!(again, MediaSource::Encrypted(_)));
    }

    #[test]
    fn rejects_garbage() {
        assert!(matches!(parse_source("nope"), Err(CoreError::Media(_))));
    }
}
