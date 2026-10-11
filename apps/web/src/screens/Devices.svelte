<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "../components/Icon.svelte";
  import type { PockyClient } from "../lib/core";
  import type { Alerts } from "../lib/alerts.svelte";
  import type { PresenceTracker } from "../lib/presence.svelte";
  import { formatTimestamp } from "../lib/text";
  import type { Device } from "../lib/types";

  let {
    client,
    tracker,
    alerts,
    onBack,
  }: { client: PockyClient; tracker: PresenceTracker; alerts: Alerts; onBack: () => void } = $props();

  function toggle(key: "sharePresence" | "shareTyping") {
    tracker.setPrivacy({ ...tracker.privacy, [key]: !tracker.privacy[key] }).catch(() => undefined);
  }

  let devices = $state<Device[] | null>(null);
  let failed = $state(false);
  let target = $state<Device | null>(null);
  let password = $state("");
  let busy = $state(false);
  let error = $state("");
  let dialog: HTMLDialogElement;

  const current = $derived(devices?.find((device) => device.isCurrent) ?? null);
  const others = $derived(devices?.filter((device) => !device.isCurrent) ?? []);

  async function load() {
    try {
      devices = await client.listDevices();
      failed = false;
    } catch {
      failed = true;
    }
  }

  onMount(load);

  function ask(device: Device) {
    target = device;
    password = "";
    error = "";
    dialog.showModal();
  }

  async function signOut(event: SubmitEvent) {
    event.preventDefault();
    if (!target || !password || busy) return;
    busy = true;
    error = "";
    try {
      await client.signOutDevice(target.deviceId, password);
      dialog.close();
      await load();
    } catch (err) {
      error = err instanceof Error && err.name === "invalid_credentials" ? "Špatné heslo." : "Něco se pokazilo. Zkus to znovu.";
    } finally {
      busy = false;
      password = "";
    }
  }

  function seen(device: Device): string {
    return device.lastSeenMs ? `Naposledy aktivní: ${formatTimestamp(device.lastSeenMs)}` : "";
  }
</script>

