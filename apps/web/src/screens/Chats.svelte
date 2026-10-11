<script lang="ts">
  import { onMount, untrack } from "svelte";
  import Avatar from "../components/Avatar.svelte";
  import Icon from "../components/Icon.svelte";
  import Logo from "../components/Logo.svelte";
  import LogoutDialog from "../components/LogoutDialog.svelte";
  import NewChatDialog from "../components/NewChatDialog.svelte";
  import Recovery from "../components/Recovery.svelte";
  import StatusDialog from "../components/StatusDialog.svelte";
  import StatusStrip from "../components/StatusStrip.svelte";
  import VerificationDialog from "../components/VerificationDialog.svelte";
  import Devices from "./Devices.svelte";
  import InviteView from "./InviteView.svelte";
  import MapScreen from "./MapScreen.svelte";
  import { mapsEnabled } from "../lib/config";
  import RoomView from "./Room.svelte";
  import { signOut } from "../lib/account";
  import type { PockyClient, Verification } from "../lib/core";
  import { Alerts } from "../lib/alerts.svelte";
  import { PresenceTracker, typingText } from "../lib/presence.svelte";
  import { formatTimestamp, previewText, unreadText } from "../lib/text";
  import type { Aura, Encryption, Room, SyncStatus } from "../lib/types";

  let { client, onSignedOut }: { client: PockyClient; onSignedOut: () => void } = $props();

  let name = $state("");
  let rooms = $state<Room[] | null>(null);
  let status = $state<SyncStatus>("idle");
  let leaving = $state(false);
  let encryption = $state<Encryption | null>(null);
  let verification = $state.raw<Verification | null>(null);
  let confirmingLogout = $state(false);
  let editingStatus = $state(false);
  let creating = $state(false);
  let filter = $state<"all" | "unread" | "people" | "groups">("all");
  let search = $state("");

  const filters = [
    { id: "all", label: "Vše" },
    { id: "unread", label: "Nepřečtené" },
    { id: "people", label: "Lidé" },
    { id: "groups", label: "Skupiny" },
  ] as const;

  const visible = $derived.by(() => {
    const term = search.trim().toLocaleLowerCase("cs");
    return (rooms ?? []).filter((room) => {
      if (term && !room.name.toLocaleLowerCase("cs").includes(term)) return false;
      if (filter === "unread") return room.unreadMessages > 0 || room.isInvite;
      if (filter === "people") return room.isDirect;
      if (filter === "groups") return !room.isDirect;
      return true;
    });
  });

  const emptyText = $derived(
    search.trim()
      ? ["Nic nenalezeno", "Zkus jiné jméno nebo název skupiny."]
      : filter === "unread"
        ? ["Všechno máš přečtené", "Žádná nová zpráva nečeká."]
        : filter === "people"
          ? ["Zatím žádný soukromý chat", "Soukromé konverzace 1:1 se objeví tady."]
          : filter === "groups"
            ? ["Zatím žádná skupina", "Skupiny, ve kterých jsi, se objeví tady."]
            : ["Zatím tu nic není", "Až ti někdo napíše, objeví se tady."],
  );

  function openRoom(roomId: string) {
    location.hash = `/room/${encodeURIComponent(roomId)}`;
  }
  const tracker = new PresenceTracker(untrack(() => client));
  const alerts = new Alerts();

  $effect(() => {
    if (rooms) alerts.update(rooms, untrack(() => selectedId), openRoom);
  });

  $effect(() => {
    tracker.track((rooms ?? []).filter((room) => room.isDirect && room.directUserId).map((room) => room.directUserId!));
  });

  function auraOf(room: Room): Aura | undefined {
    if (tracker.typingIn(room.id).length > 0) return "typing";
    if (!room.isDirect || !room.directUserId) return undefined;
    const state = tracker.presence[room.directUserId];
    return state === "online" ? "online" : state === "unavailable" ? "away" : undefined;
  }
  let route = $state(location.hash);

  const selectedId = $derived(route.match(/^#\/room\/(.+)$/) ? decodeURIComponent(route.slice(7)) : null);
  const showDevices = $derived(route === "#/devices");
  const showMap = $derived(route === "#/map");
  const locked = $derived(
    encryption !== null && !encryption.deviceVerified && (encryption.canRecover || encryption.canVerifyWithDevice),
  );

  const selected = $derived(rooms?.find((room) => room.id === selectedId) ?? null);

  function open(room: Room) {
    location.hash = `/room/${encodeURIComponent(room.id)}`;
  }

  function close() {
    history.replaceState(null, "", location.pathname);
    route = "";
  }

  function verify() {
    client
      .requestVerification()
      .then((value: Verification) => (verification = value))
      .catch(() => undefined);
  }

  const unread = $derived(
    (rooms ?? []).reduce((sum, room) => sum + (room.isMuted ? 0 : room.unreadMessages), 0),
  );

  const statusText = $derived(
    status === "offline" || status === "error"
      ? "Offline"
      : rooms === null
        ? "Načítám chaty…"
        : unread > 0
          ? unreadText(unread)
          : "Vše přečteno ✓",
  );

  onMount(() => {
    const onHash = () => (route = location.hash);
    window.addEventListener("hashchange", onHash);

    const userId = client.userId() ?? "";
    name = userId.split(":")[0].replace(/^@/, "");
    client.displayName().then((value) => (name = value)).catch(() => undefined);

    client.watchSyncStatus((value: SyncStatus) => (status = value)).catch(() => undefined);
    client.watchRooms((value: Room[]) => (rooms = value)).catch(() => undefined);
    client.watchEncryption((value: Encryption) => (encryption = value));
    client.watchVerificationRequests((value: Verification) => (verification = value));
    client.startSync().catch(() => (status = "error"));

    return () => {
      window.removeEventListener("hashchange", onHash);
      tracker.stop();
    };
  });

  async function leave() {
    confirmingLogout = false;
    if (leaving) return;
    leaving = true;
    try {
      await signOut(client);
    } finally {
      onSignedOut();
    }
  }
</script>

<div class="layout" class:open={selected !== null || showDevices || showMap}>
  <aside>
    <header>
      <div>
        <h1>Ahoj, {name} 👋</h1>
        <p class="status" class:good={status === "running" && rooms !== null && unread === 0}>
          {statusText}
        </p>
      </div>
      <div class="tools">
        {#if mapsEnabled}
          <a class="tool" href="#/map" title="Mapa"><Icon name="map" /></a>
        {/if}
        <a class="tool" href="#/devices" title="Zařízení a soukromí"><Icon name="device" /></a>
        <button class="tool" onclick={() => (confirmingLogout = true)} disabled={leaving} title="Odhlásit se">
          <Icon name="logout" />
        </button>
      </div>
    </header>

    <StatusStrip
      {tracker}
      userId={client.userId() ?? ""}
      {name}
      rooms={rooms ?? []}
      online={status === "running"}
      onEdit={() => (editingStatus = true)}
    />

    <div class="find">
      <label class="search">
        <Icon name="search" size={17} />
        <input bind:value={search} placeholder="Hledat v chatech" autocomplete="off" />
      </label>
      <button class="new" onclick={() => (creating = true)} title="Nový chat"><Icon name="compose" /></button>
    </div>
    <div class="filters">
      {#each filters as option (option.id)}
        <button class:on={filter === option.id} onclick={() => (filter = option.id)}>{option.label}</button>
      {/each}
    </div>

    {#if locked && encryption}
      <Recovery
        {client}
        canRecover={encryption.canRecover}
        canVerify={encryption.canVerifyWithDevice}
        onDone={() => encryption && (encryption = { ...encryption, deviceVerified: true })}
        onVerify={verify}
      />
    {/if}

    <ul>
      {#if rooms !== null && visible.length === 0}
        <li class="empty-list">
          <strong>{emptyText[0]}</strong>
          <span>{emptyText[1]}</span>
        </li>
      {/if}
      {#each visible as room (room.id)}
        <li>
          <button class="row" class:active={room.id === selectedId} onclick={() => open(room)}>
            <Avatar id={room.id} name={room.name} url={room.avatarUrl} square={!room.isDirect} aura={auraOf(room)} />
            <span class="text">
              <strong>{room.name}</strong>
              <span class="preview">
                {#if tracker.typingIn(room.id).length > 0}
                  <span class="typing">{typingText(tracker.typingIn(room.id), room.isDirect)}</span>
                {:else if room.isInvite}
                  Pozvánka do konverzace
                {:else if room.lastMessage}
                  {#if room.lastMessage.isOwn && room.lastMessage.delivery === "read"}<b class="tick">✓✓ </b>{/if}
                  {previewText(room.lastMessage, room.isDirect)}
                {/if}
              </span>
            </span>
            <span class="meta">
              <span class="when">
                {#if room.isFavourite}<Icon name="pin" size={12} />{/if}
                {#if room.isMuted}<Icon name="bell-off" size={12} />{/if}
                {#if room.timestampMs}{formatTimestamp(room.timestampMs)}{/if}
              </span>
              {#if room.unreadMessages > 0}
                <span class="badge" class:muted={room.isMuted}>{room.unreadMessages}</span>
              {/if}
            </span>
          </button>
        </li>
      {/each}
    </ul>
  </aside>

  {#if showMap && mapsEnabled}
    <MapScreen {client} onBack={close} onOpenRoom={openRoom} />
  {:else if showDevices}
    <Devices {client} {tracker} {alerts} onBack={close} />
  {:else if selected?.isInvite}
    {#key selected.id}
      <InviteView {client} room={selected} onBack={close} />
    {/key}
  {:else if selected}
    {#key selected.id}
      <RoomView {client} {tracker} room={selected} onBack={close} />
    {/key}
  {:else}
    <section class="empty">
      <Logo size={56} />
      <p>Vyber chat ze seznamu.</p>
    </section>
  {/if}
</div>

{#if verification}
  {#key verification}
    <VerificationDialog {verification} onClose={() => (verification = null)} />
  {/key}
{/if}

{#if creating}
  <NewChatDialog {client} rooms={rooms ?? []} onOpen={openRoom} onClose={() => (creating = false)} />
{/if}

{#if editingStatus}
  <StatusDialog {tracker} onClose={() => (editingStatus = false)} />
{/if}

{#if confirmingLogout}
  <LogoutDialog {client} onConfirm={leave} onClose={() => (confirmingLogout = false)} />
{/if}

<style>
  .layout {
    height: 100%;
    display: grid;
    grid-template-columns: minmax(320px, 400px) 1fr;
    grid-template-rows: minmax(0, 1fr);
  }

  aside {
    display: flex;
    flex-direction: column;
    min-height: 0;
    border-right: 1px solid var(--line);
  }

  header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
    padding: 22px 18px 14px;
  }

  h1 {
    font-family: var(--display);
    font-size: 1.5rem;
    font-weight: 700;
    margin: 0;
  }

  .status {
    margin: 2px 0 0;
    font-size: 0.85rem;
    color: var(--ink-muted);
  }

  .status.good {
    color: var(--success);
  }

  .tools {
    display: flex;
    gap: 8px;
  }

  .tool {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: none;
    color: var(--ink-muted);
  }

  .find {
    display: flex;
    gap: 8px;
    padding: 0 12px 8px;
  }

  .search {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    height: 40px;
    padding: 0 12px;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--line);
    color: var(--ink-muted);
  }

  .search:focus-within {
    border-color: var(--accent);
  }

  .search input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: 0;
    background: none;
    outline: none;
    color: var(--ink);
  }

  .new {
    flex: none;
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border: 0;
    border-radius: 12px;
    background: var(--accent);
    color: #fff;
  }

  .filters {
    display: flex;
    gap: 6px;
    padding: 0 12px 10px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .filters button {
    flex: none;
    padding: 5px 12px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: none;
    font-size: 0.85rem;
    color: var(--ink-muted);
  }

  .filters button.on {
    border-color: transparent;
    background: color-mix(in srgb, var(--accent) 22%, transparent);
    color: var(--ink);
  }

  .empty-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 28px 16px;
    text-align: center;
    color: var(--ink-muted);
  }

  .empty-list strong {
    color: var(--ink);
  }

  .when {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0 8px 16px;
    overflow-y: auto;
  }

  .row {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px;
    border: 0;
    border-radius: 14px;
    background: none;
    text-align: left;
  }

  .row:hover,
  .row.active {
    background: var(--surface);
  }

  .row.active {
    box-shadow: inset 3px 0 0 var(--accent);
  }

  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .text strong,
  .preview {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .preview {
    color: var(--ink-muted);
    font-size: 0.9rem;
  }

  .typing {
    color: var(--accent-soft);
    font-style: italic;
  }

  .tick {
    color: var(--accent-soft);
    font-weight: 600;
  }

  .meta {
    align-self: stretch;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: space-between;
    padding: 2px 0;
    font-size: 0.78rem;
    color: var(--ink-muted);
  }

  .badge {
    min-width: 20px;
    height: 20px;
    padding: 0 6px;
    border-radius: 10px;
    background: var(--accent);
    color: #fff;
    font-weight: 700;
    display: grid;
    place-items: center;
  }

  .badge.muted {
    background: color-mix(in srgb, var(--ink-muted) 40%, transparent);
  }

  .empty {
    display: grid;
    place-content: center;
    justify-items: center;
    gap: 14px;
    color: var(--ink-muted);
    opacity: 0.8;
  }

  @media (max-width: 760px) {
    .layout {
      grid-template-columns: 1fr;
    }

    aside {
      border-right: 0;
    }

    .empty,
    .open aside {
      display: none;
    }
  }
</style>
