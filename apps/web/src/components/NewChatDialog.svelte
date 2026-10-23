<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient } from "../lib/core";
  import { errorText, shortName } from "../lib/text";
  import type { Person, Room } from "../lib/types";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";
  import PeoplePicker from "./PeoplePicker.svelte";

  let {
    client,
    rooms,
    onOpen,
    onClose,
  }: { client: PockyClient; rooms: Room[]; onOpen: (roomId: string) => void; onClose: () => void } = $props();

  let term = $state("");
  let people = $state<Person[]>([]);
  let searching = $state(false);
  let searched = $state(false);
  let error = $state("");
  let opening = $state<string | null>(null);
  let grouping = $state(false);
  let groupName = $state("");
  let members = $state<Person[]>([]);
  let creating = $state(false);
  let dialog: HTMLDialogElement;
  let input = $state<HTMLInputElement>();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let query = 0;

  const recent = $derived(rooms.filter((room) => room.isDirect && !room.isInvite).slice(0, 6));

  onMount(() => {
    dialog.showModal();
    input?.focus();
  });

  function search() {
    clearTimeout(timer);
    const value = term.trim();
    searched = false;
    if (value.length < 2) {
      people = [];
      return;
    }
    timer = setTimeout(async () => {
      const id = ++query;
      searching = true;
      try {
        const found: Person[] = await client.searchPeople(value);
        if (id === query) {
          people = found;
          searched = true;
        }
      } catch {
        if (id === query) people = [];
      } finally {
        if (id === query) searching = false;
      }
    }, 300);
  }

  async function open(userId: string) {
    opening = userId;
    error = "";
    try {
      onOpen(await client.openDirectChat(userId));
      close();
    } catch (err) {
      error = errorText(err);
    } finally {
      opening = null;
    }
  }

  async function createGroup(event: SubmitEvent) {
    event.preventDefault();
    if (!groupName.trim() || creating) return;
    creating = true;
    error = "";
    try {
      const roomId = await client.createGroup(
        groupName,
        members.map((person) => person.userId),
      );
      onOpen(roomId);
      close();
    } catch (err) {
      error = errorText(err);
    } finally {
      creating = false;
    }
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    {#if grouping}
      <button class="icon" onclick={() => { grouping = false; error = ""; }} aria-label="Zpět"><Icon name="arrow-back" /></button>
    {/if}
    <h2>{grouping ? "Nová skupina" : "Nový chat"}</h2>
    <button class="icon" onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
  </header>
  {#if grouping}
    <form class="group" onsubmit={createGroup}>
      <label class="field">
        <span>Název skupiny</span>
        <!-- svelte-ignore a11y_autofocus -->
        <input bind:value={groupName} maxlength="64" placeholder="Třeba Výlet na Šumavu" autocomplete="off" autofocus />
      </label>
      <h3>Pozvat lidi {members.length > 0 ? `(${members.length})` : ""}</h3>
      <PeoplePicker {client} bind:selected={members} />
      {#if error}<p class="error" role="alert">{error}</p>{/if}
      <footer>
        <button type="submit" class="primary" disabled={!groupName.trim() || creating}>
          {creating ? "Zakládám…" : "Založit skupinu"}
        </button>
      </footer>
    </form>
  {:else}
    <label class="search">
      <Icon name="search" size={18} />
      <input bind:this={input} bind:value={term} oninput={search} placeholder="Jméno nebo @uživatel:server" autocomplete="off" spellcheck="false" />
    </label>
    {#if error}<p class="error" role="alert">{error}</p>{/if}

    <div class="list">
      {#if term.trim().length < 2}
        <button class="person" onclick={() => { grouping = true; error = ""; }}>
          <span class="group-icon"><Icon name="group" /></span>
          <span><strong>Nová skupina</strong><small>Pozvi víc lidí najednou</small></span>
        </button>
        <p class="hint">Napiš jméno, přezdívku nebo uživatelské jméno, třeba „karel“.</p>
        {#if recent.length > 0}
          <h3>Nedávné konverzace</h3>
          {#each recent as room (room.id)}
            <button class="person" onclick={() => { onOpen(room.id); close(); }}>
              <Avatar id={room.id} name={room.name} url={room.avatarUrl} size={40} />
              <span><strong>{room.name}</strong></span>
            </button>
          {/each}
        {/if}
      {:else}
        <h3>Lidé</h3>
        {#each people as person (person.userId)}
          <button class="person" onclick={() => open(person.userId)} disabled={opening !== null}>
            <Avatar id={person.userId} name={person.displayName ?? shortName(person.userId)} url={person.avatarUrl} size={40} />
            <span>
              <strong>{person.displayName ?? shortName(person.userId)}</strong>
              <small>{person.userId}</small>
            </span>
            {#if opening === person.userId}<Icon name="clock" size={18} />{/if}
          </button>
        {:else}
          <p class="hint">{searching || !searched ? "Hledám…" : "Nikoho jsme nenašli. Zkus přesné uživatelské jméno."}</p>
        {/each}
      {/if}
    </div>
  {/if}
</dialog>

<style>
  dialog {
    width: min(460px, calc(100vw - 32px));
    max-height: min(640px, calc(100vh - 48px));
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
    gap: 4px;
    padding: 14px 12px 6px 20px;
  }

  header:has(.icon:first-child) {
    padding-left: 8px;
  }

  h2 {
    flex: 1;
    margin: 0;
    font-family: var(--display);
    font-size: 1.3rem;
  }

  .group {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 8px 16px 0;
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

  footer {
    padding: 8px 16px 16px;
  }

  .primary {
    width: 100%;
    height: 44px;
    border: 0;
    border-radius: 12px;
    background: var(--accent);
    color: #fff;
    font-weight: 700;
  }

  .primary:disabled {
    opacity: 0.5;
  }

  .group-icon {
    flex: none;
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border-radius: 30%;
    background: color-mix(in srgb, var(--accent) 22%, transparent);
    color: var(--accent-soft);
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
    height: 46px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--bg);
    color: var(--ink-muted);
  }

  .search:focus-within {
    border-color: var(--accent);
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
    padding: 0 8px 14px;
  }

  h3 {
    margin: 12px 12px 6px;
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .hint {
    margin: 10px 12px;
    font-size: 0.88rem;
    color: var(--ink-muted);
  }

  .error {
    margin: 0 18px;
    color: #f87171;
    font-size: 0.88rem;
  }

  .person {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border: 0;
    border-radius: 12px;
    background: none;
    text-align: left;
  }

  .person:hover {
    background: color-mix(in srgb, var(--ink) 6%, transparent);
  }

  .person span:not(.group-icon) {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .person small {
    color: var(--ink-muted);
    overflow-wrap: anywhere;
  }
</style>
