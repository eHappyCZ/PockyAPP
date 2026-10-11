use pocky_core::{ChatStyle, LocationPrecision, NotificationPreview, Preferences};

#[derive(Debug, Clone, uniffi::Record)]
pub struct DevicePreferences {
    pub onboarding_done: bool,
    pub chat_style: ChatLook,
    pub notification_preview: NotificationText,
    pub location_precision: LocationArea,
    pub share_presence: bool,
    pub share_typing: bool,
    pub send_read_receipts: bool,
    pub block_screenshots: bool,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum ChatLook {
    Classic,
    Aura,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum NotificationText {
    Always,
    WhenUnlocked,
    Never,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum LocationArea {
    Exact,
    Neighborhood,
    City,
}

impl From<Preferences> for DevicePreferences {
    fn from(prefs: Preferences) -> Self {
        Self {
            onboarding_done: prefs.onboarding_done,
            chat_style: match prefs.chat_style {
                ChatStyle::Classic => ChatLook::Classic,
                ChatStyle::Aura => ChatLook::Aura,
            },
            notification_preview: match prefs.notification_preview {
                NotificationPreview::Always => NotificationText::Always,
                NotificationPreview::WhenUnlocked => NotificationText::WhenUnlocked,
                NotificationPreview::Never => NotificationText::Never,
            },
            location_precision: match prefs.location_precision {
                LocationPrecision::Exact => LocationArea::Exact,
                LocationPrecision::Neighborhood => LocationArea::Neighborhood,
                LocationPrecision::City => LocationArea::City,
            },
            share_presence: prefs.share_presence,
            share_typing: prefs.share_typing,
            send_read_receipts: prefs.send_read_receipts,
            block_screenshots: prefs.block_screenshots,
        }
    }
}

impl From<DevicePreferences> for Preferences {
    fn from(prefs: DevicePreferences) -> Self {
        Self {
            onboarding_done: prefs.onboarding_done,
            chat_style: match prefs.chat_style {
                ChatLook::Classic => ChatStyle::Classic,
                ChatLook::Aura => ChatStyle::Aura,
            },
            notification_preview: match prefs.notification_preview {
                NotificationText::Always => NotificationPreview::Always,
                NotificationText::WhenUnlocked => NotificationPreview::WhenUnlocked,
                NotificationText::Never => NotificationPreview::Never,
            },
            location_precision: prefs.location_precision.into(),
            share_presence: prefs.share_presence,
            share_typing: prefs.share_typing,
            send_read_receipts: prefs.send_read_receipts,
            block_screenshots: prefs.block_screenshots,
        }
    }
}

impl From<LocationArea> for LocationPrecision {
    fn from(area: LocationArea) -> Self {
        match area {
            LocationArea::Exact => Self::Exact,
            LocationArea::Neighborhood => Self::Neighborhood,
            LocationArea::City => Self::City,
        }
    }
}
