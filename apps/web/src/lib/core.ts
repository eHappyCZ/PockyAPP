import init, * as core from "../core/pocky.js";

export type { PockyClient, RoomTimeline, Verification } from "../core/pocky.js";

let ready: Promise<typeof core> | null = null;

export function loadCore(): Promise<typeof core> {
  ready ??= init().then(() => core);
  return ready;
}

export async function passphraseStrength(passphrase: string) {
  const core = await loadCore();
  return core.passphraseStrength(passphrase) as import("./types").PassphraseStrength;
}

export async function parseGeoUri(uri: string) {
  const core = await loadCore();
  return core.parseGeoUri(uri) as import("./types").GeoPoint | undefined;
}
