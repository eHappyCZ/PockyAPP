import { previewText } from "./text";
import type { Room } from "./types";

const key = "pocky-alerts";

interface Settings {
  enabled: boolean;
  showText: boolean;
}

function read(): Settings {
  try {
    return { enabled: false, showText: false, ...JSON.parse(localStorage.getItem(key) ?? "{}") };
  } catch {
    return { enabled: false, showText: false };
  }
}

export class Alerts {
  settings = $state<Settings>(read());
  permission = $state<NotificationPermission>(typeof Notification === "undefined" ? "denied" : Notification.permission);

  private unread = new Map<string, number>();
  private primed = false;

  get supported(): boolean {
    return typeof Notification !== "undefined";
  }

  async enable(enabled: boolean) {
    if (enabled && this.supported && Notification.permission === "default") {
      this.permission = await Notification.requestPermission();
    }
    this.save({ ...this.settings, enabled: enabled && this.permission === "granted" });
  }

  setShowText(showText: boolean) {
    this.save({ ...this.settings, showText });
  }

  update(rooms: Room[], activeRoomId: string | null, onOpen: (roomId: string) => void) {
    const total = rooms.reduce((sum, room) => sum + (room.isMuted ? 0 : room.unreadMessages), 0);
    document.title = total > 0 ? `(${total}) PockyAPP` : "PockyAPP";

    for (const room of rooms) {
      const before = this.unread.get(room.id) ?? 0;
      this.unread.set(room.id, room.unreadMessages);
      if (!this.primed || room.isMuted || room.unreadMessages <= before) continue;
      if (document.visibilityState === "visible" && room.id === activeRoomId) continue;
      this.show(room, onOpen);
    }
    this.primed = true;
  }

  private show(room: Room, onOpen: (roomId: string) => void) {
    if (!this.settings.enabled || !this.supported || Notification.permission !== "granted") return;
    const body =
      this.settings.showText && room.lastMessage ? previewText(room.lastMessage, room.isDirect) : "Nová zpráva";
    const notification = new Notification(room.name, { body, tag: room.id, icon: "/logo.svg" });
    notification.onclick = () => {
      window.focus();
      onOpen(room.id);
      notification.close();
    };
  }

  private save(settings: Settings) {
    this.settings = settings;
    try {
      localStorage.setItem(key, JSON.stringify(settings));
    } catch {
      return;
    }
  }
}
