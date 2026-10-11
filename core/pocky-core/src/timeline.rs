use std::{sync::Arc, time::Duration};

use eyeball_im::VectorDiff;
use futures_util::{pin_mut, StreamExt};
use matrix_sdk::{
    attachment::{
        AttachmentInfo, BaseAudioInfo, BaseFileInfo, BaseImageInfo, BaseVideoInfo, Thumbnail,
    },
    room::edit::EditedContent,
    ruma::{
        api::client::receipt::create_receipt::v3::ReceiptType,
        events::{
            room::message::{
                MessageType, RoomMessageEventContent, RoomMessageEventContentWithoutRelation,
                TextMessageEventContent,
            },
            AnySyncMessageLikeEvent, AnySyncStateEvent, AnySyncTimelineEvent, SyncMessageLikeEvent,
            TimelineEventType,
        },
        room_version_rules::RoomVersionRules,
        EventId, RoomId, UInt,
    },
    Client,
};
use matrix_sdk_ui::{
    sync_service::SyncService,
    timeline::{
        default_event_filter, AnyOtherStateEventContentChange, AttachmentConfig, AttachmentSource,
        EventSendState, EventTimelineItem, MembershipChange, MsgLikeKind, Profile, Timeline,
        TimelineBuilder, TimelineDetails, TimelineEventItemId, TimelineEventShieldState,
        TimelineEventShieldStateCode, TimelineItem, TimelineItemContent, TimelineItemKind,
        TimelineReadReceiptTracking, VirtualTimelineItem,
    },
};

use serde::Serialize;

use crate::{
    error::{CoreError, Result},
    media::source_string,
    preferences::load_preferences,
    rooms::{known_room, PreviewKind},
};

pub const PAGE_SIZE: u16 = 30;
const TXN_PREFIX: &str = "txn:";
pub const WAVEFORM_BARS: usize = 100;

