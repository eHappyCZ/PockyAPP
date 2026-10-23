<script lang="ts">
  import type { PockyClient } from "../lib/core";
  import { errorText } from "../lib/text";
  import type { Encryption } from "../lib/types";
  import Icon from "./Icon.svelte";
  import RecoveryDialog from "./RecoveryDialog.svelte";

  let {
    client,
    encryption,
    onDeleted,
  }: { client: PockyClient; encryption: Encryption | null; onDeleted: () => void } = $props();

  let recovery = $state<"enable" | "change" | null>(null);
  let recoveryChanged = $state(false);
  let passwordDialog: HTMLDialogElement;
  let deleteDialog: HTMLDialogElement;

  let current = $state("");
  let next = $state("");
  let repeat = $state("");
  let signOutOthers = $state(true);
  let passwordDone = $state(false);

  let deletePassword = $state("");
  let confirmText = $state("");

  let busy = $state(false);
  let error = $state("");

  const recoveryOn = $derived(recoveryChanged || (encryption?.recoveryEnabled ?? false));

  function openPassword() {
    current = next = repeat = "";
    passwordDone = false;
    error = "";
    passwordDialog.showModal();
  }

  function openDelete() {
    deletePassword = confirmText = "";
    error = "";
    deleteDialog.showModal();
  }

  async function changePassword(event: SubmitEvent) {
    event.preventDefault();
    if (busy || !current || next.length < 8 || next !== repeat) return;
    busy = true;
    error = "";
    try {
      await client.changePassword(current, next, signOutOthers);
      passwordDone = true;
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
      current = next = repeat = "";
    }
  }

  async function deleteAccount(event: SubmitEvent) {
    event.preventDefault();
    if (busy || !deletePassword || confirmText.trim().toUpperCase() !== "SMAZAT") return;
    busy = true;
    error = "";
    try {
      await client.deleteAccount(deletePassword);
      deleteDialog.close();
      onDeleted();
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
      deletePassword = "";
    }
  }
</script>

<h2>Zabezpečení účtu</h2>

<div class="row">
  <Icon name="lock" />
  <span class="text">
    <strong>Záloha šifrovacích klíčů</strong>
    <small>
      {recoveryOn
        ? "Zapnutá. Na novém zařízení odemkneš zprávy bezpečnostní frází."
        : "Vypnutá. Když přijdeš o všechna zařízení, starší zprávy nepůjde přečíst."}
    </small>
  </span>
  {#if encryption}
    <button onclick={() => (recovery = recoveryOn ? "change" : "enable")}>{recoveryOn ? "Změnit frázi" : "Zapnout"}</button>
  {/if}
</div>

<div class="row">
  <Icon name="fingerprint" />
  <span class="text">
    <strong>Heslo k účtu</strong>
    <small>Po změně se můžeš odhlásit ze všech ostatních zařízení.</small>
  </span>
  <button onclick={openPassword}>Změnit</button>
</div>

<div class="row danger">
  <Icon name="trash" />
  <span class="text">
    <strong>Smazat účet</strong>
    <small>Účet se natrvalo zruší a uživatelské jméno už nepůjde použít.</small>
  </span>
  <button onclick={openDelete}>Smazat</button>
</div>

{#if recovery}
  <RecoveryDialog {client} mode={recovery} onDone={() => (recoveryChanged = true)} onClose={() => (recovery = null)} />
{/if}

<dialog bind:this={passwordDialog}>
  {#if passwordDone}
    <h3>Heslo je změněné</h3>
    <p>{signOutOthers ? "Ostatní zařízení byla odhlášena." : "Ostatní zařízení zůstala přihlášená."}</p>
    <div class="buttons"><button class="primary" onclick={() => passwordDialog.close()}>Zavřít</button></div>
  {:else}
    <form onsubmit={changePassword}>
      <h3>Změnit heslo</h3>
      <label><span>Současné heslo</span><input type="password" bind:value={current} autocomplete="current-password" disabled={busy} /></label>
      <label><span>Nové heslo</span><input type="password" bind:value={next} autocomplete="new-password" disabled={busy} /></label>
      <label><span>Nové heslo znovu</span><input type="password" bind:value={repeat} autocomplete="new-password" disabled={busy} /></label>
      <label class="check">
        <input type="checkbox" bind:checked={signOutOthers} disabled={busy} />
        Odhlásit ostatní zařízení
      </label>
      {#if next && next.length < 8}<p class="error">Heslo musí mít aspoň 8 znaků.</p>{/if}
      {#if repeat && repeat !== next}<p class="error">Hesla se neshodují.</p>{/if}
      {#if error}<p class="error" role="alert">{error}</p>{/if}
      <div class="buttons">
        <button type="button" onclick={() => passwordDialog.close()} disabled={busy}>Zrušit</button>
        <button type="submit" class="primary" disabled={busy || !current || next.length < 8 || next !== repeat}>Změnit heslo</button>
      </div>
    </form>
  {/if}
</dialog>

<dialog bind:this={deleteDialog}>
  <form onsubmit={deleteAccount}>
    <h3>Smazat účet natrvalo?</h3>
    <p>
      Zprávy, které jsi poslal(a), zůstanou ostatním vidět. Účet ani uživatelské jméno už nepůjde obnovit. Pro potvrzení
      napiš <strong>SMAZAT</strong> a zadej heslo.
    </p>
    <label><span>Potvrzení</span><input bind:value={confirmText} autocomplete="off" disabled={busy} /></label>
    <label><span>Heslo</span><input type="password" bind:value={deletePassword} autocomplete="current-password" disabled={busy} /></label>
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    <div class="buttons">
      <button type="button" onclick={() => deleteDialog.close()} disabled={busy}>Zrušit</button>
      <button type="submit" class="danger" disabled={busy || !deletePassword || confirmText.trim().toUpperCase() !== "SMAZAT"}>
        {busy ? "Mažu…" : "Smazat účet"}
      </button>
    </div>
  </form>
</dialog>

<style>
  h2 {
    margin: 20px 0 8px;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .row {
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

  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .text strong {
    color: var(--ink);
  }

  .text small {
    font-size: 0.82rem;
  }

  .row > button {
    flex: none;
    padding: 7px 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .row.danger > button {
    color: #f87171;
  }

  dialog {
    width: min(420px, calc(100vw - 32px));
    padding: 22px;
    border: 1px solid var(--line);
    border-radius: 18px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  h3 {
    margin: 0 0 12px;
    font-family: var(--display);
    font-size: 1.2rem;
  }

  p {
    margin: 0 0 14px;
    color: var(--ink-muted);
    font-size: 0.9rem;
  }

  p strong {
    color: var(--ink);
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 5px;
    margin-bottom: 12px;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  label input:not([type="checkbox"]) {
    height: 44px;
    padding: 0 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: var(--bg);
    outline: none;
  }

  label input:focus {
    border-color: var(--accent);
  }

  .check {
    flex-direction: row;
    align-items: center;
    gap: 8px;
    color: var(--ink);
    font-size: 0.9rem;
  }

  .check input {
    accent-color: var(--accent);
  }

  .error {
    margin-top: -6px;
    color: #f87171;
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

  .buttons .primary {
    border-color: transparent;
    background: var(--accent);
    color: #fff;
  }

  .buttons .danger {
    border-color: transparent;
    background: #dc2626;
    color: #fff;
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
