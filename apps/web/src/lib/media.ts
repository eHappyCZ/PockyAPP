import type { PockyClient } from "./core";

let current: PockyClient | null = null;
const cache = new Map<string, Promise<string>>();

function sniff(bytes: Uint8Array): string {
  const ascii = (from: number, length: number) => String.fromCharCode(...bytes.subarray(from, from + length));
  if (ascii(0, 4) === "OggS") return "audio/ogg";
  if (ascii(4, 4) === "ftyp") return "video/mp4";
  if (ascii(0, 4) === "\x1aE\xdf\xa3") return "video/webm";
  if (bytes[0] === 0xff && bytes[1] === 0xd8) return "image/jpeg";
  if (ascii(1, 3) === "PNG") return "image/png";
  if (ascii(0, 3) === "GIF") return "image/gif";
  if (ascii(0, 4) === "RIFF" && ascii(8, 4) === "WEBP") return "image/webp";
  return "";
}

export function useClient(client: PockyClient | null): void {
  current = client;
  for (const url of cache.values()) url.then(URL.revokeObjectURL, () => undefined);
  cache.clear();
}

export function mediaUrl(source: string, width = 0, height = 0, type = ""): Promise<string> {
  const key = `${source}|${width}x${height}|${type}`;
  let url = cache.get(key);
  if (!url) {
    const client = current;
    if (!client) return Promise.reject(new Error("no client"));
    url = client
      .loadMedia(source, width, height)
      .then((bytes) => {
        const kind = type || sniff(bytes);
        return URL.createObjectURL(new Blob([bytes as Uint8Array<ArrayBuffer>], kind ? { type: kind } : {}));
      });
    url.catch(() => cache.delete(key));
    cache.set(key, url);
  }
  return url;
}

export async function download(source: string, name: string, type = "") {
  const url = await mediaUrl(source, 0, 0, type);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
}
