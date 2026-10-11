<script lang="ts">
  import { onMount } from "svelte";
  import Avatar from "../components/Avatar.svelte";
  import Composer from "../components/Composer.svelte";
  import ImageViewer from "../components/ImageViewer.svelte";
  import RoomInfoPanel from "../components/RoomInfoPanel.svelte";
  import MapDialog from "../components/MapDialog.svelte";
  import SendLocation from "../components/SendLocation.svelte";
  import { mapsEnabled } from "../lib/config";
  import Icon from "../components/Icon.svelte";
  import Message from "../components/Message.svelte";
  import type { PockyClient, RoomTimeline } from "../lib/core";
  import { type PresenceTracker, typingText } from "../lib/presence.svelte";
  import { contentText, dayLabel, shortName, stateText } from "../lib/text";
  import { applyDiffs } from "../lib/timeline";
  import type { Draft, EventEntry, GeoPoint, MessageActions, Room, TimelineDiff, TimelineEntry } from "../lib/types";

  let {
    client,
    tracker,
    room,
    onBack,
  }: { client: PockyClient; tracker: PresenceTracker; room: Room; onBack: () => void } = $props();

  const typists = $derived(tracker.typingIn(room.id));
  const presenceText = $derived.by(() => {
    if (!room.isDirect || !room.directUserId) return "";
    const state = tracker.presence[room.directUserId];
    return state === "online" ? "online" : state === "unavailable" ? "mimo" : "";
  });

  let typingSent = false;
  let typingTimer: ReturnType<typeof setTimeout> | undefined;

  function typing(active: boolean) {
    clearTimeout(typingTimer);
    if (active) typingTimer = setTimeout(() => typing(false), 5000);
    if (active === typingSent) return;
    typingSent = active;
    client.sendTyping(room.id, active).catch(() => undefined);
  }

  const groupGapMs = 5 * 60 * 1000;

  let entries = $state<TimelineEntry[]>([]);
  let failed = $state(false);
  let loaded = $state(false);
  let atStart = $state(false);
  let loadingOlder = false;
  let timeline = $state.raw<RoomTimeline | null>(null);
  let draft = $state<Draft | null>(null);
  let removing = $state<EventEntry | null>(null);
  let shownLocation = $state<{ point: GeoPoint; title: string } | null>(null);
  let sendingLocation = $state(false);
  let viewing = $state<string | null>(null);
  let showInfo = $state(false);

  const images = $derived(
    entries.flatMap((entry) =>
      entry.kind.type === "event" && entry.kind.content.kind === "image" ? [entry.kind] : [],
    ),
  );
  let dialog = $state<HTMLDialogElement>();

  const actions: MessageActions = {
    reply: (event) => {
      draft = {
        mode: "reply",
        itemKey: event.itemKey,
        eventId: event.eventId,
        sender: event.senderName ?? shortName(event.senderId),
        text: contentText(event.content),
      };
    },
    edit: (event) => {
      draft = {
        mode: "edit",
        itemKey: event.itemKey,
        sender: event.senderName ?? shortName(event.senderId),
        text: "body" in event.content ? event.content.body : "",
      };
    },
    react: (event, key) => {
      timeline?.toggleReaction(event.itemKey, key).catch(() => undefined);
    },
    remove: (event) => {
      removing = event;
      dialog?.showModal();
    },
    openLocation: (point, title) => {
      if (mapsEnabled) shownLocation = { point, title };
    },
    openImage: (event) => {
      viewing = event.itemKey;
    },
  };

  function confirmRemove() {
    const event = removing;
    dialog?.close();
    removing = null;
    if (event) timeline?.redact(event.itemKey).catch(() => undefined);
  }
  let scroller: HTMLElement;
  let sentinel: HTMLElement;
  let readTimer: ReturnType<typeof setTimeout> | undefined;

  const rows = $derived.by(() => {
    let previous: EventEntry | null = null;
    return entries.map((entry) => {
      const kind = entry.kind;
      if (kind.type !== "event" || kind.content.kind === "state") {
        previous = null;
        return { entry, first: true };
      }
      const first =
        !previous ||
        previous.senderId !== kind.senderId ||
        kind.timestampMs - previous.timestampMs > groupGapMs;
      previous = kind;
      return { entry, first };
    });
  });

  function nearBottom(): boolean {
    return scroller ? Math.abs(scroller.scrollTop) < 120 : true;
  }

  function scheduleRead() {
    if (readTimer || !timeline) return;
    readTimer = setTimeout(() => {
      readTimer = undefined;
      if (document.visibilityState === "visible" && nearBottom()) {
        timeline?.markAsRead().catch(() => undefined);
      }
    }, 1200);
  }

  async function loadOlder() {
    if (!timeline || loadingOlder || atStart) return;
    loadingOlder = true;
    try {
      atStart = await timeline.paginateBackwards();
    } catch {
      atStart = true;
    } finally {
      loadingOlder = false;
    }
  }

  onMount(() => {
    let alive = true;
    const observer = new IntersectionObserver(
      (items) => {
        if (items.some((item) => item.isIntersecting)) loadOlder();
      },
      { root: scroller, rootMargin: "400px 0px 0px 0px" },
    );

    client
      .openTimeline(room.id)
      .then((opened) => {
        if (!alive) {
          opened.close();
          return;
        }
        timeline = opened;
        opened.watch((diffs: TimelineDiff[]) => {
          entries = applyDiffs(entries, diffs);
          loaded = true;
          if (entries.some((entry) => entry.kind.type === "timelineStart")) atStart = true;
          scheduleRead();
        });
        observer.observe(sentinel);
      })
      .catch(() => (failed = true));

    const onVisible = () => scheduleRead();
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      alive = false;
      observer.disconnect();
      clearTimeout(readTimer);
      if (typingSent) typing(false);
      clearTimeout(typingTimer);
      document.removeEventListener("visibilitychange", onVisible);
      timeline?.close();
    };
  });
