<script lang="ts">
  import { onMount, untrack } from "svelte";
  import type { PockyClient } from "../lib/core";
  import { pickImage } from "../lib/media";
  import { errorText } from "../lib/text";
  import { theme, type ThemeMode } from "../lib/theme.svelte";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";

  let {
    client,
    name,
    avatarUrl,
    onChange,
    onClose,
  }: {
    client: PockyClient;
    name: string;
    avatarUrl?: string;
    onChange: (name: string, avatarUrl?: string) => void;
    onClose: () => void;
  } = $props();

  const modes: { value: ThemeMode; label: string }[] = [
    { value: "system", label: "Podle systému" },
    { value: "light", label: "Světlý" },
    { value: "dark", label: "Tmavý" },
  ];

  const userId = $derived(client.userId() ?? "");

  let draft = $state(untrack(() => name));
  let photo = $state(untrack(() => avatarUrl));
  let busy = $state(false);
  let error = $state("");
  let dialog: HTMLDialogElement;

  const changed = $derived(draft.trim() !== name && draft.trim().length > 0);

  let blocked = $state<string[]>([]);

  async function loadBlocked() {
    try {
      blocked = await client.blockedUsers();
    } catch {
      blocked = [];
    }
  }

  function unblock(user: string) {
    act(async () => {
      await client.unblockUser(user);
      await loadBlocked();
    });
  }

  onMount(() => {
    dialog.showModal();
    loadBlocked();
  });

  async function act(action: () => Promise<void>) {
    busy = true;
    error = "";
    try {
      await action();
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
    }
  }

  async function changePhoto() {
    const bytes = await pickImage();
    if (!bytes) return;
    await act(async () => {
      photo = await client.setAvatar(bytes);
      onChange(name, photo);
    });
  }

  function removePhoto() {
    act(async () => {
      await client.removeAvatar();
      photo = undefined;
      onChange(name, undefined);
    });
  }

  function save(event: SubmitEvent) {
    event.preventDefault();
    if (!changed || busy) return;
    act(async () => {
      const saved = await client.setDisplayName(draft);
      onChange(saved, photo);
      close();
    });
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <form onsubmit={save}>
    <header>
      <h2>Můj profil</h2>
      <button type="button" class="icon" onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
    </header>

    <div class="photo">
      <Avatar id={userId} name={draft || name} url={photo} size={96} />
      <div class="photo-actions">
        <button type="button" onclick={changePhoto} disabled={busy}>
          <Icon name="camera" size={16} />{photo ? "Změnit fotku" : "Přidat fotku"}
        </button>
        {#if photo}
          <button type="button" class="remove" onclick={removePhoto} disabled={busy}>
            <Icon name="trash" size={16} />Odebrat
          </button>
        {/if}
      </div>
    </div>

    <label class="field">
      <span>Jméno, které uvidí ostatní</span>
      <input bind:value={draft} maxlength="64" autocomplete="nickname" disabled={busy} />
    </label>
    <p class="muted">{userId}</p>

    <div class="field look">
      <span>Vzhled</span>
      <div class="modes" role="radiogroup" aria-label="Vzhled">
        {#each modes as option (option.value)}
          <button
            type="button"
            role="radio"
            aria-checked={theme.mode === option.value}
            class:active={theme.mode === option.value}
            onclick={() => theme.select(option.value)}
          >{option.label}</button>
        {/each}
      </div>
    </div>

    <div class="blocked">
      <span>Blokovaní lidé</span>
      {#if blocked.length === 0}
        <p class="muted">Nikoho jsi nezablokoval(a).</p>
      {/if}
      {#each blocked as user (user)}
        <div class="blocked-row">
          <span>{user}</span>
          <button type="button" onclick={() => unblock(user)} disabled={busy}>Odblokovat</button>
        </div>
      {/each}
    </div>

    {#if error}<p class="error" role="alert">{error}</p>{/if}

    <div class="buttons">
      <button type="button" onclick={close}>Zavřít</button>
      <button type="submit" class="primary" disabled={!changed || busy}>Uložit jméno</button>
    </div>
  </form>
</dialog>

<style>
  dialog {
    width: min(420px, calc(100vw - 32px));
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  form {
    padding: 14px 20px 20px;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-right: -8px;
  }

  h2 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.3rem;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 10px;
    background: none;
  }

  .photo {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    margin: 12px 0 18px;
  }

  .photo-actions {
    display: flex;
    gap: 8px;
  }

  .photo-actions button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 7px 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
    font-size: 0.88rem;
  }

  .photo-actions .remove {
    color: var(--danger);
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .field input {
    height: 46px;
    padding: 0 14px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--bg);
    outline: none;
    color: var(--ink);
    font-size: 1rem;
  }

  .field input:focus {
    border-color: var(--accent);
  }

  .look {
    margin-top: 18px;
  }

  .blocked {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 18px;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .blocked-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    color: var(--ink);
    font-size: 0.9rem;
  }

  .blocked-row span {
    overflow-wrap: anywhere;
  }

  .blocked-row button {
    padding: 6px 10px;
    border-radius: 8px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .modes {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
  }

  .modes button {
    padding: 9px 8px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
    font-size: 0.88rem;
  }

  .modes button.active {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .muted {
    margin: 6px 0 0;
    color: var(--ink-muted);
    font-size: 0.82rem;
    overflow-wrap: anywhere;
  }

  .error {
    margin: 12px 0 0;
    color: var(--danger);
    font-size: 0.88rem;
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 20px;
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

  button:disabled {
    opacity: 0.5;
  }
</style>
