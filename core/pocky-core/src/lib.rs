#![recursion_limit = "256"]

mod app_lock;
mod auth;
mod changelog;
mod client;
mod devices;
mod encryption;
mod error;
mod live_location;
mod location;
mod media;
mod moderation;
mod people;
mod picture;
mod pins;
mod preferences;
mod presence;
mod profile;
mod push;
mod retention;
mod room_info;
mod rooms;
mod session;
mod status;
mod sync;
mod timeline;
mod trust;
mod typing;
#[cfg(not(target_family = "wasm"))]
mod update;
mod verification;

pub use app_lock::{
    app_lock_settings, disable_app_lock, enable_app_lock, pin_allowed, unlock_app,
    unlock_app_with_biometrics, update_app_lock, AppLockSettings, UnlockResult, PIN_LENGTH,
};
pub use auth::{
    avatar_url, current_session, delete_account, display_name, login, logout, restore_client,
    register, restore_session, watch_session, SessionEvent,
};
pub use changelog::{changelog, Release};
#[cfg(not(target_family = "wasm"))]
pub use client::delete_store;
pub use client::{
    build_client, check_server, normalize_server, Server, ServerCheck, StoreLocation, SERVER_NAME,
};
pub use devices::{list_devices, sign_out_device, DeviceInfo};
pub use encryption::{
    change_password, change_recovery_passphrase, enable_recovery, encryption_status, logout_risk,
    passphrase_strength, recover, reset_identity, watch_encryption, EncryptionStatus, LogoutRisk,
    PassphraseStrength, RecoveryState, MIN_PASSPHRASE_CHARS, RECOMMENDED_PASSPHRASE_CHARS,
};
pub use error::{CoreError, Result};
pub use live_location::{
    live_share_expired, own_live_shares, watch_live_locations, LiveShare, OwnLiveShare,
};
pub use location::{
    blur, distance_m, geo_uri, parse_geo_uri, send_beacon, send_location, should_send_beacon,
    start_live_location, stop_live_location, BeaconFix, GeoPoint, LiveDuration,
};
pub use matrix_sdk::Client;
pub use matrix_sdk_ui::sync_service::SyncService;
pub use matrix_sdk_ui::timeline::Timeline;
pub use media::load_media;
pub use moderation::{block_user, blocked_users, report_message, unblock_user};
pub use pins::{can_pin_messages, pin_message, pinned_messages, unpin_message, PinnedMessage};
pub use retention::{can_set_message_lifetime, message_lifetime, set_message_lifetime};
pub use people::{
    accept_invite, create_group, decline_invite, invite_info, open_direct_chat, search_people,
    InviteInfo, Person,
};
pub use picture::{prepare_avatar, prepare_image, PreparedImage, AVATAR_SIDE, MAX_SIDE};
pub use preferences::{
    load_preferences, save_preferences, ChatStyle, LocationPrecision, NotificationPreview,
    Preferences,
};
pub use presence::{apply_presence_preference, fetch_presence, Presence, UserPresence};
pub use profile::{remove_avatar, set_avatar, set_display_name, MAX_NAME_CHARS};
pub use push::{
    fetch_notification, register_pusher, unregister_pusher, PushNotification, PUSH_APP_ID,
    PUSH_GATEWAY,
};
pub use room_info::{
    invite_people, remove_member, room_details, set_room_avatar, set_room_name, set_room_topic,
    MemberInfo, MemberRole, RoomDetails,
};
pub use rooms::{
    leave_room, mark_room_read, set_favourite, set_muted, watch_rooms, Delivery, MessagePreview,
    PreviewKind, RoomSummary,
};
pub use session::Session;
pub use status::{
    clear_status, expire_status, own_status, set_status, watch_own_status, OwnStatus,
    MAX_STATUS_CHARS,
};
pub use sync::{create_sync_service, watch_sync_state, SyncState};
pub use timeline::{
    edit_text, forward_message, mark_as_read, max_upload_size, open_history, open_media,
    open_timeline, paginate_backwards, redact, send_file, send_image, send_reply, send_text,
    send_video, send_voice, toggle_reaction, watch_timeline, waveform, EntryKind, EventContent,
    EventEntry, MembershipKind, Reaction, ReadReceipt, ReplyPreview, SendState, Shield,
    ShieldReason, StateChange, TimelineDiff, TimelineEntry, VideoClip, PAGE_SIZE, WAVEFORM_BARS,
};
pub use trust::{
    acknowledge_identity, user_display_name, user_trust, watch_identity_warnings, IdentityWarning,
    TrustLevel,
};
pub use typing::{send_typing, watch_typing, TypingUser};
#[cfg(not(target_family = "wasm"))]
pub use update::{
    check_update, download_update, parse_update, verify_file, UpdateInfo, UPDATE_MANIFEST,
};
pub use verification::{
    watch_verification_requests, SasEmoji, VerificationFlow, VerificationPhase,
};

pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_matches_package() {
        assert_eq!(version(), "0.10.0");
    }
}
