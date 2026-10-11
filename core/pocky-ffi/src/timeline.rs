use std::sync::{Arc, Mutex};

use pocky_core::{
    EntryKind, EventContent, EventEntry, MembershipKind, Reaction, ReplyPreview, SendState,
    StateChange, Timeline, TimelineDiff, TimelineEntry,
};

use crate::{
    location::GeoLocation, preferences::LocationArea, rooms::MessageContent, run, task::TaskHandle,
    trust::ShieldInfo, PockyError, RUNTIME,
};

#[derive(Debug, Clone, uniffi::Record)]
pub struct TimelineItem {
    pub id: String,
    pub kind: TimelineItemKind,
}

#[allow(clippy::large_enum_variant)]
#[derive(Debug, Clone, uniffi::Enum)]
pub enum TimelineItemKind {
    DateDivider { timestamp_ms: u64 },
    ReadMarker,
    TimelineStart,
    Event { event: TimelineEvent },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct TimelineEvent {
    pub item_key: String,
    pub event_id: Option<String>,
    pub sender_id: String,
    pub sender_name: Option<String>,
    pub sender_avatar: Option<String>,
    pub timestamp_ms: u64,
    pub is_own: bool,
    pub is_edited: bool,
    pub content: TimelineContent,
    pub reactions: Vec<TimelineReaction>,
    pub in_reply_to: Option<TimelineReply>,
    pub send_state: TimelineSendState,
    pub shield: Option<ShieldInfo>,
    pub read_by: Vec<ReadBy>,
    pub can_reply: bool,
    pub can_edit: bool,
    pub can_redact: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ReadBy {
    pub user_id: String,
    pub timestamp_ms: Option<u64>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct VideoUpload {
    pub bytes: Vec<u8>,
    pub mime: String,
    pub width: u32,
    pub height: u32,
    pub duration_ms: u64,
    pub thumbnail: Option<Vec<u8>>,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum TimelineContent {
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
    State {
        change: TimelineStateChange,
    },
    Other,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum TimelineStateChange {
    Membership {
        user_id: String,
        name: Option<String>,
        change: Membership,
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

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum Membership {
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

#[derive(Debug, Clone, uniffi::Record)]
pub struct TimelineReaction {
    pub key: String,
    pub count: u32,
    pub reacted_by_me: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct TimelineReply {
    pub event_id: String,
    pub sender_id: Option<String>,
    pub sender_name: Option<String>,
    pub content: Option<MessageContent>,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum TimelineSendState {
    Sending,
    Sent,
    Failed,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum TimelineChange {
    Reset { items: Vec<TimelineItem> },
    Append { items: Vec<TimelineItem> },
    Clear,
    PushFront { item: TimelineItem },
    PushBack { item: TimelineItem },
    PopFront,
    PopBack,
    Insert { index: u32, item: TimelineItem },
    Set { index: u32, item: TimelineItem },
    Remove { index: u32 },
    Truncate { length: u32 },
}

#[uniffi::export(callback_interface)]
pub trait TimelineListener: Send + Sync {
    fn on_changes(&self, changes: Vec<TimelineChange>);
}

#[derive(uniffi::Object)]
pub struct RoomTimeline {
    inner: Mutex<Option<Arc<Timeline>>>,
}

impl RoomTimeline {
    pub(crate) fn new(timeline: Timeline) -> Arc<Self> {
        Arc::new(Self {
            inner: Mutex::new(Some(Arc::new(timeline))),
        })
    }

    fn timeline(&self) -> Result<Arc<Timeline>, PockyError> {
        self.inner
            .lock()
            .unwrap()
            .clone()
            .ok_or_else(|| PockyError::Other("timeline closed".to_owned()))
    }
}

impl Drop for RoomTimeline {
    fn drop(&mut self) {
        let _guard = RUNTIME.enter();
        drop(self.inner.get_mut().unwrap().take());
    }
}

#[uniffi::export]
impl RoomTimeline {
    pub fn observe(
        &self,
        listener: Box<dyn TimelineListener>,
    ) -> Result<Arc<TaskHandle>, PockyError> {
        let timeline = self.timeline()?;
        let handle = RUNTIME.spawn(async move {
            pocky_core::watch_timeline(&timeline, |diffs| {
                listener.on_changes(diffs.into_iter().map(Into::into).collect());
            })
            .await;
        });

        Ok(Arc::new(TaskHandle::new(handle)))
    }

    pub async fn paginate_backwards(&self) -> Result<bool, PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::paginate_backwards(&timeline).await }).await
    }

    pub async fn send_text(&self, text: String) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::send_text(&timeline, &text).await }).await
    }

    pub async fn send_reply(&self, text: String, in_reply_to: String) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::send_reply(&timeline, &text, &in_reply_to).await }).await
    }

    pub async fn edit(&self, item_key: String, text: String) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::edit_text(&timeline, &item_key, &text).await }).await
    }

    pub async fn toggle_reaction(&self, item_key: String, key: String) -> Result<bool, PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::toggle_reaction(&timeline, &item_key, &key).await }).await
    }

    pub async fn redact(&self, item_key: String) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::redact(&timeline, &item_key).await }).await
    }

    pub async fn send_image(
        &self,
        bytes: Vec<u8>,
        caption: Option<String>,
        full_quality: bool,
    ) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move {
            pocky_core::send_image(&timeline, &bytes, caption.as_deref(), full_quality).await
        })
        .await
    }

    pub async fn send_file(
        &self,
        bytes: Vec<u8>,
        filename: String,
        mime: String,
    ) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::send_file(&timeline, bytes, &filename, &mime).await }).await
    }

    pub async fn send_video(
        &self,
        clip: VideoUpload,
        caption: Option<String>,
    ) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        let clip = pocky_core::VideoClip {
            bytes: clip.bytes,
            mime: clip.mime,
            width: clip.width,
            height: clip.height,
            duration_ms: clip.duration_ms,
            thumbnail: clip.thumbnail,
        };
        run(async move { pocky_core::send_video(&timeline, clip, caption.as_deref()).await }).await
    }

    pub async fn send_voice(
        &self,
        bytes: Vec<u8>,
        mime: String,
        duration_ms: u64,
        levels: Vec<f32>,
    ) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::send_voice(&timeline, bytes, &mime, duration_ms, &levels).await })
            .await
    }

    pub async fn send_location(
        &self,
        location: GeoLocation,
        area: LocationArea,
    ) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::send_location(&timeline, location.into(), area.into()).await })
            .await
    }

    pub async fn mark_as_read(&self) -> Result<(), PockyError> {
        let timeline = self.timeline()?;
        run(async move { pocky_core::mark_as_read(&timeline).await }).await
    }
}

