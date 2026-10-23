<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient } from "../lib/core";
  import { pickImage } from "../lib/media";
  import { errorText, shortName } from "../lib/text";
  import type { EventEntry, Member, Person, Room, RoomInfo, TrustLevel } from "../lib/types";
  import Album from "./Album.svelte";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";
  import MediaImage from "./MediaImage.svelte";
  import PeoplePicker from "./PeoplePicker.svelte";
  import RoomHistory from "./RoomHistory.svelte";

  let {
    client,
    room,
    rooms,
    images,
    onOpenImage,
    onLeft,
    onVerify,
    onClose,
  }: {
    client: PockyClient;
    room: Room;
    rooms: Room[];
    images: EventEntry[];
    onOpenImage: (itemKey: string) => void;
    onLeft: () => void;
    onVerify: (userId: string) => void;
    onClose: () => void;
  } = $props();

  let trust = $state<TrustLevel | null>(null);
  let showAlbum = $state(false);

  let info = $state<RoomInfo | null>(null);
  let failed = $state(false);
  let busy = $state(false);
  let showHistory = $state(false);
  let error = $state("");
  let editing = $state(false);
  let draftName = $state("");
  let draftTopic = $state("");
  let invitees = $state<Person[]>([]);
  let removing = $state<Member | null>(null);
  let confirm: HTMLDialogElement;
  let inviteDialog: HTMLDialogElement;
  let removeDialog: HTMLDialogElement;

  const members = $derived(
    [...(info?.members ?? [])].sort((a, b) => rank(a.role) - rank(b.role) || Number(a.invited) - Number(b.invited)),
  );
  const joined = $derived(members.filter((member) => !member.invited).length);
  const photos = $derived(images.slice(-9).reverse());
  const canEdit = $derived(!room.isDirect && !!info && (info.canEditName || info.canEditTopic || info.canEditAvatar));

  function rank(role: string): number {
    return role === "admin" ? 0 : role === "moderator" ? 1 : 2;
  }

  function membersText(count: number): string {
    if (count === 1) return "1 člen";
    if (count >= 2 && count <= 4) return `${count} členové`;
    return `${count} členů`;
  }

  function load() {
    return client
      .roomInfo(room.id)
      .then((value: RoomInfo) => {
        info = value;
        failed = false;
      })
      .catch(() => (failed = true));
  }

  onMount(() => {
    load();
    if (room.isDirect && room.isEncrypted && room.directUserId) {
      client
        .userTrust(room.directUserId)
        .then((value) => (trust = value as TrustLevel))
        .catch(() => undefined);
    }
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

  function startEditing() {
    draftName = info?.name ?? room.name;
    draftTopic = info?.topic ?? "";
    editing = true;
  }

  function saveEdits(event: SubmitEvent) {
    event.preventDefault();
    if (!info) return;
    const current = info;
    act(async () => {
      if (current.canEditName && draftName.trim() !== current.name) {
        await client.setRoomName(room.id, draftName);
      }
      if (current.canEditTopic && draftTopic.trim() !== (current.topic ?? "")) {
        await client.setRoomTopic(room.id, draftTopic);
      }
      editing = false;
      await load();
    });
  }

  async function changePhoto() {
    const bytes = await pickImage();
    if (bytes) act(() => client.setRoomAvatar(room.id, bytes));
  }

  function openInvite() {
    invitees = [];
    error = "";
    inviteDialog.showModal();
  }

  function invite() {
    const ids = invitees.map((person) => person.userId);
    if (ids.length === 0) return;
    act(async () => {
      await client.invitePeople(room.id, ids);
      inviteDialog.close();
      await load();
    });
  }

  function askRemove(member: Member) {
    removing = member;
    removeDialog.showModal();
  }

  function remove() {
    const member = removing;
    removeDialog.close();
    if (!member) return;
    act(async () => {
      await client.removeMember(room.id, member.userId);
      await load();
    });
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
      {#if editing && info}
        <form class="edit" onsubmit={saveEdits}>
          {#if info.canEditAvatar}
            <button type="button" class="link" onclick={changePhoto} disabled={busy}>
              <Icon name="camera" size={16} />Změnit fotku skupiny
            </button>
          {/if}
          {#if info.canEditName}
            <label>
              <span>Název</span>
              <input bind:value={draftName} maxlength="64" disabled={busy} />
            </label>
          {/if}
          {#if info.canEditTopic}
            <label>
              <span>Popis</span>
              <textarea bind:value={draftTopic} maxlength="500" rows="3" placeholder="O čem skupina je" disabled={busy}></textarea>
            </label>
          {/if}
          <div class="buttons">
            <button type="button" onclick={() => (editing = false)} disabled={busy}>Zrušit</button>
            <button type="submit" class="primary" disabled={busy || (info.canEditName && !draftName.trim())}>Uložit</button>
          </div>
        </form>
      {:else}
        <h2>{room.name}</h2>
        {#if info?.isDirect && info.directUserId}<span class="muted">{info.directUserId}</span>{/if}
        {#if info?.topic}<p class="topic">{info.topic}</p>{/if}
        {#if room.isEncrypted}<span class="lock"><Icon name="lock" size={13} />Šifrováno</span>{/if}
        {#if trust === "verified"}
          <span class="trust ok"><Icon name="shield-check" size={15} />Ověřený kontakt</span>
        {:else if trust === "unverified" || trust === "verificationBroken"}
          <span class="trust" class:broken={trust === "verificationBroken"}>
            <Icon name="shield-alert" size={15} />{trust === "verificationBroken" ? "Ověření už neplatí" : "Neověřený kontakt"}
          </span>
          {#if room.directUserId}
            {@const userId = room.directUserId}
            <button class="link" onclick={() => onVerify(userId)}><Icon name="fingerprint" size={16} />Ověřit pomocí emoji</button>
          {/if}
        {/if}
        {#if canEdit}
          <button class="link" onclick={startEditing}><Icon name="edit" size={16} />Upravit skupinu</button>
        {/if}
      {/if}
    </div>

    <div class="actions">
      <button onclick={() => act(() => client.setFavourite(room.id, !room.isFavourite))} disabled={busy}>
        <Icon name="pin" size={18} />{room.isFavourite ? "Odepnout" : "Připnout nahoru"}
      </button>
      <button onclick={() => act(() => client.setMuted(room.id, !room.isMuted))} disabled={busy}>
        <Icon name={room.isMuted ? "bell" : "bell-off"} size={18} />{room.isMuted ? "Zrušit ztlumení" : "Ztlumit"}
      </button>
      <button onclick={() => (showAlbum = true)}>
        <Icon name="images" size={18} />Album fotek a souborů
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
      {#if info?.canInvite}
        <button class="invite" onclick={openInvite} disabled={busy}><Icon name="user-plus" size={18} />Pozvat lidi</button>
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
            {#if member.canRemove}
              <button class="icon remove" onclick={() => askRemove(member)} disabled={busy} title="Odebrat ze skupiny">
                <Icon name="close" size={18} />
              </button>
            {/if}
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

  {#if showAlbum}
    <Album {client} {room} {rooms} onClose={() => (showAlbum = false)} />
  {/if}

  <dialog bind:this={inviteDialog} class="invite-dialog">
    <h2>Pozvat do „{room.name}“</h2>
    <PeoplePicker {client} bind:selected={invitees} exclude={members.map((member) => member.userId)} />
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    <div class="buttons">
      <button onclick={() => inviteDialog.close()}>Zrušit</button>
      <button class="primary" onclick={invite} disabled={busy || invitees.length === 0}>
        {invitees.length > 1 ? `Pozvat ${invitees.length} lidi` : "Pozvat"}
      </button>
    </div>
  </dialog>

  <dialog bind:this={removeDialog}>
    <h2>Odebrat {removing?.name ?? shortName(removing?.userId ?? "")}?</h2>
    <p>Přestane dostávat zprávy z této skupiny. Vrátit se může jen s novou pozvánkou.</p>
    <div class="buttons">
      <button onclick={() => removeDialog.close()}>Zrušit</button>
      <button class="danger" onclick={remove}>Odebrat</button>
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

  .trust {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-top: 6px;
    font-size: 0.82rem;
    color: var(--gold);
  }

  .trust.ok {
    color: var(--success);
  }

  .trust.broken {
    color: #f87171;
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

  .buttons .primary {
    border-color: transparent;
    background: var(--accent);
    color: #fff;
  }

  .link {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-top: 8px;
    padding: 6px 12px;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: none;
    font-weight: 600;
    font-size: 0.88rem;
  }

  .edit {
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    margin-top: 4px;
  }

  .edit label {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 4px;
    text-align: left;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .edit input,
  .edit textarea {
    padding: 10px 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: var(--surface);
    color: var(--ink);
    font: inherit;
    font-size: 0.95rem;
    outline: none;
    resize: vertical;
  }

  .edit input:focus,
  .edit textarea:focus {
    border-color: var(--accent);
  }

  .edit .buttons {
    width: 100%;
  }

  .invite {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 6px;
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px dashed var(--line);
    background: none;
    font-weight: 600;
    color: var(--accent-soft);
  }

  .remove {
    margin-left: auto;
    width: 32px;
    height: 32px;
    color: var(--ink-muted);
  }

  .invite-dialog {
    width: min(460px, calc(100vw - 32px));
    max-height: min(640px, calc(100vh - 48px));
  }

  .invite-dialog[open] {
    display: flex;
    flex-direction: column;
  }

  .invite-dialog :global(.search),
  .invite-dialog :global(.chips) {
    margin-left: 0;
    margin-right: 0;
  }

  .invite-dialog .buttons {
    margin-top: 12px;
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
