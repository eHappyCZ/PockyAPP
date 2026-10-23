import type { PockyClient } from "./core";
import type {
  ChatStyle,
  LocationPrecision,
  OwnStatus,
  PresenceInfo,
  PresenceState,
  Privacy,
  Typist,
} from "./types";

const refreshMs = 60_000;
const maxTracked = 40;

export class PresenceTracker {
  presence = $state<Record<string, PresenceState>>({});
  statuses = $state<Record<string, string>>({});
  typing = $state<Record<string, Typist[]>>({});
  own = $state<OwnStatus | null>(null);
  privacy = $state<Privacy>({ sharePresence: true, shareTyping: true });

  private users: string[] = [];
  private timer: ReturnType<typeof setTimeout> | undefined;
  private expiry: ReturnType<typeof setTimeout> | undefined;
  private watchingTyping = false;
  private stopped = false;

  constructor(private client: PockyClient) {
    client
      .privacy()
      .then((value: Privacy) => {
        this.privacy = value;
        this.watchTyping();
      })
      .catch(() => undefined);
    client
      .ownStatus()
      .then((value: OwnStatus | null) => this.applyOwn(value))
      .catch(() => undefined);
    document.addEventListener("visibilitychange", this.onVisible);
  }

  track(userIds: string[]) {
    const next = [...new Set(userIds)].slice(0, maxTracked);
    const changed = next.length !== this.users.length || next.some((id, i) => id !== this.users[i]);
    this.users = next;
    if (changed) this.schedule(0);
  }

  typingIn(roomId: string): Typist[] {
    return this.privacy.shareTyping ? (this.typing[roomId] ?? []) : [];
  }

  async setStatus(text: string, untilMs: number | null) {
    this.applyOwn(await this.client.setStatus(text, untilMs ?? undefined));
  }

  async clearStatus() {
    await this.client.clearStatus();
    this.applyOwn(null);
  }

  async setPrivacy(value: Privacy) {
    await this.client.setPrivacy(value.sharePresence, value.shareTyping);
    this.privacy = value;
    if (!value.sharePresence) this.presence = {};
    if (!value.shareTyping) this.typing = {};
    this.watchTyping();
    this.schedule(0);
  }

  async setLocationPrecision(precision: LocationPrecision) {
    await this.client.setLocationPrecision(precision);
    this.privacy = { ...this.privacy, locationPrecision: precision };
  }

  async setChatStyle(style: ChatStyle) {
    await this.client.setChatStyle(style);
    this.privacy = { ...this.privacy, chatStyle: style };
  }

  stop() {
    this.stopped = true;
    clearTimeout(this.timer);
    clearTimeout(this.expiry);
    document.removeEventListener("visibilitychange", this.onVisible);
  }

  private onVisible = () => {
    if (document.visibilityState === "visible") this.schedule(0);
  };

  private applyOwn(value: OwnStatus | null) {
    this.own = value;
    clearTimeout(this.expiry);
    if (value?.untilMs) {
      const wait = Math.max(0, value.untilMs - Date.now());
      this.expiry = setTimeout(() => this.clearStatus().catch(() => undefined), Math.min(wait, 2 ** 31 - 1));
    }
  }

  private watchTyping() {
    if (this.watchingTyping || !this.privacy.shareTyping) return;
    this.watchingTyping = true;
    this.client
      .watchTyping((roomId: string, users: Typist[]) => {
        if (!this.privacy.shareTyping) return;
        const next = { ...this.typing };
        if (users.length === 0) delete next[roomId];
        else next[roomId] = users;
        this.typing = next;
      })
      .catch(() => undefined)
      .finally(() => (this.watchingTyping = false));
  }

  private schedule(delay: number) {
    clearTimeout(this.timer);
    if (this.stopped) return;
    this.timer = setTimeout(() => this.refresh(), delay);
  }

  private async refresh() {
    if (document.visibilityState !== "visible" || this.users.length === 0) return;
    const presence: Record<string, PresenceState> = {};
    const statuses: Record<string, string> = {};
    for (const userId of this.users) {
      try {
        const info: PresenceInfo = await this.client.fetchPresence(userId);
        if (this.privacy.sharePresence) presence[userId] = info.presence;
        if (info.status) statuses[userId] = info.status;
      } catch {
        if (this.presence[userId]) presence[userId] = this.presence[userId];
        if (this.statuses[userId]) statuses[userId] = this.statuses[userId];
      }
      if (this.stopped) return;
    }
    this.presence = presence;
    this.statuses = statuses;
    this.schedule(refreshMs);
  }
}

export function typingText(typists: Typist[], isDirect: boolean): string {
  const names = typists.map((t) => t.name ?? t.userId.split(":")[0].replace(/^@/, ""));
  if (isDirect || names.length === 0) return "píše…";
  if (names.length === 1) return `${names[0]} píše…`;
  if (names.length === 2) return `${names[0]} a ${names[1]} píšou…`;
  return "Několik lidí píše…";
}