impl From<TimelineDiff> for TimelineChange {
    fn from(diff: TimelineDiff) -> Self {
        let items = |entries: Vec<TimelineEntry>| entries.into_iter().map(Into::into).collect();

        match diff {
            TimelineDiff::Reset(entries) => Self::Reset {
                items: items(entries),
            },
            TimelineDiff::Append(entries) => Self::Append {
                items: items(entries),
            },
            TimelineDiff::Clear => Self::Clear,
            TimelineDiff::PushFront(entry) => Self::PushFront { item: entry.into() },
            TimelineDiff::PushBack(entry) => Self::PushBack { item: entry.into() },
            TimelineDiff::PopFront => Self::PopFront,
            TimelineDiff::PopBack => Self::PopBack,
            TimelineDiff::Insert { index, entry } => Self::Insert {
                index,
                item: entry.into(),
            },
            TimelineDiff::Set { index, entry } => Self::Set {
                index,
                item: entry.into(),
            },
            TimelineDiff::Remove { index } => Self::Remove { index },
            TimelineDiff::Truncate { length } => Self::Truncate { length },
        }
    }
}

impl From<TimelineEntry> for TimelineItem {
    fn from(entry: TimelineEntry) -> Self {
        let kind = match entry.kind {
            EntryKind::DateDivider { timestamp_ms } => {
                TimelineItemKind::DateDivider { timestamp_ms }
            }
            EntryKind::ReadMarker => TimelineItemKind::ReadMarker,
            EntryKind::TimelineStart => TimelineItemKind::TimelineStart,
            EntryKind::Event(event) => TimelineItemKind::Event {
                event: (*event).into(),
            },
        };

        Self { id: entry.id, kind }
    }
}

impl From<EventEntry> for TimelineEvent {
    fn from(event: EventEntry) -> Self {
        Self {
            item_key: event.item_key,
            event_id: event.event_id,
            sender_id: event.sender_id,
            sender_name: event.sender_name,
            sender_avatar: event.sender_avatar,
            timestamp_ms: event.timestamp_ms,
            is_own: event.is_own,
            is_edited: event.is_edited,
            content: event.content.into(),
            reactions: event.reactions.into_iter().map(Into::into).collect(),
            in_reply_to: event.in_reply_to.map(Into::into),
            send_state: match event.send_state {
                SendState::Sending => TimelineSendState::Sending,
                SendState::Sent => TimelineSendState::Sent,
                SendState::Failed => TimelineSendState::Failed,
            },
            shield: event.shield.map(Into::into),
            read_by: event
                .read_by
                .into_iter()
                .map(|receipt| ReadBy {
                    user_id: receipt.user_id,
                    timestamp_ms: receipt.timestamp_ms,
                })
                .collect(),
            can_reply: event.can_reply,
            can_edit: event.can_edit,
            can_redact: event.can_redact,
        }
    }
}

