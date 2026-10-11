import type { EventContent, LastMessage, PreviewContent, StateChange } from "./types";

const errors: Record<string, string> = {
  invalid_credentials: "Nesprávné jméno nebo heslo.",
  user_deactivated: "Tento účet byl deaktivován.",
  rate_limited: "Příliš mnoho pokusů. Zkus to za chvíli.",
  network: "Server není dostupný. Zkontroluj adresu serveru a připojení.",
  server_not_found: "Takový Matrix server se nepodařilo najít.",
  password_login_unsupported: "Tenhle server nepovoluje přihlášení heslem.",
  session_expired: "Přihlášení vypršelo. Přihlas se znovu.",
  invalid_recovery_key: "Klíč nebo fráze nesedí. Zkontroluj ho a zkus to znovu.",
  file_too_large: "Soubor je na server moc velký.",
};

export function errorText(err: unknown): string {
  const code = err instanceof Error ? err.name : "";
  return errors[code] ?? "Něco se pokazilo. Zkus to znovu.";
}

const previews: Record<string, string> = {
  image: "📷 Obrázek",
  video: "🎬 Video",
  audio: "🎤 Zvuková zpráva",
  file: "📎 Soubor",
  location: "📍 Poloha",
  sticker: "Nálepka",
  poll: "📊 Anketa",
  encrypted: "🔒 Šifrovaná zpráva",
  invite: "Pozvánka do konverzace",
  other: "Zpráva",
};

export function previewContent(c: PreviewContent): string {
  return c.kind === "text" ? c.body : c.kind === "emote" ? `* ${c.body}` : previews[c.kind];
}

export function previewText(message: LastMessage, isDirect: boolean): string {
  const content = previewContent(message.content);

  const sender = message.isOwn
    ? "Ty"
    : isDirect
      ? null
      : (message.senderName ?? message.senderId.split(":")[0].replace(/^@/, ""));

  return sender ? `${sender}: ${content}` : content;
}

const dayMs = 86_400_000;

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

export function formatTimestamp(timestampMs: number): string {
  const time = new Date(timestampMs);
  const today = startOfDay(new Date());
  const day = startOfDay(time);

  if (day === today) return `${time.getHours()}:${String(time.getMinutes()).padStart(2, "0")}`;
  if (day === today - dayMs) return "Včera";
  if (day > today - 7 * dayMs) {
    const name = time.toLocaleDateString("cs-CZ", { weekday: "short" });
    return name.charAt(0).toUpperCase() + name.slice(1);
  }
  const year = time.getFullYear() === new Date().getFullYear() ? "" : ` ${time.getFullYear()}`;
  return `${time.getDate()}. ${time.getMonth() + 1}.${year}`;
}

export function unreadText(count: number): string {
  if (count === 1) return "1 nepřečtená zpráva";
  if (count >= 2 && count <= 4) return `${count} nepřečtené zprávy`;
  return `${count} nepřečtených zpráv`;
}

export function shortName(userId: string): string {
  return userId.split(":")[0].replace(/^@/, "");
}

export function timeOfDay(timestampMs: number): string {
  const time = new Date(timestampMs);
  return `${time.getHours()}:${String(time.getMinutes()).padStart(2, "0")}`;
}

export function dayLabel(timestampMs: number): string {
  const today = startOfDay(new Date());
  const day = startOfDay(new Date(timestampMs));
  if (day === today) return "Dnes";
  if (day === today - dayMs) return "Včera";
  const label = new Date(timestampMs).toLocaleDateString("cs-CZ", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: new Date(timestampMs).getFullYear() === new Date().getFullYear() ? undefined : "numeric",
  });
  return label.charAt(0).toUpperCase() + label.slice(1);
}

const membership: Record<string, string> = {
  joined: "{a} se připojil(a)",
  left: "{a} odešel/odešla",
  invited: "{a} pozval(a) {b}",
  invitationAccepted: "{a} přijal(a) pozvání",
  invitationRejected: "{a} odmítl(a) pozvání",
  invitationRevoked: "{a} zrušil(a) pozvání pro {b}",
  kicked: "{a} odebral(a) {b}",
  banned: "{a} zablokoval(a) {b}",
  unbanned: "{a} odblokoval(a) {b}",
  knocked: "{a} žádá o vstup",
  other: "{a} změnil(a) členství",
};

function fill(template: string, a: string, b = ""): string {
  return template.replace("{a}", a).replace("{b}", b);
}

export function stateText(change: StateChange, sender: string): string {
  switch (change.type) {
    case "membership": {
      const target = change.name ?? shortName(change.userId);
      const twoSided = ["invited", "invitationRevoked", "kicked", "banned", "unbanned"].includes(change.change);
      return twoSided ? fill(membership[change.change], sender, target) : fill(membership[change.change], target);
    }
    case "displayName":
      if (!change.new) return `${sender} odstranil(a) své jméno`;
      if (!change.old) return `${shortName(change.userId)} si nastavil(a) jméno ${change.new}`;
      return `${change.old} si změnil(a) jméno na ${change.new}`;
    case "avatar":
      return `${sender} změnil(a) avatar`;
    case "roomName":
      return change.name
        ? `${sender} přejmenoval(a) konverzaci na ${change.name}`
        : `${sender} odstranil(a) název konverzace`;
    case "roomTopic":
      return change.topic ? `${sender} změnil(a) téma: ${change.topic}` : `${sender} odstranil(a) téma`;
    case "roomAvatar":
      return `${sender} změnil(a) obrázek konverzace`;
    case "roomCreated":
      return `${sender} založil(a) konverzaci`;
    case "encryptionEnabled":
      return "Šifrování konverzace je zapnuté";
    default:
      return `${sender} upravil(a) nastavení konverzace`;
  }
}

export function contentText(content: EventContent): string {
  switch (content.kind) {
    case "text":
    case "notice":
      return content.body;
    case "emote":
      return `* ${content.body}`;
    case "image":
      return content.caption ?? "📷 Obrázek";
    case "video":
      return content.caption ?? "🎬 Video";
    case "audio":
      return "🎤 Zvuková zpráva";
    case "file":
      return `📎 ${content.name}`;
    case "location":
      return "📍 Poloha";
    case "liveLocation":
      return content.isLive ? "📍 Sdílí polohu živě" : "📍 Sdílení polohy skončilo";
    case "sticker":
      return "Nálepka";
    case "poll":
      return `📊 ${content.question}`;
    case "redacted":
      return "Zpráva byla smazána";
    case "unableToDecrypt":
      return "🔒 Zprávu zatím nelze přečíst";
    default:
      return "Nepodporovaná zpráva";
  }
}

export function fileSize(bytes?: number): string {
  if (bytes === undefined) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}