<section class="devices">
  <header>
    <button class="back" onclick={onBack} aria-label="Zpět"><Icon name="arrow-back" /></button>
    <h1>Zařízení a soukromí</h1>
  </header>

  <div class="body">
    {#if failed}
      <p class="muted">Seznam zařízení se nepodařilo načíst.</p>
    {:else if devices === null}
      <p class="muted">Načítám…</p>
    {:else}
      {#if current}
        <h2>Toto zařízení</h2>
        <div class="device">
          <Icon name="device" />
          <div class="info">
            <strong>{current.name ?? current.deviceId}</strong>
            <span>Aktivní teď</span>
          </div>
          <span class="badge" class:ok={current.verified}>{current.verified ? "Ověřené" : "Neověřené"}</span>
        </div>
      {/if}

      <h2>Další zařízení</h2>
      {#each others as device (device.deviceId)}
        <div class="device">
          <Icon name="device" />
          <div class="info">
            <strong>{device.name ?? device.deviceId}</strong>
            <span>{seen(device)}</span>
          </div>
          <span class="badge" class:ok={device.verified}>{device.verified ? "Ověřené" : "Neověřené"}</span>
          <button class="out" onclick={() => ask(device)}>Odhlásit</button>
        </div>
      {:else}
        <p class="muted">Žádné další zařízení.</p>
      {/each}

      <p class="note">Zařízení, které nepoznáváš nebo už nepoužíváš, raději odhlas. Neověřená zařízení nemusí vidět starší šifrované zprávy.</p>
    {/if}

    <h2>Soukromí v tomto prohlížeči</h2>
    <label class="switch">
      <span>
        <strong>Ukazovat, že jsem online</strong>
        <small>Ostatní uvidí zelený kroužek, když máš PockyAPP otevřené.</small>
      </span>
      <input type="checkbox" checked={tracker.privacy.sharePresence} onchange={() => toggle("sharePresence")} />
    </label>
    <label class="switch">
      <span>
        <strong>Ukazovat, že píšu</strong>
        <small>Ostatní uvidí „píše…“. Když to vypneš, neuvidíš to ani ty u ostatních.</small>
      </span>
      <input type="checkbox" checked={tracker.privacy.shareTyping} onchange={() => toggle("shareTyping")} />
    </label>

    <h2>Upozornění v tomto prohlížeči</h2>
    {#if !alerts.supported}
      <p class="muted">Tento prohlížeč upozornění neumí.</p>
    {:else}
      <label class="switch">
        <span>
          <strong>Upozornit na novou zprávu</strong>
          <small>
            {alerts.permission === "denied"
              ? "Prohlížeč upozornění zakázal. Povol je v nastavení prohlížeče pro tuto stránku."
              : "Jen když je PockyAPP otevřené v některé kartě. Nic neprochází přes servery Googlu ani Apple."}
          </small>
        </span>
        <input
          type="checkbox"
          checked={alerts.settings.enabled}
          disabled={alerts.permission === "denied"}
          onchange={(e) => alerts.enable(e.currentTarget.checked)}
        />
      </label>
      <label class="switch">
        <span>
          <strong>Ukazovat text zprávy</strong>
          <small>Vypnuté ukáže jen „Nová zpráva“. Text by jinak byl vidět v oznámeních systému.</small>
        </span>
        <input
          type="checkbox"
          checked={alerts.settings.showText}
          disabled={!alerts.settings.enabled}
          onchange={(e) => alerts.setShowText(e.currentTarget.checked)}
        />
      </label>
    {/if}
  </div>

  <dialog bind:this={dialog}>
    <form onsubmit={signOut}>
      <h2>Odhlásit „{target?.name ?? target?.deviceId}“?</h2>
      <p>Zařízení přestane dostávat zprávy. Kvůli bezpečnosti zadej své heslo.</p>
      <input type="password" bind:value={password} autocomplete="current-password" placeholder="Heslo" disabled={busy} />
      {#if error}<p class="error" role="alert">{error}</p>{/if}
      <div class="buttons">
        <button type="button" onclick={() => dialog.close()}>Zrušit</button>
        <button type="submit" class="danger" disabled={!password || busy}>Odhlásit</button>
      </div>
    </form>
  </dialog>
</section>

<style>
  .devices {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    border-bottom: 1px solid var(--line);
  }

  h1 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.3rem;
  }

  .back {
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    border: 0;
    border-radius: 10px;
    background: none;
  }

  .body {
    flex: 1;
    overflow-y: auto;
    padding: 8px 20px 24px;
    max-width: 640px;
  }

  h2 {
    margin: 20px 0 8px;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .device {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    margin-bottom: 8px;
    border-radius: 14px;
    background: var(--surface);
    border: 1px solid var(--line);
    color: var(--ink-muted);
  }

  .info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .info strong {
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .info span {
    font-size: 0.82rem;
  }

  .badge {
    padding: 2px 9px;
    border-radius: 999px;
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--gold);
    border: 1px solid color-mix(in srgb, var(--gold) 45%, transparent);
  }

  .badge.ok {
    color: var(--success);
    border-color: color-mix(in srgb, var(--success) 45%, transparent);
  }

  .out {
    padding: 7px 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
    color: #f87171;
  }

  .switch {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 14px;
    margin-bottom: 8px;
    border-radius: 14px;
    background: var(--surface);
    border: 1px solid var(--line);
    cursor: pointer;
  }

  .switch span {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .switch small {
    color: var(--ink-muted);
    font-size: 0.82rem;
  }

  .switch input {
    appearance: none;
    flex: none;
    width: 44px;
    height: 26px;
    border-radius: 13px;
    background: color-mix(in srgb, var(--ink-muted) 35%, transparent);
    position: relative;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .switch input::after {
    content: "";
    position: absolute;
    top: 3px;
    left: 3px;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #fff;
    transition: transform 0.15s ease;
  }

  .switch input:checked {
    background: var(--accent);
  }

  .switch input:checked::after {
    transform: translateX(18px);
  }

  .muted,
  .note {
    color: var(--ink-muted);
    font-size: 0.88rem;
  }

  .note {
    margin-top: 18px;
  }

  dialog {
    width: min(400px, calc(100vw - 32px));
    padding: 22px;
    border: 1px solid var(--line);
    border-radius: 18px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  dialog h2 {
    margin: 0 0 6px;
    font-family: var(--display);
    font-size: 1.2rem;
    color: var(--ink);
  }

  dialog p {
    margin: 0 0 14px;
    color: var(--ink-muted);
  }

  dialog input {
    width: 100%;
    height: 44px;
    padding: 0 12px;
    margin-bottom: 14px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: var(--bg);
    outline: none;
  }

  .error {
    color: #f87171 !important;
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .buttons button {
    padding: 9px 16px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .buttons .danger {
    border-color: transparent;
    background: #dc2626;
    color: #fff;
  }

  .buttons button:disabled {
    opacity: 0.5;
  }
</style>
