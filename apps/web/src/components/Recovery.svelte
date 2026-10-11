<script lang="ts">
  import type { PockyClient } from "../lib/core";
  import { errorText } from "../lib/text";
  import Icon from "./Icon.svelte";

  let {
    client,
    canRecover,
    canVerify,
    onDone,
    onVerify,
  }: {
    client: PockyClient;
    canRecover: boolean;
    canVerify: boolean;
    onDone: () => void;
    onVerify: () => void;
  } = $props();

  let key = $state("");
  let busy = $state(false);
  let error = $state("");

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (!key.trim() || busy) return;

    busy = true;
    error = "";
    try {
      await client.recover(key);
      key = "";
      onDone();
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
    }
  }
</script>

<form class="recovery" onsubmit={submit}>
  <div class="head">
    <Icon name="lock" size={18} />
    <strong>Odemkni starší zprávy</strong>
  </div>
  <p>
    {canRecover ? "Zadej klíč pro obnovení nebo bezpečnostní frázi, nebo potvrď přihlášení z jiného zařízení." : "Potvrď přihlášení z jiného zařízení porovnáním emoji."}
    Prohlížeč pak získá přístup k šifrovaným zprávám.
  </p>
  {#if canRecover}
  <div class="field">
    <input
      type="password"
      bind:value={key}
      placeholder="Klíč pro obnovení nebo fráze"
      autocomplete="off"
      spellcheck="false"
      disabled={busy}
    />
    <button type="submit" disabled={!key.trim() || busy}>{busy ? "Ověřuji…" : "Ověřit"}</button>
  </div>
  {/if}
  {#if canVerify}
    <button type="button" class="device" onclick={onVerify}>Ověřit jiným zařízením</button>
  {/if}
  {#if error}<p class="error" role="alert">{error}</p>{/if}
</form>

<style>
  .recovery {
    margin: 0 12px 10px;
    padding: 14px;
    border-radius: 14px;
    border: 1px solid color-mix(in srgb, var(--gold) 35%, transparent);
    background: color-mix(in srgb, var(--gold) 7%, var(--surface));
  }

  .head {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--gold);
  }

  .head strong {
    color: var(--ink);
  }

  p {
    margin: 6px 0 10px;
    font-size: 0.85rem;
    color: var(--ink-muted);
  }

  .field {
    display: flex;
    gap: 8px;
  }

  input {
    flex: 1;
    min-width: 0;
    height: 40px;
    padding: 0 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: var(--bg);
    outline: none;
  }

  input:focus {
    border-color: var(--accent);
  }

  button {
    height: 40px;
    padding: 0 14px;
    border: 0;
    border-radius: 10px;
    background: var(--accent);
    color: #fff;
    font-weight: 700;
  }

  button:disabled {
    opacity: 0.5;
  }

  .device {
    width: 100%;
    margin-top: 8px;
    background: none;
    border: 1px solid var(--line);
    color: var(--ink);
  }

  .error {
    margin: 8px 0 0;
    color: #f87171;
  }
</style>