impl From<EventContent> for TimelineContent {
    fn from(content: EventContent) -> Self {
        match content {
            EventContent::Text { body, html } => Self::Text { body, html },
            EventContent::Notice { body, html } => Self::Notice { body, html },
            EventContent::Emote { body, html } => Self::Emote { body, html },
            EventContent::Image {
                caption,
                source,
                original,
                width,
                height,
                name,
                size,
                mime,
            } => Self::Image {
                caption,
                source,
                original,
                width,
                height,
                name,
                size,
                mime,
            },
            EventContent::Video {
                caption,
                source,
                thumbnail,
                width,
                height,
                duration_ms,
                size,
                name,
                mime,
            } => Self::Video {
                caption,
                source,
                thumbnail,
                width,
                height,
                duration_ms,
                size,
                name,
                mime,
            },
            EventContent::Audio {
                caption,
                source,
                duration_ms,
                waveform,
                voice,
            } => Self::Audio {
                caption,
                source,
                duration_ms,
                waveform,
                voice,
            },
            EventContent::File {
                name,
                source,
                size,
                mime,
            } => Self::File {
                name,
                source,
                size,
                mime,
            },
            EventContent::Location {
                geo_uri,
                description,
            } => Self::Location {
                geo_uri,
                description,
            },
            EventContent::LiveLocation { is_live, geo_uri } => {
                Self::LiveLocation { is_live, geo_uri }
            }
            EventContent::Sticker {
                body,
                source,
                width,
                height,
            } => Self::Sticker {
                body,
                source,
                width,
                height,
            },
            EventContent::Poll { question } => Self::Poll { question },
            EventContent::Redacted => Self::Redacted,
            EventContent::UnableToDecrypt => Self::UnableToDecrypt,
            EventContent::State(change) => Self::State {
                change: change.into(),
            },
            EventContent::Other => Self::Other,
        }
    }
}

impl From<StateChange> for TimelineStateChange {
    fn from(change: StateChange) -> Self {
        match change {
            StateChange::Membership {
                user_id,
                name,
                change,
            } => Self::Membership {
                user_id,
                name,
                change: change.into(),
            },
            StateChange::DisplayName { user_id, old, new } => {
                Self::DisplayName { user_id, old, new }
            }
            StateChange::Avatar { user_id, name } => Self::Avatar { user_id, name },
            StateChange::RoomName { name } => Self::RoomName { name },
            StateChange::RoomTopic { topic } => Self::RoomTopic { topic },
            StateChange::RoomAvatar => Self::RoomAvatar,
            StateChange::RoomCreated => Self::RoomCreated,
            StateChange::EncryptionEnabled => Self::EncryptionEnabled,
            StateChange::Other => Self::Other,
        }
    }
}

impl From<MembershipKind> for Membership {
    fn from(kind: MembershipKind) -> Self {
        match kind {
            MembershipKind::Joined => Self::Joined,
            MembershipKind::Left => Self::Left,
            MembershipKind::Invited => Self::Invited,
            MembershipKind::InvitationAccepted => Self::InvitationAccepted,
            MembershipKind::InvitationRejected => Self::InvitationRejected,
            MembershipKind::InvitationRevoked => Self::InvitationRevoked,
            MembershipKind::Kicked => Self::Kicked,
            MembershipKind::Banned => Self::Banned,
            MembershipKind::Unbanned => Self::Unbanned,
            MembershipKind::Knocked => Self::Knocked,
            MembershipKind::Other => Self::Other,
        }
    }
}

impl From<Reaction> for TimelineReaction {
    fn from(reaction: Reaction) -> Self {
        Self {
            key: reaction.key,
            count: reaction.count,
            reacted_by_me: reaction.reacted_by_me,
        }
    }
}

impl From<ReplyPreview> for TimelineReply {
    fn from(reply: ReplyPreview) -> Self {
        Self {
            event_id: reply.event_id,
            sender_id: reply.sender_id,
            sender_name: reply.sender_name,
            content: reply.content.map(Into::into),
        }
    }
}
