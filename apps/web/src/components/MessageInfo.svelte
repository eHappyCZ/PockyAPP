<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient } from "../lib/core";
  import { fullTime, shieldText, shortName } from "../lib/text";
  import type { EventEntry, Member, RoomInfo } from "../lib/types";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";

  let {
    client,
    roomId,
    event,
    encrypted,
    onClose,
  }: { client: PockyClient; roomId: string; event: EventEntry; encrypted: boolean; onClose: () => void } = $props();

  let members = $state<Record<string, Member>>({});
  let dialog: HTMLDialogElement;

  const sender = $derived(event.senderName ?? shortName(event.senderId));
  const readers = $derived(
    [...event.readBy]
      .filter((receipt) => receipt.userId !== event.senderId)
      .sort((a, b) => (b.timestampMs ?? 0) - (a.timestampMs ?? 0)),
  );

  onMount(() => {
    dialog.showModal();
    client
      .roomInfo(roomId)
      .then((info: RoomInfo) => (members = Object.fromEntries(info.members.map((member) => [member.userId, member]))))
      .catch(() => undefined);
  });

  function nameOf(userId: string): string {
    return members[userId]?.name ?? shortName(userId);
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    <h2>Informace o zprávě</h2>
    <button class="icon" onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
  </header>

  <dl>
    <dt>Odesílatel</dt>
    <dd>{sender} <small>{event.senderId}</small></dd>
    <dt>Odesláno</dt>
    <dd>{fullTime(event.timestampMs)}{event.isEdited ? " · upraveno" : ""}</dd>
    <dt>Šifrování</dt>
    <dd>
      {#if event.shield}
        <span class="state" class:danger={event.shield.danger}><Icon name="shield-alert" size={16} />{shieldText(event.shield)}</span>
      {:else if encrypted}
        <span class="state ok"><Icon name="shield-check" size={16} />Šifrovaná zpráva od ověřeného odesílatele</span>
      {:else}
        <span class="state">Bez šifrování</span>
      {/if}
    </dd>
  </dl>

  <h3>Přečetli</h3>
  {#if readers.length === 0}
    <p class="muted">Zatím nikdo, nebo to druhá strana nesdílí.</p>
  {:else}
    <ul>
      {#each readers as receipt (receipt.userId)}
        <li>
          <Avatar id={receipt.userId} name={nameOf(receipt.userId)} url={members[receipt.userId]?.avatarUrl} size={32} />
          <span>{nameOf(receipt.userId)}</span>
          {#if receipt.timestampMs}<small>{fullTime(receipt.timestampMs)}</small>{/if}
        </li>
      {/each}
    </ul>
  {/if}
</dialog>

<style>
  dialog {
    width: min(440px, calc(100vw - 32px));
    max-height: min(620px, calc(100vh - 48px));
    padding: 14px 20px 20px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
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

  dl {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 8px 14px;
    margin: 14px 0 0;
  }

  dt {
    color: var(--ink-muted);
    font-size: 0.85rem;
  }

  dd {
    margin: 0;
    overflow-wrap: anywhere;
  }

  dd small {
    display: block;
    color: var(--ink-muted);
  }

  .state {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--gold);
  }

  .state.ok {
    color: var(--success);
  }

  .state.danger {
    color: var(--danger);
  }

  h3 {
    margin: 20px 0 8px;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .muted {
    margin: 0;
    color: var(--ink-muted);
    font-size: 0.88rem;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 5px 0;
  }

  li span {
    flex: 1;
  }

  li small {
    color: var(--ink-muted);
    font-size: 0.8rem;
  }
</style>
