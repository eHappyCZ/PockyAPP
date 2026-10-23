export type MessageContent =
  | { kind: "text"; body: string }
  | { kind: "emote"; body: string }
  | {
      kind:
        | "image"
        | "video"
        | "audio"
        | "file"
        | "location"
        | "sticker"
        | "poll"
        | "encrypted"
        | "invite"
        | "other";
    };

export interface LastMessage {
  senderId: string;
  senderName?: string;
  isOwn: boolean;
  content: MessageContent;
  delivery?: "sending" | "failed" | "sent" | "read";
}

export interface Room {
  id: string;
  name: string;
  avatarUrl?: string;
  lastMessage?: LastMessage;
  timestampMs?: number;
  unreadMessages: number;
  unreadMentions: number;
  isEncrypted: boolean;
  isDirect: boolean;
  directUserId?: string;
  isInvite: boolean;
  isFavourite: boolean;
  isMuted: boolean;
}

export type SyncStatus = "idle" | "running" | "offline" | "error" | "terminated";

export type PreviewContent = { kind: "text" | "emote"; body: string } | { kind: Exclude<MessageContent["kind"], "text" | "emote"> };

export type MembershipKind =
  | "joined"
  | "left"
  | "invited"
  | "invitationAccepted"
  | "invitationRejected"
  | "invitationRevoked"
  | "kicked"
  | "banned"
  | "unbanned"
  | "knocked"
  | "other";

export type StateChange =
  | { type: "membership"; userId: string; name?: string; change: MembershipKind }
  | { type: "displayName"; userId: string; old?: string; new?: string }
  | { type: "avatar"; userId: string; name?: string }
  | { type: "roomName"; name?: string }
  | { type: "roomTopic"; topic?: string }
  | { type: "roomAvatar" | "roomCreated" | "encryptionEnabled" | "other" };

export type EventContent =
  | { kind: "text" | "notice" | "emote"; body: string; html?: string }
  | { kind: "image"; caption?: string; source: string; width?: number; height?: number }
  | {
      kind: "video";
      caption?: string;
      source: string;
      thumbnail?: string;
      width?: number;
      height?: number;
      durationMs?: number;
      size?: number;
    }
  | { kind: "audio"; caption: string; source: string; durationMs?: number; waveform: number[]; voice: boolean }
  | { kind: "file"; name: string; source: string; size?: number; mime?: string }
  | { kind: "location"; geoUri: string; description?: string }
  | { kind: "liveLocation"; isLive: boolean; geoUri?: string }
  | { kind: "sticker"; body: string; source: string; width?: number; height?: number }
  | { kind: "poll"; question: string }
  | ({ kind: "state" } & StateChange)
  | { kind: "redacted" | "unableToDecrypt" | "other" };

export interface Reaction {
  key: string;
  count: number;
  reactedByMe: boolean;
}

export interface ReplyPreview {
  eventId: string;
  senderId?: string;
  senderName?: string;
  content?: PreviewContent;
}

export interface ReadReceipt {
  userId: string;
  timestampMs?: number;
}

export interface Shield {
  danger: boolean;
  reason:
    | "authenticityNotGuaranteed"
    | "unknownDevice"
    | "unverifiedDevice"
    | "unverifiedSender"
    | "identityChanged"
    | "mismatchedSender"
    | "sentInClear";
}

export interface EventEntry {
  itemKey: string;
  eventId?: string;
  senderId: string;
  senderName?: string;
  senderAvatar?: string;
  timestampMs: number;
  isOwn: boolean;
  isEdited: boolean;
  content: EventContent;
  reactions: Reaction[];
  inReplyTo?: ReplyPreview;
  sendState: "sending" | "sent" | "failed";
  shield?: Shield;
  readBy: ReadReceipt[];
  canReply: boolean;
  canEdit: boolean;
  canRedact: boolean;
}

