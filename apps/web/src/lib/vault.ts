const DATABASE = "pocky-vault";
const STORE = "vault";

export interface Secret {
  session: string;
  store: string;
  passphrase: string;
}

interface Sealed {
  iv: Uint8Array<ArrayBuffer>;
  data: ArrayBuffer;
}

function request<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function open(): Promise<IDBDatabase> {
  const req = indexedDB.open(DATABASE, 1);
  req.onupgradeneeded = () => req.result.createObjectStore(STORE);
  return request(req);
}

async function read<T>(db: IDBDatabase, key: string): Promise<T | undefined> {
  return request(db.transaction(STORE).objectStore(STORE).get(key));
}

async function write(db: IDBDatabase, key: string, value: unknown): Promise<void> {
  await request(db.transaction(STORE, "readwrite").objectStore(STORE).put(value, key));
}

async function sealingKey(db: IDBDatabase): Promise<CryptoKey> {
  const existing = await read<CryptoKey>(db, "key");
  if (existing) return existing;

  const key = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, [
    "encrypt",
    "decrypt",
  ]);
  await write(db, "key", key);
  return key;
}

export async function save(secret: Secret): Promise<void> {
  const db = await open();
  try {
    const key = await sealingKey(db);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plain = new TextEncoder().encode(JSON.stringify(secret));
    const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, plain);
    await write(db, "secret", { iv, data } satisfies Sealed);
  } finally {
    db.close();
  }
}

export async function load(): Promise<Secret | null> {
  const db = await open();
  try {
    const key = await read<CryptoKey>(db, "key");
    const sealed = await read<Sealed>(db, "secret");
    if (!key || !sealed) return null;

    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.iv }, key, sealed.data);
    return JSON.parse(new TextDecoder().decode(plain)) as Secret;
  } catch {
    return null;
  } finally {
    db.close();
  }
}

export async function clear(): Promise<void> {
  await request(indexedDB.deleteDatabase(DATABASE)).catch(() => undefined);
}
