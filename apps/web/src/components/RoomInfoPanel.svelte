<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient } from "../lib/core";
  import { errorText, shortName } from "../lib/text";
  import type { EventEntry, Room, RoomInfo } from "../lib/types";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";
  import MediaImage from "./MediaImage.svelte";
  import RoomHistory from "./RoomHistory.svelte";

  let {
    client,
    room,
    images,
    onOpenImage,
    onLeft,
    onClose,
  }: {
    client: PockyClient;
    room: Room;
    images: EventEntry[];
    onOpenImage: (itemKey: string) => void;
    onLeft: () => void;
    onClose: () => void;
  } = $props();

  let info = $state<RoomInfo | null>(null);
  let failed = $state(false);
  let busy = $state(false);
  let showHistory = $state(false);
  let error = $state("");
  let confirm: HTMLDialogElement;

  const members = $derived(
    [...(info?.members ?? [])].sort((a, b) => rank(a.role) - rank(b.role) || Number(a.invited) - Number(b.invited)),
  );
  const joined = $derived(members.filter((member) => !member.invited).length);
  const photos = $derived(images.slice(-9).reverse());

  function rank(role: string): number {
    return role === "admin" ? 0 : role === "moderator" ? 1 : 2;
  }

  function membersText(count: number): string {
    if (count === 1) return "1 člen";
    if (count >= 2 && count <= 4) return `${count} členové`;
    return `${count} členů`;
  }

  onMount(() => {
    client
      .roomInfo(room.id)
      .then((value: RoomInfo) => (info = value))
      .catch(() => (failed = true));
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

  function leave() {
    confirm.close();
    act(async () => {
      await client.leaveRoom(room.id);
      onLeft();
    });
  }
</script>

<aside class="panel">
  <header>
    <strong>{room.isDirect ? "Profil" : "Informace o chatu"}</strong>
    <button class="icon" onclick={onClose} aria-label="Zavřít"><Icon name="close" /></button>
  </header>

  <div class="body">
    <div class="who">
      <Avatar id={room.id} name={room.name} url={room.avatarUrl} size={84} square={!room.isDirect} />
      <h2>{room.name}</h2>
      {#if info?.isDirect && info.directUserId}<span class="muted">{info.directUserId}</span>{/if}
      {#if info?.topic}<p class="topic">{info.topic}</p>{/if}
      {#if room.isEncrypted}<span class="lock"><Icon name="lock" size={13} />Šifrováno</span>{/if}
    </div>

    <div class="actions">
      <button onclick={() => act(() => client.setFavourite(room.id, !room.isFavourite))} disabled={busy}>
        <Icon name="pin" size={18} />{room.isFavourite ? "Odepnout" : "Připnout nahoru"}
      </button>
      <button onclick={() => act(() => client.setMuted(room.id, !room.isMuted))} disabled={busy}>
        <Icon name={room.isMuted ? "bell" : "bell-off"} size={18} />{room.isMuted ? "Zrušit ztlumení" : "Ztlumit"}
      </button>
      <button onclick={() => (showHistory = !showHistory)} aria-expanded={showHistory}>
        <Icon name="timeline" size={18} />Historie změn
      </button>
    </div>
    {#if showHistory}
      <h3>Historie změn</h3>
      <RoomHistory {client} roomId={room.id} />
    {/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}

    {#if photos.length > 0}
      <h3>Fotky</h3>
      <div class="photos">
        {#each photos as photo (photo.itemKey)}
          {#if photo.content.kind === "image"}
            <button class="photo" onclick={() => onOpenImage(photo.itemKey)}>
              <MediaImage source={photo.content.source} width={1} height={1} />
            </button>
          {/if}
        {/each}
      </div>
    {/if}

    {#if !room.isDirect}
      <h3>{info ? membersText(joined) : "Členové"}</h3>
      {#if failed}
        <p class="muted">Informace o chatu se nepodařilo načíst.</p>
      {/if}
      <ul>
        {#each members as member (member.userId)}
          <li>
            <Avatar id={member.userId} name={member.name ?? shortName(member.userId)} url={member.avatarUrl} size={36} />
            <span class="text">
              <strong>{member.name ?? shortName(member.userId)}{member.isOwn ? " (ty)" : ""}</strong>
              <small>
                {member.invited ? "Pozvánka čeká na přijetí" : member.role === "admin" ? "Správce" : member.role === "moderator" ? "Moderátor" : member.userId}
              </small>
            </span>
          </li>
        {/each}
      </ul>
    {/if}

    <button class="leave" onclick={() => confirm.showModal()} disabled={busy}>
      <Icon name="logout" size={18} />Opustit
    </button>
  </div>

  <dialog bind:this={confirm}>
    <h2>Opustit „{room.name}“?</h2>
    <p>
      {room.isDirect
        ? "Konverzace zmizí ze seznamu. Pokud ti dotyčný napíše znovu, přijde ti pozvánka."
        : "Přestaneš dostávat zprávy z této skupiny. Vrátit se půjde jen s novou pozvánkou."}
    </p>
    <div class="buttons">
      <button onclick={() => confirm.close()}>Zrušit</button>
      <button class="danger" onclick={leave}>Opustit</button>
    </div>
  </dialog>
</aside>

<style>
  .panel {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
    background: var(--bg);
    border-left: 1px solid var(--line);
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 10px 12px 18px;
    border-bottom: 1px solid var(--line);
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

  .body {
    flex: 1;
    overflow-y: auto;
    padding: 18px 16px 24px;
  }

  .who {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    text-align: center;
  }

  .who h2 {
    margin: 10px 0 0;
    font-family: var(--display);
    font-size: 1.3rem;
  }

  .topic {
    margin: 4px 0;
    white-space: pre-wrap;
  }

  .muted {
    color: var(--ink-muted);
    font-size: 0.85rem;
  }

  .lock {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-top: 4px;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--success);
    border: 1px solid color-mix(in srgb, var(--success) 40%, transparent);
    font-size: 0.78rem;
  }

  .actions {
    display: grid;
    gap: 6px;
    margin-top: 18px;
  }

  .actions button,
  .leave {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 11px 14px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--surface);
    font-weight: 600;
    text-align: left;
  }

  .leave {
    width: 100%;
    margin-top: 20px;
    color: #f87171;
  }

  .error {
    color: #f87171;
    font-size: 0.85rem;
  }

  h3 {
    margin: 20px 0 8px;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .photos {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 4px;
  }

  .photo {
    padding: 0;
    border: 0;
    background: none;
    aspect-ratio: 1;
    overflow: hidden;
    border-radius: 8px;
  }

  .photo :global(.media) {
    width: 100% !important;
    height: 100%;
    border-radius: 0;
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
    padding: 6px 0;
  }

  .text {
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .text small {
    color: var(--ink-muted);
    overflow-wrap: anywhere;
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
  }

  dialog p {
    margin: 0 0 18px;
    color: var(--ink-muted);
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

  button:disabled {
    opacity: 0.5;
  }
</style>