#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct TimelineEntry {
    pub id: String,
    pub kind: EntryKind,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum EntryKind {
    DateDivider { timestamp_ms: u64 },
    ReadMarker,
    TimelineStart,
    Event(Box<EventEntry>),
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EventEntry {
    pub item_key: String,
    pub event_id: Option<String>,
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub sender_avatar: Option<String>,
    pub timestamp_ms: u64,
    pub is_own: bool,
    pub is_edited: bool,
    pub content: EventContent,
    pub reactions: Vec<Reaction>,
    pub in_reply_to: Option<ReplyPreview>,
    pub send_state: SendState,
    pub shield: Option<Shield>,
    pub read_by: Vec<ReadReceipt>,
    pub can_reply: bool,
    pub can_edit: bool,
    pub can_redact: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(
    tag = "kind",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum EventContent {
    Text {
        body: String,
        html: Option<String>,
    },
    Notice {
        body: String,
        html: Option<String>,
    },
    Emote {
        body: String,
        html: Option<String>,
    },
    Image {
        caption: Option<String>,
        source: String,
        original: String,
        width: Option<u32>,
        height: Option<u32>,
        name: String,
        size: Option<u64>,
        mime: Option<String>,
    },
    Video {
        caption: Option<String>,
        source: String,
        thumbnail: Option<String>,
        width: Option<u32>,
        height: Option<u32>,
        duration_ms: Option<u64>,
        size: Option<u64>,
        name: String,
        mime: Option<String>,
    },
    Audio {
        caption: String,
        source: String,
        duration_ms: Option<u64>,
        waveform: Vec<u16>,
        voice: bool,
    },
    File {
        name: String,
        source: String,
        size: Option<u64>,
        mime: Option<String>,
    },
    Location {
        geo_uri: String,
        description: Option<String>,
    },
    LiveLocation {
        is_live: bool,
        geo_uri: Option<String>,
    },
    Sticker {
        body: String,
        source: String,
        width: Option<u32>,
        height: Option<u32>,
    },
    Poll {
        question: String,
    },
    Redacted,
    UnableToDecrypt,
    State(StateChange),
    Other,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum StateChange {
    Membership {
        user_id: String,
        name: Option<String>,
        change: MembershipKind,
    },
    DisplayName {
        user_id: String,
        old: Option<String>,
        new: Option<String>,
    },
    Avatar {
        user_id: String,
        name: Option<String>,
    },
    RoomName {
        name: Option<String>,
    },
    RoomTopic {
        topic: Option<String>,
    },
    RoomAvatar,
    RoomCreated,
    EncryptionEnabled,
    Other,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum MembershipKind {
    Joined,
    Left,
    Invited,
    InvitationAccepted,
    InvitationRejected,
    InvitationRevoked,
    Kicked,
    Banned,
    Unbanned,
    Knocked,
    Other,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Reaction {
    pub key: String,
    pub count: u32,
    pub reacted_by_me: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReplyPreview {
    pub event_id: String,
    pub sender_id: Option<String>,
    pub sender_name: Option<String>,
    pub content: Option<PreviewKind>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReadReceipt {
    pub user_id: String,
    pub timestamp_ms: Option<u64>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Shield {
    pub danger: bool,
    pub reason: ShieldReason,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum ShieldReason {
    AuthenticityNotGuaranteed,
    UnknownDevice,
    UnverifiedDevice,
    UnverifiedSender,
    IdentityChanged,
    MismatchedSender,
    SentInClear,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum SendState {
    Sending,
    Sent,
    Failed,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(tag = "op", content = "value", rename_all = "camelCase")]
pub enum TimelineDiff {
    Reset(Vec<TimelineEntry>),
    Append(Vec<TimelineEntry>),
    Clear,
    PushFront(TimelineEntry),
    PushBack(TimelineEntry),
    PopFront,
    PopBack,
    Insert { index: u32, entry: TimelineEntry },
    Set { index: u32, entry: TimelineEntry },
    Remove { index: u32 },
    Truncate { length: u32 },
}

pub async fn open_timeline(
    client: &Client,
    sync: Option<&SyncService>,
    room_id: &str,
) -> Result<Timeline> {
    let room_id =
        RoomId::parse(room_id).map_err(|e| CoreError::Timeline(format!("invalid room id: {e}")))?;
    let room = client
        .get_room(&room_id)
        .ok_or_else(|| CoreError::Timeline("unknown room".to_owned()))?;

    if let Some(sync) = sync {
        sync.room_list_service()
            .set_room_subscriptions(&[&room_id])
            .await;
    }

    if room.encryption_state().is_encrypted() {
        let client = client.clone();
        let room_id = room_id.clone();
        matrix_sdk::executor::spawn(async move {
            crate::encryption::download_room_keys(&client, &room_id).await;
        });
    }

    TimelineBuilder::new(&room)
        .track_read_marker_and_receipts(TimelineReadReceiptTracking::MessageLikeEvents)
        .event_filter(|event, rules| show_event(event, rules) && in_chat(event))
        .build()
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn open_media(client: &Client, room_id: &str) -> Result<Timeline> {
    let room = known_room(client, room_id)?;
    TimelineBuilder::new(&room)
        .event_filter(|event, rules| is_media(event) && show_event(event, rules))
        .build()
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn open_history(client: &Client, room_id: &str) -> Result<Timeline> {
    let room = known_room(client, room_id)?;
    TimelineBuilder::new(&room)
        .event_filter(|event, rules| {
            matches!(event, AnySyncTimelineEvent::State(_)) && show_event(event, rules)
        })
        .build()
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn watch_timeline(timeline: &Timeline, mut on_diffs: impl FnMut(Vec<TimelineDiff>)) {
    let own_user_id = timeline.room().own_user_id().to_owned();
    let (items, stream) = timeline.subscribe().await;

    on_diffs(vec![TimelineDiff::Reset(
        items.iter().map(|item| entry(item, &own_user_id)).collect(),
    )]);

    pin_mut!(stream);
    while let Some(diffs) = stream.next().await {
        on_diffs(
            diffs
                .into_iter()
                .map(|diff| map_diff(diff, &own_user_id))
                .collect(),
        );
    }
}

pub async fn paginate_backwards(timeline: &Timeline) -> Result<bool> {
    timeline
        .paginate_backwards(PAGE_SIZE)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn send_text(timeline: &Timeline, text: &str) -> Result<()> {
    let text = text.trim();
    if text.is_empty() {
        return Ok(());
    }

    timeline
        .send(RoomMessageEventContent::text_markdown(text).into())
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))?;
    Ok(())
}

pub async fn send_reply(timeline: &Timeline, text: &str, in_reply_to: &str) -> Result<()> {
    let text = text.trim();
    if text.is_empty() {
        return Ok(());
    }
    let event_id = EventId::parse(in_reply_to)
        .map_err(|e| CoreError::Timeline(format!("invalid event id: {e}")))?;

    timeline
        .send_reply(
            RoomMessageEventContentWithoutRelation::text_markdown(text),
            event_id,
        )
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))?;
    Ok(())
}

pub async fn edit_text(timeline: &Timeline, item_key: &str, text: &str) -> Result<()> {
    let text = text.trim();
    if text.is_empty() {
        return Err(CoreError::Timeline("empty edit".to_owned()));
    }
    let content = RoomMessageEventContentWithoutRelation::text_markdown(text);

    timeline
        .edit(
            &parse_item_key(item_key)?,
            EditedContent::RoomMessage(content),
        )
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn toggle_reaction(timeline: &Timeline, item_key: &str, key: &str) -> Result<bool> {
    timeline
        .toggle_reaction(&parse_item_key(item_key)?, key)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn redact(timeline: &Timeline, item_key: &str) -> Result<()> {
    timeline
        .redact(&parse_item_key(item_key)?, None)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn send_image(
    timeline: &Timeline,
    bytes: &[u8],
    caption: Option<&str>,
    full_quality: bool,
) -> Result<()> {
    let prepared = crate::picture::prepare_image(bytes, full_quality)?;
    let mime: mime::Mime = prepared
        .mime
        .parse()
        .map_err(|_| CoreError::Media("invalid mime type".to_owned()))?;
    let extension = if prepared.mime == "image/png" {
        "png"
    } else if prepared.mime == "image/gif" {
        "gif"
    } else if prepared.mime == "image/webp" {
        "webp"
    } else {
        "jpg"
    };

    let info = BaseImageInfo {
        width: Some(prepared.width.into()),
        height: Some(prepared.height.into()),
        size: UInt::new(prepared.bytes.len() as u64),
        ..Default::default()
    };
    let config = AttachmentConfig {
        info: Some(AttachmentInfo::Image(info)),
        caption: caption
            .map(str::trim)
            .filter(|c| !c.is_empty())
            .map(TextMessageEventContent::markdown),
        ..Default::default()
    };

    let source = AttachmentSource::Data {
        bytes: prepared.bytes,
        filename: format!("image.{extension}"),
    };
    timeline
        .send_attachment(source, mime, config)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn send_voice(
    timeline: &Timeline,
    bytes: Vec<u8>,
    mime: &str,
    duration_ms: u64,
    levels: &[f32],
) -> Result<()> {
    let mime: mime::Mime = mime
        .parse()
        .map_err(|_| CoreError::Media("invalid mime type".to_owned()))?;
    let extension = if mime.subtype() == "ogg" {
        "ogg"
    } else {
        "m4a"
    };
    if bytes.is_empty() {
        return Err(CoreError::Media("empty recording".to_owned()));
    }
    let info = BaseAudioInfo {
        duration: Some(Duration::from_millis(duration_ms)),
        size: UInt::new(bytes.len() as u64),
        waveform: Some(waveform(levels, WAVEFORM_BARS)),
    };
    let config = AttachmentConfig {
        info: Some(AttachmentInfo::Voice(info)),
        ..Default::default()
    };
    let source = AttachmentSource::Data {
        bytes,
        filename: format!("voice.{extension}"),
    };
    timeline
        .send_attachment(source, mime, config)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn send_file(
    timeline: &Timeline,
    bytes: Vec<u8>,
    filename: &str,
    mime: &str,
) -> Result<()> {
    check_size(timeline, bytes.len()).await?;
    let mime: mime::Mime = mime.parse().unwrap_or(mime::APPLICATION_OCTET_STREAM);
    let config = AttachmentConfig {
        info: Some(AttachmentInfo::File(BaseFileInfo {
            size: UInt::new(bytes.len() as u64),
        })),
        ..Default::default()
    };
    let source = AttachmentSource::Data {
        bytes,
        filename: clean_filename(filename),
    };
    timeline
        .send_attachment(source, mime, config)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VideoClip {
    pub bytes: Vec<u8>,
    pub mime: String,
    pub width: u32,
    pub height: u32,
    pub duration_ms: u64,
    pub thumbnail: Option<Vec<u8>>,
}

pub async fn send_video(timeline: &Timeline, clip: VideoClip, caption: Option<&str>) -> Result<()> {
    check_size(timeline, clip.bytes.len()).await?;
    let mime: mime::Mime = clip
        .mime
        .parse()
        .map_err(|_| CoreError::Media("invalid mime type".to_owned()))?;

    let thumbnail = match clip.thumbnail {
        Some(data) => {
            let prepared = crate::picture::prepare_image(&data, false)?;
            Some(Thumbnail {
                size: UInt::new(prepared.bytes.len() as u64).unwrap_or_default(),
                width: UInt::from(prepared.width),
                height: UInt::from(prepared.height),
                content_type: mime::IMAGE_JPEG,
                data: prepared.bytes,
            })
        }
        None => None,
    };
    let info = BaseVideoInfo {
        duration: Some(Duration::from_millis(clip.duration_ms)),
        width: Some(UInt::from(clip.width)),
        height: Some(UInt::from(clip.height)),
        size: UInt::new(clip.bytes.len() as u64),
        blurhash: None,
    };
    let config = AttachmentConfig {
        info: Some(AttachmentInfo::Video(info)),
        thumbnail,
        caption: caption
            .map(str::trim)
            .filter(|c| !c.is_empty())
            .map(TextMessageEventContent::markdown),
        ..Default::default()
    };
    let extension = mime.subtype().as_str().to_owned();
    let source = AttachmentSource::Data {
        bytes: clip.bytes,
        filename: format!("video.{extension}"),
    };
    timeline
        .send_attachment(source, mime, config)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))
}

pub async fn forward_message(
    client: &Client,
    from_room: &str,
    event_id: &str,
    to_room: &str,
) -> Result<()> {
    let source = known_room(client, from_room)?;
    let target = known_room(client, to_room)?;
    let event_id = EventId::parse(event_id)
        .map_err(|e| CoreError::Timeline(format!("invalid event id: {e}")))?;

    let event = source
        .load_or_fetch_event(&event_id, None)
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))?;
    let event = event
        .raw()
        .deserialize()
        .map_err(|e| CoreError::Timeline(e.to_string()))?;
    let AnySyncTimelineEvent::MessageLike(AnySyncMessageLikeEvent::RoomMessage(
        SyncMessageLikeEvent::Original(message),
    )) = event
    else {
        return Err(CoreError::Timeline("event cannot be forwarded".to_owned()));
    };

    target
        .send(RoomMessageEventContent::new(message.content.msgtype))
        .await
        .map_err(|e| CoreError::Timeline(e.to_string()))?;
    Ok(())
}

pub async fn max_upload_size(client: &Client) -> Result<u64> {
    Ok(client.load_or_fetch_max_upload_size().await?.into())
}

async fn check_size(timeline: &Timeline, size: usize) -> Result<()> {
    let client = timeline.room().client();
    if let Ok(limit) = client.load_or_fetch_max_upload_size().await {
        if size as u64 > u64::from(limit) {
            return Err(CoreError::FileTooLarge(limit.into()));
        }
    }
    Ok(())
}

fn clean_filename(name: &str) -> String {
    let name = name.rsplit(['/', '\\']).next().unwrap_or(name).trim();
    if name.is_empty() {
        "soubor".to_owned()
    } else {
        name.to_owned()
    }
}

pub fn waveform(levels: &[f32], bars: usize) -> Vec<f32> {
    if levels.is_empty() || bars == 0 {
        return Vec::new();
    }
    let count = bars.min(levels.len());
    let chunk = levels.len() as f32 / count as f32;
    let raw: Vec<f32> = (0..count)
        .map(|i| {
            let start = (i as f32 * chunk) as usize;
            let end = (((i + 1) as f32 * chunk) as usize)
                .max(start + 1)
                .min(levels.len());
            levels[start..end].iter().copied().fold(0.0, f32::max)
        })
        .collect();
    let peak = raw.iter().copied().fold(0.0, f32::max);
    if peak <= f32::EPSILON {
        return vec![0.0; count];
    }
    raw.iter()
        .map(|value| (value / peak).clamp(0.0, 1.0))
        .collect()
}

pub fn item_key(id: &TimelineEventItemId) -> String {
    match id {
        TimelineEventItemId::EventId(event_id) => event_id.to_string(),
        TimelineEventItemId::TransactionId(txn) => format!("{TXN_PREFIX}{txn}"),
    }
}

fn parse_item_key(key: &str) -> Result<TimelineEventItemId> {
    if let Some(txn) = key.strip_prefix(TXN_PREFIX) {
        return Ok(TimelineEventItemId::TransactionId(txn.into()));
    }
    EventId::parse(key)
        .map(TimelineEventItemId::EventId)
        .map_err(|e| CoreError::Timeline(format!("invalid item key: {e}")))
}

pub async fn mark_as_read(timeline: &Timeline) -> Result<()> {
    let public = load_preferences(&timeline.room().client())
        .await?
        .send_read_receipts;
    timeline
        .mark_as_read(receipt_type(public))
        .await
        .map_err(CoreError::from)?;
    Ok(())
}

fn receipt_type(public: bool) -> ReceiptType {
    if public {
        ReceiptType::Read
    } else {
        ReceiptType::ReadPrivate
    }
}

fn is_media(event: &AnySyncTimelineEvent) -> bool {
    match event {
        AnySyncTimelineEvent::MessageLike(AnySyncMessageLikeEvent::RoomEncrypted(_)) => true,
        AnySyncTimelineEvent::MessageLike(AnySyncMessageLikeEvent::RoomMessage(
            SyncMessageLikeEvent::Original(message),
        )) => matches!(
            message.content.msgtype,
            MessageType::Image(_)
                | MessageType::Video(_)
                | MessageType::Audio(_)
                | MessageType::File(_)
        ),
        _ => false,
    }
}

fn is_verification(event: &AnySyncTimelineEvent) -> bool {
    match event {
        AnySyncTimelineEvent::MessageLike(AnySyncMessageLikeEvent::RoomMessage(
            SyncMessageLikeEvent::Original(message),
        )) => matches!(message.content.msgtype, MessageType::VerificationRequest(_)),
        _ => matches!(
            event.event_type(),
            TimelineEventType::KeyVerificationReady
                | TimelineEventType::KeyVerificationStart
                | TimelineEventType::KeyVerificationCancel
                | TimelineEventType::KeyVerificationAccept
                | TimelineEventType::KeyVerificationKey
                | TimelineEventType::KeyVerificationMac
                | TimelineEventType::KeyVerificationDone
        ),
    }
}

fn in_chat(event: &AnySyncTimelineEvent) -> bool {
    let AnySyncTimelineEvent::State(state) = event else {
        return true;
    };
    matches!(
        state,
        AnySyncStateEvent::RoomEncryption(_)
            | AnySyncStateEvent::RoomAvatar(_)
            | AnySyncStateEvent::RoomName(_)
            | AnySyncStateEvent::BeaconInfo(_)
    )
}

fn show_event(event: &AnySyncTimelineEvent, rules: &RoomVersionRules) -> bool {
    if !default_event_filter(event, rules) || is_verification(event) {
        return false;
    }

    !matches!(
        event.event_type(),
        TimelineEventType::RoomPowerLevels
            | TimelineEventType::RoomJoinRules
            | TimelineEventType::RoomHistoryVisibility
            | TimelineEventType::RoomGuestAccess
            | TimelineEventType::RoomCanonicalAlias
            | TimelineEventType::RoomServerAcl
            | TimelineEventType::RoomPinnedEvents
            | TimelineEventType::RoomThirdPartyInvite
            | TimelineEventType::PolicyRuleRoom
            | TimelineEventType::PolicyRuleServer
            | TimelineEventType::PolicyRuleUser
            | TimelineEventType::SpaceChild
            | TimelineEventType::SpaceParent
    )
}

fn map_diff(
    diff: VectorDiff<Arc<TimelineItem>>,
    own_user_id: &matrix_sdk::ruma::UserId,
) -> TimelineDiff {
    let e = |item: &Arc<TimelineItem>| entry(item, own_user_id);

    match diff {
        VectorDiff::Append { values } => TimelineDiff::Append(values.iter().map(e).collect()),
        VectorDiff::Clear => TimelineDiff::Clear,
        VectorDiff::PushFront { value } => TimelineDiff::PushFront(e(&value)),
        VectorDiff::PushBack { value } => TimelineDiff::PushBack(e(&value)),
        VectorDiff::PopFront => TimelineDiff::PopFront,
        VectorDiff::PopBack => TimelineDiff::PopBack,
        VectorDiff::Insert { index, value } => TimelineDiff::Insert {
            index: index as u32,
            entry: e(&value),
        },
        VectorDiff::Set { index, value } => TimelineDiff::Set {
            index: index as u32,
            entry: e(&value),
        },
        VectorDiff::Remove { index } => TimelineDiff::Remove {
            index: index as u32,
        },
        VectorDiff::Truncate { length } => TimelineDiff::Truncate {
            length: length as u32,
        },
        VectorDiff::Reset { values } => TimelineDiff::Reset(values.iter().map(e).collect()),
    }
}

fn entry(item: &TimelineItem, own_user_id: &matrix_sdk::ruma::UserId) -> TimelineEntry {
    let kind = match item.kind() {
        TimelineItemKind::Event(event) => {
            EntryKind::Event(Box::new(event_entry(event, own_user_id)))
        }
        TimelineItemKind::Virtual(VirtualTimelineItem::DateDivider(ts)) => EntryKind::DateDivider {
            timestamp_ms: ts.get().into(),
        },
        TimelineItemKind::Virtual(VirtualTimelineItem::ReadMarker) => EntryKind::ReadMarker,
        TimelineItemKind::Virtual(VirtualTimelineItem::TimelineStart) => EntryKind::TimelineStart,
    };

    TimelineEntry {
        id: item.unique_id().0.clone(),
        kind,
    }
}

fn event_entry(event: &EventTimelineItem, own_user_id: &matrix_sdk::ruma::UserId) -> EventEntry {
    let (sender_name, sender_avatar) = profile(event.sender_profile());
    let content = event.content();

    let (reactions, in_reply_to, is_edited) = match content {
        TimelineItemContent::MsgLike(msg) => (
            msg.reactions
                .iter()
                .map(|(key, senders)| Reaction {
                    key: key.clone(),
                    count: senders.len() as u32,
                    reacted_by_me: senders.contains_key(own_user_id),
                })
                .collect(),
            msg.in_reply_to.as_ref().map(|reply| {
                let (sender_id, sender_name, content) = match &reply.event {
                    TimelineDetails::Ready(embedded) => (
                        Some(embedded.sender.to_string()),
                        profile(&embedded.sender_profile).0,
                        Some(preview_of(&embedded.content)),
                    ),
                    _ => (None, None, None),
                };
                ReplyPreview {
                    event_id: reply.event_id.to_string(),
                    sender_id,
                    sender_name,
                    content,
                }
            }),
            msg.as_message().is_some_and(|m| m.is_edited()),
        ),
        _ => (Vec::new(), None, false),
    };

    let redacted = matches!(
        content,
        TimelineItemContent::MsgLike(msg) if matches!(msg.kind, MsgLikeKind::Redacted)
    );

    EventEntry {
        item_key: item_key(&event.identifier()),
        event_id: event.event_id().map(ToString::to_string),
        sender_id: event.sender().to_string(),
        sender_name,
        sender_avatar,
        timestamp_ms: event.timestamp().get().into(),
        is_own: event.is_own(),
        is_edited,
        content: event_content(content),
        reactions,
        in_reply_to,
        send_state: match event.send_state() {
            None | Some(EventSendState::Sent { .. }) => SendState::Sent,
            Some(EventSendState::NotSentYet { .. }) => SendState::Sending,
            Some(EventSendState::SendingFailed { .. }) => SendState::Failed,
        },
        shield: shield(event.get_shield(false)),
        read_by: event
            .read_receipts()
            .iter()
            .filter(|(user, _)| user.as_str() != own_user_id.as_str())
            .map(|(user, receipt)| ReadReceipt {
                user_id: user.to_string(),
                timestamp_ms: receipt.ts.map(|ts| ts.get().into()),
            })
            .collect(),
        can_reply: event.can_be_replied_to(),
        can_edit: event.is_editable(),
        can_redact: event.is_own() && !redacted,
    }
}

fn shield(state: TimelineEventShieldState) -> Option<Shield> {
    let (danger, code) = match state {
        TimelineEventShieldState::None => return None,
        TimelineEventShieldState::Red { code } => (true, code),
        TimelineEventShieldState::Grey { code } => (false, code),
    };
    let reason = match code {
        TimelineEventShieldStateCode::AuthenticityNotGuaranteed => {
            ShieldReason::AuthenticityNotGuaranteed
        }
        TimelineEventShieldStateCode::UnknownDevice => ShieldReason::UnknownDevice,
        TimelineEventShieldStateCode::UnsignedDevice => ShieldReason::UnverifiedDevice,
        TimelineEventShieldStateCode::UnverifiedIdentity => ShieldReason::UnverifiedSender,
        TimelineEventShieldStateCode::VerificationViolation => ShieldReason::IdentityChanged,
        TimelineEventShieldStateCode::MismatchedSender => ShieldReason::MismatchedSender,
        TimelineEventShieldStateCode::SentInClear => ShieldReason::SentInClear,
    };
    Some(Shield { danger, reason })
}

fn profile(details: &TimelineDetails<Profile>) -> (Option<String>, Option<String>) {
    match details {
        TimelineDetails::Ready(profile) => (
            profile.display_name.clone(),
            profile.avatar_url.as_ref().map(ToString::to_string),
        ),
        _ => (None, None),
    }
}

pub(crate) fn event_content(content: &TimelineItemContent) -> EventContent {
    match content {
        TimelineItemContent::MsgLike(msg) => match &msg.kind {
            MsgLikeKind::Message(message) => message_content(message.msgtype()),
            MsgLikeKind::Sticker(sticker) => {
                let content = sticker.content();
                EventContent::Sticker {
                    body: content.body.clone(),
                    source: source_string(&content.source.clone().into()),
                    width: content.info.width.map(uint),
                    height: content.info.height.map(uint),
                }
            }
            MsgLikeKind::Poll(poll) => EventContent::Poll {
                question: poll.results().question,
            },
            MsgLikeKind::Redacted => EventContent::Redacted,
            MsgLikeKind::UnableToDecrypt(_) => EventContent::UnableToDecrypt,
            MsgLikeKind::LiveLocation(state) => EventContent::LiveLocation {
                is_live: state.is_live(),
                geo_uri: state.latest_location().map(|l| l.geo_uri().to_owned()),
            },
            MsgLikeKind::Other(_) => EventContent::Other,
        },
        TimelineItemContent::MembershipChange(change) => {
            EventContent::State(StateChange::Membership {
                user_id: change.user_id().to_string(),
                name: change.display_name(),
                change: membership_kind(change.change()),
            })
        }
        TimelineItemContent::ProfileChange(change) => {
            let user_id = change.user_id().to_string();
            if let Some(names) = change.displayname_change() {
                EventContent::State(StateChange::DisplayName {
                    user_id,
                    old: names.old.clone(),
                    new: names.new.clone(),
                })
            } else {
                EventContent::State(StateChange::Avatar {
                    user_id,
                    name: None,
                })
            }
        }
        TimelineItemContent::OtherState(state) => EventContent::State(other_state(state.content())),
        _ => EventContent::Other,
    }
}

fn message_content(msgtype: &MessageType) -> EventContent {
    match msgtype {
        MessageType::Text(text) => EventContent::Text {
            body: text.body.clone(),
            html: html(text.formatted.as_ref()),
        },
        MessageType::Notice(notice) => EventContent::Notice {
            body: notice.body.clone(),
            html: html(notice.formatted.as_ref()),
        },
        MessageType::Emote(emote) => EventContent::Emote {
            body: emote.body.clone(),
            html: html(emote.formatted.as_ref()),
        },
        MessageType::Image(image) => {
            let info = image.info.as_deref();
            let source = info
                .and_then(|i| i.thumbnail_source.as_ref())
                .unwrap_or(&image.source);
            EventContent::Image {
                caption: image.caption().map(ToOwned::to_owned),
                source: source_string(source),
                original: source_string(&image.source),
                width: info.and_then(|i| i.width).map(uint),
                height: info.and_then(|i| i.height).map(uint),
                name: image.filename().to_owned(),
                size: info.and_then(|i| i.size).map(u64::from),
                mime: info.and_then(|i| i.mimetype.clone()),
            }
        }
        MessageType::Video(video) => {
            let info = video.info.as_deref();
            EventContent::Video {
                caption: video.caption().map(ToOwned::to_owned),
                source: source_string(&video.source),
                thumbnail: info
                    .and_then(|i| i.thumbnail_source.as_ref())
                    .map(source_string),
                width: info.and_then(|i| i.width).map(uint),
                height: info.and_then(|i| i.height).map(uint),
                duration_ms: info.and_then(|i| i.duration).map(|d| d.as_millis() as u64),
                size: info.and_then(|i| i.size).map(u64::from),
                name: video.filename().to_owned(),
                mime: info.and_then(|i| i.mimetype.clone()),
            }
        }
        MessageType::Audio(audio) => EventContent::Audio {
            caption: audio.body.clone(),
            source: source_string(&audio.source),
            duration_ms: audio
                .audio
                .as_ref()
                .map(|details| details.duration)
                .or_else(|| audio.info.as_ref().and_then(|info| info.duration))
                .map(|duration| duration.as_millis() as u64),
            waveform: audio
                .audio
                .as_ref()
                .map(|details| {
                    details
                        .waveform
                        .iter()
                        .map(|amp| u64::from(amp.get()) as u16)
                        .collect()
                })
                .unwrap_or_default(),
            voice: audio.voice.is_some(),
        },
        MessageType::File(file) => {
            let info = file.info.as_deref();
            EventContent::File {
                name: file.filename().to_owned(),
                source: source_string(&file.source),
                size: info.and_then(|i| i.size).map(u64::from),
                mime: info.and_then(|i| i.mimetype.clone()),
            }
        }
        MessageType::Location(location) => EventContent::Location {
            geo_uri: location.geo_uri.clone(),
            description: Some(location.body.trim().to_owned())
                .filter(|b| !b.is_empty() && !b.contains("geo:")),
        },
        other => EventContent::Text {
            body: other.body().to_owned(),
            html: None,
        },
    }
}

fn uint(value: matrix_sdk::ruma::UInt) -> u32 {
    u32::try_from(u64::from(value)).unwrap_or(u32::MAX)
}

fn html(
    formatted: Option<&matrix_sdk::ruma::events::room::message::FormattedBody>,
) -> Option<String> {
    use matrix_sdk::ruma::events::room::message::MessageFormat;

    formatted
        .filter(|f| f.format == MessageFormat::Html)
        .map(|f| f.body.clone())
}

fn membership_kind(change: Option<MembershipChange>) -> MembershipKind {
    match change {
        Some(MembershipChange::Joined) => MembershipKind::Joined,
        Some(MembershipChange::Left) => MembershipKind::Left,
        Some(MembershipChange::Invited) => MembershipKind::Invited,
        Some(MembershipChange::InvitationAccepted) => MembershipKind::InvitationAccepted,
        Some(MembershipChange::InvitationRejected) => MembershipKind::InvitationRejected,
        Some(MembershipChange::InvitationRevoked) => MembershipKind::InvitationRevoked,
        Some(MembershipChange::Kicked) | Some(MembershipChange::KickedAndBanned) => {
            MembershipKind::Kicked
        }
        Some(MembershipChange::Banned) => MembershipKind::Banned,
        Some(MembershipChange::Unbanned) => MembershipKind::Unbanned,
        Some(MembershipChange::Knocked) => MembershipKind::Knocked,
        _ => MembershipKind::Other,
    }
}

fn other_state(content: &AnyOtherStateEventContentChange) -> StateChange {
    use matrix_sdk::ruma::events::StateEventContentChange as C;

    match content {
        AnyOtherStateEventContentChange::RoomName(C::Original { content, .. }) => {
            StateChange::RoomName {
                name: Some(content.name.clone()).filter(|n| !n.is_empty()),
            }
        }
        AnyOtherStateEventContentChange::RoomTopic(C::Original { content, .. }) => {
            StateChange::RoomTopic {
                topic: Some(content.topic.clone()).filter(|t| !t.is_empty()),
            }
        }
        AnyOtherStateEventContentChange::RoomAvatar(_) => StateChange::RoomAvatar,
        AnyOtherStateEventContentChange::RoomCreate(_) => StateChange::RoomCreated,
        AnyOtherStateEventContentChange::RoomEncryption(_) => StateChange::EncryptionEnabled,
        _ => StateChange::Other,
    }
}

fn preview_of(content: &TimelineItemContent) -> PreviewKind {
    match event_content(content) {
        EventContent::Text { body, .. } | EventContent::Notice { body, .. } => {
            PreviewKind::Text(crate::rooms::shorten(&body))
        }
        EventContent::Emote { body, .. } => PreviewKind::Emote(crate::rooms::shorten(&body)),
        EventContent::Image { .. } => PreviewKind::Image,
        EventContent::Video { .. } => PreviewKind::Video,
        EventContent::Audio { .. } => PreviewKind::Audio,
        EventContent::File { .. } => PreviewKind::File,
        EventContent::Location { .. } | EventContent::LiveLocation { .. } => PreviewKind::Location,
        EventContent::Sticker { .. } => PreviewKind::Sticker,
        EventContent::Poll { .. } => PreviewKind::Poll,
        EventContent::UnableToDecrypt => PreviewKind::Encrypted,
        _ => PreviewKind::Other,
    }
}

#[cfg(test)]
mod tests {
    use matrix_sdk::ruma::{
        events::room::message::{
            FileMessageEventContent, ImageMessageEventContent, LocationMessageEventContent,
            TextMessageEventContent,
        },
        serde::Raw,
        OwnedMxcUri,
    };
    use serde_json::json;

    use super::*;

    fn sync_event(value: serde_json::Value) -> AnySyncTimelineEvent {
        Raw::<AnySyncTimelineEvent>::from_json_string(value.to_string())
            .unwrap()
            .deserialize()
            .unwrap()
    }

    fn state_event(event_type: &str, content: serde_json::Value) -> AnySyncTimelineEvent {
        sync_event(json!({
            "type": event_type,
            "event_id": "$1",
            "sender": "@alice:example.org",
            "origin_server_ts": 1,
            "state_key": "",
            "content": content,
        }))
    }

    #[test]
    fn text_keeps_html() {
        let msgtype = MessageType::Text(TextMessageEventContent::html("**ahoj**", "<b>ahoj</b>"));

        assert_eq!(
            message_content(&msgtype),
            EventContent::Text {
                body: "**ahoj**".to_owned(),
                html: Some("<b>ahoj</b>".to_owned()),
            }
        );
    }

    #[test]
    fn media_and_location() {
        let mxc = OwnedMxcUri::from("mxc://example.org/abc");
        let image = MessageType::Image(ImageMessageEventContent::plain(
            "fotka.jpg".to_owned(),
            mxc.clone(),
        ));
        let file = MessageType::File(FileMessageEventContent::plain("zprava.pdf".to_owned(), mxc));
        let location = MessageType::Location(LocationMessageEventContent::new(
            "Praha".to_owned(),
            "geo:50.08,14.42".to_owned(),
        ));

        assert_eq!(
            message_content(&image),
            EventContent::Image {
                caption: None,
                source: "mxc://example.org/abc".to_owned(),
                original: "mxc://example.org/abc".to_owned(),
                width: None,
                height: None,
                name: "fotka.jpg".to_owned(),
                size: None,
                mime: None,
            }
        );
        assert_eq!(
            message_content(&file),
            EventContent::File {
                name: "zprava.pdf".to_owned(),
                source: "mxc://example.org/abc".to_owned(),
                size: None,
                mime: None,
            }
        );
        assert_eq!(
            message_content(&location),
            EventContent::Location {
                geo_uri: "geo:50.08,14.42".to_owned(),
                description: Some("Praha".to_owned()),
            }
        );
    }

    #[test]
    fn hides_technical_state_events() {
        let rules = RoomVersionRules::V11;
        let power_levels = state_event("m.room.power_levels", json!({}));
        let join_rules = state_event("m.room.join_rules", json!({ "join_rule": "public" }));
        let name = state_event("m.room.name", json!({ "name": "Pokec" }));

        assert!(!show_event(&power_levels, &rules));
        assert!(!show_event(&join_rules, &rules));
        assert!(show_event(&name, &rules));
    }

    #[test]
    fn waveform_is_normalized_and_resampled() {
        let levels: Vec<f32> = (0..1000).map(|i| (i % 50) as f32 / 100.0).collect();
        let bars = waveform(&levels, WAVEFORM_BARS);

        assert_eq!(bars.len(), WAVEFORM_BARS);
        assert!(bars.iter().all(|v| (0.0..=1.0).contains(v)));
        assert!((bars.iter().copied().fold(0.0, f32::max) - 1.0).abs() < 1e-6);
    }

    #[test]
    fn filenames_lose_paths() {
        assert_eq!(
            clean_filename("/storage/emulated/0/Download/smlouva.pdf"),
            "smlouva.pdf"
        );
        assert_eq!(clean_filename("C:\\Users\\karel\\foto.png"), "foto.png");
        assert_eq!(clean_filename("  "), "soubor");
    }

    #[test]
    fn short_and_silent_recordings() {
        assert_eq!(waveform(&[0.2, 0.4], WAVEFORM_BARS), vec![0.5, 1.0]);
        assert_eq!(waveform(&[0.0; 10], 5), vec![0.0; 5]);
        assert!(waveform(&[], 5).is_empty());
    }

    #[test]
    fn item_keys_roundtrip() {
        let remote = TimelineEventItemId::EventId(EventId::parse("$abc:example.org").unwrap());
        let local = TimelineEventItemId::TransactionId("t123".into());

        assert_eq!(item_key(&remote), "$abc:example.org");
        assert_eq!(item_key(&local), "txn:t123");
        assert_eq!(parse_item_key(&item_key(&remote)).unwrap(), remote);
        assert_eq!(parse_item_key(&item_key(&local)).unwrap(), local);
    }

    #[test]
    fn rejects_broken_item_key() {
        assert!(matches!(
            parse_item_key("nonsense"),
            Err(CoreError::Timeline(_))
        ));
    }

    #[test]
    fn membership_mapping() {
        assert_eq!(
            membership_kind(Some(MembershipChange::Joined)),
            MembershipKind::Joined
        );
        assert_eq!(
            membership_kind(Some(MembershipChange::KickedAndBanned)),
            MembershipKind::Kicked
        );
        assert_eq!(membership_kind(None), MembershipKind::Other);
    }

    fn member_event(
        content: serde_json::Value,
        prev: Option<serde_json::Value>,
    ) -> AnySyncTimelineEvent {
        let mut event = json!({
            "type": "m.room.member",
            "event_id": "$2",
            "sender": "@jana:pokecio.com",
            "origin_server_ts": 1,
            "state_key": "@jana:pokecio.com",
            "content": content,
        });
        if let Some(prev) = prev {
            event["unsigned"] = json!({ "prev_content": prev });
        }
        sync_event(event)
    }

    #[test]
    fn image_keeps_thumbnail_and_original() {
        let mut info = matrix_sdk::ruma::events::room::ImageInfo::new();
        info.thumbnail_source = Some(matrix_sdk::ruma::events::room::MediaSource::Plain(
            "mxc://example.org/small".into(),
        ));
        let mut image = ImageMessageEventContent::plain(
            "fotka.jpg".to_owned(),
            "mxc://example.org/full".into(),
        );
        image.info = Some(Box::new(info));

        let EventContent::Image {
            source, original, ..
        } = message_content(&MessageType::Image(image))
        else {
            panic!("not an image");
        };
        assert_eq!(source, "mxc://example.org/small");
        assert_eq!(original, "mxc://example.org/full");
    }

    #[test]
    fn media_timeline_keeps_only_media() {
        let photo = sync_event(json!({
            "type": "m.room.message",
            "event_id": "$p",
            "sender": "@jana:pokecio.com",
            "origin_server_ts": 1,
            "content": { "msgtype": "m.image", "body": "a.jpg", "url": "mxc://example.org/a" },
        }));
        let text = sync_event(json!({
            "type": "m.room.message",
            "event_id": "$t",
            "sender": "@jana:pokecio.com",
            "origin_server_ts": 1,
            "content": { "msgtype": "m.text", "body": "ahoj" },
        }));
        let topic = state_event("m.room.topic", json!({ "topic": "Pivo" }));

        assert!(is_media(&photo));
        assert!(!is_media(&text));
        assert!(!is_media(&topic));
    }

    #[test]
    fn only_important_changes_stay_in_chat() {
        let encryption = state_event(
            "m.room.encryption",
            json!({ "algorithm": "m.megolm.v1.aes-sha2" }),
        );
        let topic = state_event("m.room.topic", json!({ "topic": "Pivo" }));
        let joined = member_event(json!({ "membership": "join" }), None);
        let renamed = member_event(
            json!({ "membership": "join", "displayname": "Jana" }),
            Some(json!({ "membership": "join", "displayname": "Jája" })),
        );

        assert!(in_chat(&encryption));
        assert!(!in_chat(&topic));
        assert!(!in_chat(&joined));
        assert!(!in_chat(&renamed));
    }

    #[test]
    fn private_receipts_when_sharing_is_off() {
        assert_eq!(receipt_type(true), ReceiptType::Read);
        assert_eq!(receipt_type(false), ReceiptType::ReadPrivate);
    }

    #[test]
    fn shields_keep_severity_and_reason() {
        assert_eq!(shield(TimelineEventShieldState::None), None);
        assert_eq!(
            shield(TimelineEventShieldState::Grey {
                code: TimelineEventShieldStateCode::UnsignedDevice
            }),
            Some(Shield {
                danger: false,
                reason: ShieldReason::UnverifiedDevice,
            })
        );
        assert_eq!(
            shield(TimelineEventShieldState::Red {
                code: TimelineEventShieldStateCode::VerificationViolation
            }),
            Some(Shield {
                danger: true,
                reason: ShieldReason::IdentityChanged,
            })
        );
    }

    #[test]
    fn serializes_for_web() {
        let entry = TimelineEntry {
            id: "a".to_owned(),
            kind: EntryKind::Event(Box::new(EventEntry {
                item_key: "$e".to_owned(),
                event_id: Some("$e".to_owned()),
                sender_id: "@jana:pokecio.com".to_owned(),
                sender_name: Some("Jana".to_owned()),
                sender_avatar: None,
                timestamp_ms: 1,
                is_own: false,
                is_edited: false,
                content: EventContent::State(StateChange::Membership {
                    user_id: "@jana:pokecio.com".to_owned(),
                    name: None,
                    change: MembershipKind::Joined,
                }),
                reactions: vec![],
                in_reply_to: Some(ReplyPreview {
                    event_id: "$r".to_owned(),
                    sender_id: None,
                    sender_name: None,
                    content: Some(PreviewKind::Text("ahoj".to_owned())),
                }),
                send_state: SendState::Sent,
                shield: Some(Shield {
                    danger: true,
                    reason: ShieldReason::IdentityChanged,
                }),
                read_by: vec![ReadReceipt {
                    user_id: "@petr:pokecio.com".to_owned(),
                    timestamp_ms: Some(7),
                }],
                can_reply: true,
                can_edit: false,
                can_redact: false,
            })),
        };
        let json = serde_json::to_value(TimelineDiff::Insert { index: 2, entry }).unwrap();

        assert_eq!(json["op"], "insert");
        let event = &json["value"]["entry"]["kind"];
        assert_eq!(event["type"], "event");
        assert_eq!(event["senderName"], "Jana");
        assert_eq!(event["sendState"], "sent");
        assert_eq!(event["shield"]["danger"], true);
        assert_eq!(event["readBy"][0]["userId"], "@petr:pokecio.com");
        assert_eq!(event["readBy"][0]["timestampMs"], 7);
        assert_eq!(event["shield"]["reason"], "identityChanged");
        assert_eq!(event["content"]["kind"], "state");
        assert_eq!(event["content"]["type"], "membership");
        assert_eq!(event["content"]["change"], "joined");
        assert_eq!(event["inReplyTo"]["content"]["kind"], "text");
        assert_eq!(event["inReplyTo"]["content"]["body"], "ahoj");

        let divider = serde_json::to_value(EntryKind::DateDivider { timestamp_ms: 5 }).unwrap();
        assert_eq!(divider["type"], "dateDivider");
        assert_eq!(divider["timestampMs"], 5);
    }

    #[tokio::test]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_timeline() {
        use std::time::Duration;

        use crate::{
            build_client, create_sync_service, login, logout, watch_rooms, Server, SERVER_NAME,
        };

        let user = std::env::var("POCKY_TEST_USER").expect("POCKY_TEST_USER");
        let password = std::env::var("POCKY_TEST_PASSWORD").expect("POCKY_TEST_PASSWORD");
        let dir = tempfile::tempdir().unwrap();

        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP test")
            .await
            .unwrap();

        let service = create_sync_service(&client).await.unwrap();
        service.start().await;

        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
        let watcher = Box::pin(watch_rooms(&service, move |rooms| {
            let _ = tx.send(rooms);
        }));
        let rooms = tokio::select! {
            result = watcher => panic!("watcher stopped: {result:?}"),
            rooms = async {
                loop {
                    let rooms = rx.recv().await.unwrap();
                    if !rooms.is_empty() {
                        break rooms;
                    }
                }
            } => rooms,
        };

        let room = rooms.iter().find(|r| !r.is_encrypted).unwrap_or(&rooms[0]);
        let timeline = Box::pin(open_timeline(&client, Some(&service), &room.id))
            .await
            .unwrap();

        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
        let watcher = Box::pin(watch_timeline(&timeline, move |diffs| {
            let _ = tx.send(diffs);
        }));

        let collect = async {
            let mut entries = Vec::<TimelineEntry>::new();
            let first = rx.recv().await.unwrap();
            apply(&mut entries, first);
            let reached_start = Box::pin(paginate_backwards(&timeline)).await.unwrap();

            while let Ok(Some(diffs)) =
                tokio::time::timeout(Duration::from_secs(5), rx.recv()).await
            {
                apply(&mut entries, diffs);
            }
            (entries, reached_start)
        };

        let (entries, reached_start) = tokio::select! {
            _ = watcher => panic!("timeline watcher stopped"),
            result = collect => result,
        };

        println!(
            "room: {} | entries: {} | reached start: {reached_start}",
            room.name,
            entries.len()
        );
        for entry in &entries {
            let kind = match &entry.kind {
                EntryKind::Event(event) => format!(
                    "event {} own={} reactions={} reply={} state={:?}",
                    content_name(&event.content),
                    event.is_own,
                    event.reactions.len(),
                    event.in_reply_to.is_some(),
                    event.send_state
                ),
                other => format!("{other:?}"),
            };
            println!("  {kind}");
        }
        assert!(!entries.is_empty());

        service.stop().await;
        logout(&client).await.unwrap();
    }

    fn apply(entries: &mut Vec<TimelineEntry>, diffs: Vec<TimelineDiff>) {
        for diff in diffs {
            match diff {
                TimelineDiff::Reset(values) => *entries = values,
                TimelineDiff::Append(values) => entries.extend(values),
                TimelineDiff::Clear => entries.clear(),
                TimelineDiff::PushFront(entry) => entries.insert(0, entry),
                TimelineDiff::PushBack(entry) => entries.push(entry),
                TimelineDiff::PopFront => {
                    entries.remove(0);
                }
                TimelineDiff::PopBack => {
                    entries.pop();
                }
                TimelineDiff::Insert { index, entry } => entries.insert(index as usize, entry),
                TimelineDiff::Set { index, entry } => entries[index as usize] = entry,
                TimelineDiff::Remove { index } => {
                    entries.remove(index as usize);
                }
                TimelineDiff::Truncate { length } => entries.truncate(length as usize),
            }
        }
    }

    fn content_name(content: &EventContent) -> String {
        match content {
            EventContent::State(change) => format!("state:{}", state_name(change)),
            EventContent::Text { html, .. } => format!("text(html={})", html.is_some()),
            other => format!("{other:?}")
                .split([' ', '{', '('])
                .next()
                .unwrap_or_default()
                .to_owned(),
        }
    }

    fn state_name(change: &StateChange) -> String {
        match change {
            StateChange::Membership { change, .. } => format!("membership:{change:?}"),
            other => format!("{other:?}")
                .split([' ', '{'])
                .next()
                .unwrap_or_default()
                .to_owned(),
        }
    }
}
