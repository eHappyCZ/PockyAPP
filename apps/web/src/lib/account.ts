import { loadCore, type PockyClient } from "./core";
import * as vault from "./vault";

function random(bytes: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(bytes));
}

function hex(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function deviceName(): string {
  const agent = navigator.userAgent;
  const browser = /Firefox\//.test(agent)
    ? "Firefox"
    : /Edg\//.test(agent)
      ? "Edge"
      : /Chrome\//.test(agent)
        ? "Chrome"
        : /Safari\//.test(agent)
          ? "Safari"
          : "prohlížeč";
  return `PockyAPP web · ${browser}`;
}

async function dropStores(keep?: string): Promise<void> {
  const databases = await indexedDB.databases().catch(() => []);
  for (const { name } of databases) {
    if (!name?.startsWith("pocky-") || name === "pocky-vault") continue;
    if (keep && (name === keep || name.startsWith(`${keep}::`))) continue;
    indexedDB.deleteDatabase(name);
  }
}

let pending: { client: PockyClient; store: string; passphrase: string; server: string } | null = null;

async function pendingClient(server: string) {
  if (pending && pending.server !== server) {
    pending.client.free();
    pending = null;
  }
  if (!pending) {
    const core = await loadCore();
    const store = `pocky-${hex(random(8))}`;
    const passphrase = hex(random(32));
    pending = { client: await core.createClient(store, passphrase, server), store, passphrase, server };
  }
  return pending;
}

export async function signIn(username: string, password: string, server: string): Promise<PockyClient> {
  const { client, store, passphrase } = await pendingClient(server);
  const session = await client.login(username, password, deviceName());
  await vault.save({ session, store, passphrase });
  pending = null;
  return client;
}

export async function signUp(
  username: string,
  password: string,
  inviteCode: string,
  server: string,
): Promise<PockyClient> {
  const { client, store, passphrase } = await pendingClient(server);
  const session = await client.register(username, password, inviteCode, deviceName());
  await vault.save({ session, store, passphrase });
  pending = null;
  return client;
}

export async function resume(): Promise<PockyClient | null> {
  const secret = await vault.load();
  await dropStores(secret?.store);
  if (!secret) return null;

  const core = await loadCore();
  return core.restoreClient(secret.session, secret.store, secret.passphrase);
}

export async function refreshSession(client: PockyClient): Promise<void> {
  const secret = await vault.load();
  const session = client.exportSession();
  if (secret && session) await vault.save({ ...secret, session });
}

export async function signOut(client: PockyClient): Promise<void> {
  const timeout = new Promise((resolve) => setTimeout(resolve, 10_000));
  await Promise.race([client.logout(), timeout]).catch(() => undefined);
  await forget();
}

export async function forget(): Promise<void> {
  await vault.clear();
  await dropStores();
}