</script>

<div class="shell" class:info={showInfo}>
<section class="room">
  <header>
    <button class="back" onclick={onBack} aria-label="Zpět"><Icon name="arrow-back" /></button>
    <Avatar id={room.id} name={room.name} url={room.avatarUrl} size={40} square={!room.isDirect} />
    <button class="title" onclick={() => (showInfo = !showInfo)}>
      <strong>{room.name}</strong>
      {#if typists.length > 0}
        <span class="sub typing">{typingText(typists, room.isDirect)}</span>
      {:else if presenceText}
        <span class="sub" class:online={presenceText === "online"}>{presenceText}</span>
      {:else if room.isEncrypted}
        <span class="lock"><Icon name="lock" size={13} />Šifrovaná místnost</span>
      {/if}
    </button>
    <button class="info-toggle" class:on={showInfo} onclick={() => (showInfo = !showInfo)} aria-label="Informace o chatu">
      <Icon name="info" />
    </button>
  </header>

  <div class="scroller" bind:this={scroller} onscroll={scheduleRead}>
    <div class="content">
      <div bind:this={sentinel} class="sentinel"></div>
      {#if failed}
        <p class="center">Konverzaci se nepodařilo otevřít.</p>
      {:else if !loaded}
        <p class="center">Načítám zprávy…</p>
      {/if}

      {#each rows as { entry, first } (entry.id)}
        {@const kind = entry.kind}
        {#if kind.type === "dateDivider"}
          <div class="divider"><span>{dayLabel(kind.timestampMs)}</span></div>
        {:else if kind.type === "readMarker"}
          <div class="unread"><span>Nové zprávy</span></div>
        {:else if kind.type === "timelineStart"}
          <p class="center start">Začátek konverzace</p>
        {:else if kind.content.kind === "state"}
          <p class="center state">{stateText(kind.content, kind.senderName ?? shortName(kind.senderId))}</p>
        {:else}
          <Message event={kind} {first} showSender={!room.isDirect} {actions} />
        {/if}
      {/each}
    </div>
  </div>

  <Composer {timeline} {draft} onCancel={() => (draft = null)} onTyping={typing} onLocation={mapsEnabled ? () => (sendingLocation = true) : undefined} />

  {#if viewing && !showInfo}
    <ImageViewer {images} start={viewing} onClose={() => (viewing = null)} />
  {/if}

  {#if shownLocation}
    <MapDialog point={shownLocation.point} title={shownLocation.title} onClose={() => (shownLocation = null)} />
  {/if}

  {#if sendingLocation && timeline}
    <SendLocation
      {timeline}
      initialPrecision={tracker.privacy.locationPrecision ?? "city"}
      onClose={() => (sendingLocation = false)}
    />
  {/if}

  <dialog bind:this={dialog} onclose={() => (removing = null)}>
    <h2>Smazat zprávu?</h2>
    <p>Zpráva zmizí všem v konverzaci. Nejde to vrátit.</p>
    <div class="buttons">
      <button type="button" onclick={() => dialog?.close()}>Zrušit</button>
      <button type="button" class="danger" onclick={confirmRemove}>Smazat</button>
    </div>
  </dialog>
</section>

{#if showInfo}
  <RoomInfoPanel
    {client}
    {room}
    {images}
    onOpenImage={(itemKey) => {
      showInfo = false;
      viewing = itemKey;
    }}
    onLeft={onBack}
    onClose={() => (showInfo = false)}
  />
{/if}
</div>

<style>
  .shell {
    height: 100%;
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
  }

  .shell.info {
    grid-template-columns: minmax(0, 1fr) 340px;
  }

  .title {
    flex: 1;
    padding: 0;
    border: 0;
    background: none;
    text-align: left;
    color: inherit;
  }

  .info-toggle {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 10px;
    background: none;
    color: var(--ink-muted);
  }

  .info-toggle.on {
    color: var(--accent-soft);
    background: var(--surface);
  }

  @media (max-width: 1100px) {
    .shell.info {
      grid-template-columns: minmax(0, 1fr);
    }

    .shell.info .room {
      display: none;
    }
  }

  .room {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--line);
  }

  .back {
    display: none;
    place-items: center;
    width: 36px;
    height: 36px;
    margin-left: -6px;
    border: 0;
    border-radius: 10px;
    background: none;
  }

  .title {
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .title strong {
    font-size: 1.05rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .sub {
    font-size: 0.78rem;
    color: var(--ink-muted);
  }

  .sub.online {
    color: var(--success);
  }

  .sub.typing {
    color: var(--accent-soft);
    font-style: italic;
  }

  .lock {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.78rem;
    color: var(--success);
  }

  .scroller {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column-reverse;
    overscroll-behavior: contain;
  }

  .content {
    padding: 8px 0 14px;
  }

  .sentinel {
    height: 1px;
  }

  .center {
    margin: 10px 16px;
    text-align: center;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .start {
    margin-top: 16px;
  }

  .divider,
  .unread {
    display: flex;
    justify-content: center;
    margin: 16px 0 8px;
  }

  .divider span {
    padding: 3px 12px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--line);
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .unread {
    align-items: center;
    gap: 10px;
    padding: 0 16px;
    color: var(--accent-soft);
    font-size: 0.78rem;
    font-weight: 700;
  }

  .unread::before,
  .unread::after {
    content: "";
    flex: 1;
    height: 1px;
    background: color-mix(in srgb, var(--accent) 50%, transparent);
  }

  dialog {
    max-width: 360px;
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
    font-size: 1.25rem;
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

  @media (max-width: 760px) {
    .back {
      display: grid;
    }
  }
</style>
