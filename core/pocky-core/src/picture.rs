use std::io::Cursor;

use image::{
    codecs::{jpeg::JpegEncoder, webp::WebPDecoder},
    imageops::FilterType,
    DynamicImage, ImageDecoder, ImageFormat, ImageReader,
};

use crate::error::{CoreError, Result};

pub const MAX_SIDE: u32 = 2048;
pub const AVATAR_SIDE: u32 = 512;
const JPEG_QUALITY: u8 = 85;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PreparedImage {
    pub bytes: Vec<u8>,
    pub mime: String,
    pub width: u32,
    pub height: u32,
}

pub fn prepare_image(bytes: &[u8], full_quality: bool) -> Result<PreparedImage> {
    let reader = ImageReader::new(Cursor::new(bytes))
        .with_guessed_format()
        .map_err(|e| CoreError::Media(e.to_string()))?;
    let format = reader
        .format()
        .ok_or_else(|| CoreError::Media("unsupported image format".to_owned()))?;

    let animated_webp = format == ImageFormat::WebP
        && WebPDecoder::new(Cursor::new(bytes)).is_ok_and(|decoder| decoder.has_animation());
    if format == ImageFormat::Gif || animated_webp {
        let (width, height) = reader
            .into_dimensions()
            .map_err(|e| CoreError::Media(e.to_string()))?;
        let mime = if animated_webp {
            "image/webp"
        } else {
            "image/gif"
        };
        return Ok(PreparedImage {
            bytes: bytes.to_vec(),
            mime: mime.to_owned(),
            width,
            height,
        });
    }

    let mut picture = decode(reader)?;

    if !full_quality && picture.width().max(picture.height()) > MAX_SIDE {
        picture = picture.resize(MAX_SIDE, MAX_SIDE, FilterType::Lanczos3);
    }

    let keep_alpha = format == ImageFormat::Png && picture.color().has_alpha();
    let mut out = Vec::new();
    let mime = if keep_alpha {
        picture
            .write_to(&mut Cursor::new(&mut out), ImageFormat::Png)
            .map_err(|e| CoreError::Media(e.to_string()))?;
        "image/png"
    } else {
        JpegEncoder::new_with_quality(&mut out, JPEG_QUALITY)
            .encode_image(&picture.to_rgb8())
            .map_err(|e| CoreError::Media(e.to_string()))?;
        "image/jpeg"
    };

    Ok(PreparedImage {
        bytes: out,
        mime: mime.to_owned(),
        width: picture.width(),
        height: picture.height(),
    })
}

pub fn prepare_avatar(bytes: &[u8]) -> Result<PreparedImage> {
    let reader = ImageReader::new(Cursor::new(bytes))
        .with_guessed_format()
        .map_err(|e| CoreError::Media(e.to_string()))?;
    let picture = decode(reader)?;

    let side = picture.width().min(picture.height());
    let left = (picture.width() - side) / 2;
    let top = (picture.height() - side) / 2;
    let mut square = picture.crop_imm(left, top, side, side);
    if side > AVATAR_SIDE {
        square = square.resize_exact(AVATAR_SIDE, AVATAR_SIDE, FilterType::Lanczos3);
    }

    let mut out = Vec::new();
    JpegEncoder::new_with_quality(&mut out, JPEG_QUALITY)
        .encode_image(&square.to_rgb8())
        .map_err(|e| CoreError::Media(e.to_string()))?;

    Ok(PreparedImage {
        bytes: out,
        mime: "image/jpeg".to_owned(),
        width: square.width(),
        height: square.height(),
    })
}

fn decode(reader: ImageReader<Cursor<&[u8]>>) -> Result<DynamicImage> {
    let mut decoder = reader
        .into_decoder()
        .map_err(|e| CoreError::Media(e.to_string()))?;
    let orientation = decoder
        .orientation()
        .map_err(|e| CoreError::Media(e.to_string()))?;
    let mut picture =
        DynamicImage::from_decoder(decoder).map_err(|e| CoreError::Media(e.to_string()))?;
    picture.apply_orientation(orientation);
    Ok(picture)
}

#[cfg(test)]
mod tests {
    use super::*;
    use image::{ImageBuffer, Rgb, Rgba};

    fn jpeg(width: u32, height: u32) -> Vec<u8> {
        let picture = ImageBuffer::from_pixel(width, height, Rgb([120u8, 60, 200]));
        let mut out = Vec::new();
        JpegEncoder::new(&mut out).encode_image(&picture).unwrap();
        out
    }

    fn with_exif(jpeg: &[u8]) -> Vec<u8> {
        let mut out = jpeg[..2].to_vec();
        let payload = b"Exif\0\0GPS-secret-location";
        let len = (payload.len() + 2) as u16;
        out.extend_from_slice(&[0xFF, 0xE1]);
        out.extend_from_slice(&len.to_be_bytes());
        out.extend_from_slice(payload);
        out.extend_from_slice(&jpeg[2..]);
        out
    }

    #[test]
    fn shrinks_large_photos() {
        let prepared = prepare_image(&jpeg(4000, 3000), false).unwrap();

        assert_eq!((prepared.width, prepared.height), (2048, 1536));
        assert_eq!(prepared.mime, "image/jpeg");
    }

    #[test]
    fn full_quality_keeps_size() {
        let prepared = prepare_image(&jpeg(3000, 1000), true).unwrap();

        assert_eq!((prepared.width, prepared.height), (3000, 1000));
    }

    #[test]
    fn strips_metadata() {
        let original = with_exif(&jpeg(64, 64));
        assert!(original.windows(10).any(|w| w == b"GPS-secret"));

        let prepared = prepare_image(&original, false).unwrap();

        assert!(!prepared.bytes.windows(10).any(|w| w == b"GPS-secret"));
    }

    #[test]
    fn keeps_transparency() {
        let picture = ImageBuffer::from_pixel(10, 10, Rgba([0u8, 0, 0, 0]));
        let mut png = Vec::new();
        picture
            .write_to(&mut Cursor::new(&mut png), ImageFormat::Png)
            .unwrap();

        let prepared = prepare_image(&png, false).unwrap();

        assert_eq!(prepared.mime, "image/png");
    }

    #[test]
    fn avatar_is_square_and_small() {
        let prepared = prepare_avatar(&with_exif(&jpeg(1600, 900))).unwrap();

        assert_eq!(
            (prepared.width, prepared.height),
            (AVATAR_SIDE, AVATAR_SIDE)
        );
        assert!(!prepared.bytes.windows(10).any(|w| w == b"GPS-secret"));
    }

    #[test]
    fn small_avatar_is_not_upscaled() {
        let prepared = prepare_avatar(&jpeg(300, 200)).unwrap();

        assert_eq!((prepared.width, prepared.height), (200, 200));
    }

    #[test]
    fn rejects_garbage() {
        assert!(matches!(
            prepare_image(b"definitely not a picture", false),
            Err(CoreError::Media(_))
        ));
    }
}
