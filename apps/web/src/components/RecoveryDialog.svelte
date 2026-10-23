<script lang="ts">
  import { onMount } from "svelte";
  import { passphraseStrength, type PockyClient } from "../lib/core";
  import { errorText } from "../lib/text";
  import type { PassphraseStrength } from "../lib/types";
  import Icon from "./Icon.svelte";

  let {
    client,
    mode,
    onDone,
    onClose,
  }: {
    client: PockyClient;
    mode: "enable" | "change" | "reset";
    onDone: () => void;
    onClose: () => void;
  } = $props();

  const titles = {
    enable: "Zapnout zálohu klíčů",
    change: "Změnit bezpečnostní frázi",
    reset: "Nová šifrovací identita",
  };

  const strengths: Record<PassphraseStrength, { label: string; level: number }> = {
    tooShort: { label: "Moc krátká, aspoň 8 znaků", level: 0 },
    noSymbol: { label: "Přidej číslo nebo znak", level: 1 },
    weak: { label: "Slabá", level: 1 },
    fair: { label: "Dostačující", level: 2 },
    strong: { label: "Silná", level: 3 },
  };

  let password = $state("");
  let passphrase = $state("");
  let repeat = $state("");
  let strength = $state<PassphraseStrength>("tooShort");
  let busy = $state(false);
  let error = $state("");
  let key = $state<string | null>(null);
  let copied = $state(false);
  let dialog: HTMLDialogElement;

  const needsPassword = $derived(mode !== "change");
  const acceptable = $derived(strength === "fair" || strength === "strong");
  const ready = $derived(acceptable && passphrase === repeat && (!needsPassword || password.length > 0) && !busy);

  $effect(() => {
    const value = passphrase;
    passphraseStrength(value).then((result) => {
      if (value === passphrase) strength = result;
    });
  });

  onMount(() => dialog.showModal());

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (!ready) return;
    busy = true;
    error = "";
    try {
      key =
        mode === "enable"
          ? await client.enableRecovery(password, passphrase)
          : mode === "reset"
            ? await client.resetIdentity(password, passphrase)
            : await client.changeRecoveryPassphrase(passphrase);
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
      password = "";
    }
  }

  function copy() {
    if (!key) return;
    navigator.clipboard?.writeText(key).then(() => (copied = true), () => undefined);
  }

  function close() {
    dialog.close();
    if (key) onDone();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); if (!busy) close(); }}>
  <header>
    <Icon name={mode === "reset" ? "alert" : "lock"} />
    <h2>{titles[mode]}</h2>
  </header>

  {#if key}
    <p>Hotovo. Na novém zařízení odemkneš zprávy svou bezpečnostní frází. Pro jistotu si ulož i tento klíč pro obnovení:</p>
    <code class="key">{key}</code>
    <div class="buttons">
      <button type="button" onclick={copy}><Icon name="copy" size={16} />{copied ? "Zkopírováno" : "Kopírovat"}</button>
      <button type="button" class="primary" onclick={close}>Mám uloženo</button>
    </div>
  {:else}
    <form onsubmit={submit}>
      {#if mode === "enable"}
        <p>Záloha uloží šifrovací klíče na server zamčené tvou bezpečnostní frází. Bez ní je nikdo nepřečte, ani správce serveru.</p>
      {:else if mode === "change"}
        <p>Stará fráze i starý klíč pro obnovení přestanou platit. Zálohované zprávy zůstanou dostupné.</p>
      {:else}
        <p class="warn">
          Použij jen tehdy, když nemáš klíč, frázi ani jiné přihlášené zařízení. Starší šifrované zprávy už nepůjde přečíst a
          ostatní uvidí, že se ti změnila identita.
        </p>
      {/if}

      {#if needsPassword}
        <label>
          <span>Heslo k účtu</span>
          <input type="password" bind:value={password} autocomplete="current-password" disabled={busy} />
        </label>
      {/if}
      <label>
        <span>{mode === "change" ? "Nová bezpečnostní fráze" : "Bezpečnostní fráze"}</span>
        <input type="password" bind:value={passphrase} autocomplete="new-password" disabled={busy} />
      </label>
      {#if passphrase}
        <div class="meter" data-level={strengths[strength].level}>
          <span></span><span></span><span></span>
          <small>{strengths[strength].label}</small>
        </div>
      {/if}
      <label>
        <span>Fráze znovu</span>
        <input type="password" bind:value={repeat} autocomplete="new-password" disabled={busy} />
      </label>
      {#if repeat && repeat !== passphrase}<p class="error">Fráze se neshodují.</p>{/if}
      {#if error}<p class="error" role="alert">{error}</p>{/if}

      <div class="buttons">
        <button type="button" onclick={close} disabled={busy}>Zrušit</button>
        <button type="submit" class={mode === "reset" ? "danger" : "primary"} disabled={!ready}>
          {busy ? "Chvilku…" : mode === "reset" ? "Vytvořit novou identitu" : "Uložit"}
        </button>
      </div>
    </form>
  {/if}
</dialog>

<style>
  dialog {
    width: min(440px, calc(100vw - 32px));
    padding: 22px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.65);
  }

  header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
    color: var(--accent-soft);
  }

  h2 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.25rem;
    color: var(--ink);
  }

  p {
    margin: 0 0 14px;
    color: var(--ink-muted);
    font-size: 0.9rem;
  }

  .warn {
    color: var(--gold);
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 5px;
    margin-bottom: 12px;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  input {
    height: 44px;
    padding: 0 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: var(--bg);
    outline: none;
  }

  input:focus {
    border-color: var(--accent);
  }

  .meter {
    display: flex;
    align-items: center;
    gap: 4px;
    margin: -4px 0 12px;
  }

  .meter span {
    width: 36px;
    height: 4px;
    border-radius: 2px;
    background: var(--line);
  }

  .meter small {
    margin-left: 8px;
    color: var(--ink-muted);
  }

  .meter[data-level="1"] span:nth-child(-n + 1) {
    background: var(--danger);
  }

  .meter[data-level="2"] span:nth-child(-n + 2) {
    background: var(--gold);
  }

  .meter[data-level="3"] span {
    background: var(--success);
  }

  .key {
    display: block;
    margin-bottom: 16px;
    padding: 12px;
    border-radius: 10px;
    background: var(--bg);
    border: 1px solid var(--line);
    font-size: 0.95rem;
    letter-spacing: 0.04em;
    word-spacing: 0.3em;
    user-select: all;
  }

  .error {
    margin-top: -6px;
    color: var(--danger);
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 10px 16px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .primary {
    border-color: transparent;
    background: var(--accent);
    color: #fff;
  }

  .danger {
    border-color: transparent;
    background: #dc2626;
    color: #fff;
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
