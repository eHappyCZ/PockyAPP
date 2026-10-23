<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient } from "../lib/core";
  import { errorText } from "../lib/text";
  import type { Room } from "../lib/types";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";

  let {
    client,
    rooms,
    fromRoom,
    eventIds,
    onClose,
  }: { client: PockyClient; rooms: Room[]; fromRoom: string; eventIds: string[]; onClose: () => void } = $props();

  let term = $state("");
  let sending = $state<string | null>(null);
  let sent = $state<string[]>([]);
  let error = $state("");
  let dialog: HTMLDialogElement;

  const targets = $derived.by(() => {
    const value = term.trim().toLocaleLowerCase("cs");
    return rooms.filter((room) => !room.isInvite && (!value || room.name.toLocaleLowerCase("cs").includes(value)));
  });

  onMount(() => dialog.showModal());

  async function forward(room: Room) {
    if (sending) return;
    sending = room.id;
    error = "";
    try {
      for (const eventId of eventIds) await client.forwardMessage(fromRoom, eventId, room.id);
      sent = [...sent, room.id];
    } catch (err) {
      error = errorText(err);
    } finally {
      sending = null;
    }
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    <h2>{eventIds.length > 1 ? `Přeposlat ${eventIds.length} položek` : "Přeposlat"}</h2>
    <button class="icon" onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
  </header>
  <label class="search">
    <Icon name="search" size={18} />
    <input bind:value={term} placeholder="Hledat chat" autocomplete="off" />
  </label>
  {#if error}<p class="error" role="alert">{error}</p>{/if}
  <div class="list">
    {#each targets as room (room.id)}
      <div class="row">
        <Avatar id={room.id} name={room.name} url={room.avatarUrl} size={38} square={!room.isDirect} />
        <strong>{room.name}</strong>
        {#if sent.includes(room.id)}
          <span class="done"><Icon name="check" size={16} />Posláno</span>
        {:else}
          <button onclick={() => forward(room)} disabled={sending !== null}>{sending === room.id ? "Posílám…" : "Poslat"}</button>
        {/if}
      </div>
    {:else}
      <p class="hint">Žádný chat neodpovídá hledání.</p>
    {/each}
  </div>
</dialog>

<style>
  dialog {
    width: min(440px, calc(100vw - 32px));
    max-height: min(620px, calc(100vh - 48px));
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog[open] {
    display: flex;
    flex-direction: column;
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 12px 6px 20px;
  }

  h2 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.25rem;
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

  .search {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 8px 16px;
    padding: 0 14px;
    height: 44px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--bg);
    color: var(--ink-muted);
  }

  .search input {
    flex: 1;
    height: 100%;
    border: 0;
    background: none;
    outline: none;
    color: var(--ink);
  }

  .list {
    overflow-y: auto;
    padding: 0 12px 14px;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 7px 6px;
  }

  .row strong {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .row button {
    padding: 6px 14px;
    border: 0;
    border-radius: 9px;
    background: var(--accent);
    color: #fff;
    font-weight: 600;
  }

  .row button:disabled {
    opacity: 0.5;
  }

  .done {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: var(--success);
    font-size: 0.85rem;
  }

  .hint {
    margin: 10px 8px;
    color: var(--ink-muted);
    font-size: 0.88rem;
  }

  .error {
    margin: 0 20px 6px;
    color: var(--danger);
    font-size: 0.88rem;
  }
</style>