export type EntryKind =
  | { type: "dateDivider"; timestampMs: number }
  | { type: "readMarker" }
  | { type: "timelineStart" }
  | ({ type: "event" } & EventEntry);

export interface TimelineEntry {
  id: string;
  kind: EntryKind;
}

export type TimelineDiff =
  | { op: "reset" | "append"; value: TimelineEntry[] }
  | { op: "clear" | "popFront" | "popBack" }
  | { op: "pushFront" | "pushBack"; value: TimelineEntry }
  | { op: "insert" | "set"; value: { index: number; entry: TimelineEntry } }
  | { op: "remove"; value: { index: number } }
  | { op: "truncate"; value: { length: number } };

export interface Draft {
  mode: "reply" | "edit";
  itemKey: string;
  eventId?: string;
  sender: string;
  text: string;
}

export interface MessageActions {
  reply: (event: EventEntry) => void;
  edit: (event: EventEntry) => void;
  react: (event: EventEntry, key: string) => void;
  remove: (event: EventEntry) => void;
  openLocation: (point: GeoPoint, title: string) => void;
  openImage: (event: EventEntry) => void;
  info: (event: EventEntry) => void;
  forward: (event: EventEntry) => void;
}

export type TrustLevel = "noIdentity" | "unverified" | "verified" | "verificationBroken";

export interface IdentityWarning {
  userId: string;
  name?: string;
  wasVerified: boolean;
}

export interface Encryption {
  canRecover: boolean;
  canVerifyWithDevice: boolean;
  deviceVerified: boolean;
  backupEnabled: boolean;
  recoveryEnabled: boolean;
}

export type PassphraseStrength = "tooShort" | "noSymbol" | "weak" | "fair" | "strong";

export interface SasEmoji {
  index: number;
  symbol: string;
  description: string;
}

export type VerificationStep =
  | { step: "requested" | "incoming" | "ready" | "starting" | "confirmed" | "done" }
  | { step: "emojis"; emojis: SasEmoji[] }
  | { step: "cancelled"; byUs: boolean; reason: string };

export interface Device {
  deviceId: string;
  name?: string;
  lastSeenMs?: number;
  isCurrent: boolean;
  verified: boolean;
}

export type PresenceState = "online" | "unavailable" | "offline" | "unknown";

export interface PresenceInfo {
  userId: string;
  presence: PresenceState;
  lastActiveMs?: number;
  status?: string;
}

export interface OwnStatus {
  text: string;
  untilMs?: number;
}

export interface Typist {
  userId: string;
  name?: string;
}

export interface Privacy {
  sharePresence: boolean;
  shareTyping: boolean;
  locationPrecision?: LocationPrecision;
  chatStyle?: ChatStyle;
}

export type ChatStyle = "classic" | "aura";

export type Aura = "online" | "away" | "typing" | "own";

export interface GeoPoint {
  latitude: number;
  longitude: number;
  accuracyM?: number;
}

export interface LiveLocation {
  roomId: string;
  userId: string;
  name?: string;
  avatarUrl?: string;
  geoUri?: string;
  updatedMs?: number;
  endsMs: number;
  isOwn: boolean;
}

export type LocationPrecision = "exact" | "neighborhood" | "city";

export interface Person {
  userId: string;
  displayName?: string;
  avatarUrl?: string;
}

export interface Invite {
  roomId: string;
  inviterId: string;
  inviterName?: string;
  inviterAvatar?: string;
}

export interface Member {
  userId: string;
  name?: string;
  avatarUrl?: string;
  role: "admin" | "moderator" | "member";
  invited: boolean;
  isOwn: boolean;
  canRemove: boolean;
}

export interface RoomInfo {
  roomId: string;
  name: string;
  topic?: string;
  avatarUrl?: string;
  isDirect: boolean;
  isEncrypted: boolean;
  directUserId?: string;
  members: Member[];
  canEditName: boolean;
  canEditTopic: boolean;
  canEditAvatar: boolean;
  canInvite: boolean;
}
