<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient, RoomTimeline } from "../lib/core";
  import { download, mediaFile } from "../lib/media";
  import { fileSize, shortName } from "../lib/text";
  import { applyDiffs } from "../lib/timeline";
  import type { EventEntry, Room, TimelineDiff, TimelineEntry } from "../lib/types";
  import ForwardDialog from "./ForwardDialog.svelte";
  import Icon from "./Icon.svelte";
  import ImageViewer from "./ImageViewer.svelte";
  import MediaImage from "./MediaImage.svelte";

  let {
    client,
    room,
    rooms,
    onClose,
  }: { client: PockyClient; room: Room; rooms: Room[]; onClose: () => void } = $props();

  type Tab = "visual" | "audio" | "files";

  const tabs: { id: Tab; label: string }[] = [
    { id: "visual", label: "Fotky a videa" },
    { id: "audio", label: "Hlasovky" },
    { id: "files", label: "Dokumenty" },
  ];

  let entries = $state<TimelineEntry[]>([]);
  let timeline = $state.raw<RoomTimeline | null>(null);
  let atStart = $state(false);
  let loading = $state(true);
  let failed = $state(false);
  let tab = $state<Tab>("visual");
  let selected = $state<string[]>([]);
  let viewing = $state<string | null>(null);
  let forwarding = $state<string[] | null>(null);
  let busy = $state(false);
  let notice = $state("");
  let dialog: HTMLDialogElement;
  let sentinel: HTMLElement;

  const events = $derived(
    entries
      .flatMap((entry) => (entry.kind.type === "event" ? [entry.kind] : []))
      .sort((a, b) => b.timestampMs - a.timestampMs),
  );
  const visible = $derived(events.filter((event) => tabOf(event) === tab));
  const months = $derived.by(() => {
    const groups: { label: string; items: EventEntry[] }[] = [];
    for (const event of visible) {
      const label = monthLabel(event.timestampMs);
      const last = groups.at(-1);
      if (last?.label === label) last.items.push(event);
      else groups.push({ label, items: [event] });
    }
    return groups;
  });
  const images = $derived(events.filter((event) => event.content.kind === "image").reverse());
  const picked = $derived(visible.filter((event) => selected.includes(event.itemKey)));
  const canShare = typeof navigator.canShare === "function";

  function tabOf(event: EventEntry): Tab | null {
    const kind = event.content.kind;
    if (kind === "image" || kind === "video") return "visual";
    if (kind === "audio") return "audio";
    if (kind === "file") return "files";
    return null;
  }

  function monthLabel(timestampMs: number): string {
    const label = new Date(timestampMs).toLocaleDateString("cs-CZ", { month: "long", year: "numeric" });
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  onMount(() => {
    dialog.showModal();
    let alive = true;
    let opened: RoomTimeline | null = null;
    const observer = new IntersectionObserver((items) => {
      if (items.some((item) => item.isIntersecting)) loadOlder();
    });

    client
      .openMedia(room.id)
      .then((value) => {
        if (!alive) {
          value.close();
          return;
        }
        opened = value;
        timeline = value;
        value.watch((diffs: TimelineDiff[]) => {
          entries = applyDiffs(entries, diffs);
          if (entries.some((entry) => entry.kind.type === "timelineStart")) atStart = true;
        });
        observer.observe(sentinel);
        return loadOlder();
      })
      .catch(() => (failed = true))
      .finally(() => (loading = false));

    return () => {
      alive = false;
      observer.disconnect();
      opened?.close();
    };
  });

  let paging = false;

  async function loadOlder() {
    if (!timeline || paging || atStart) return;
    paging = true;
    try {
      atStart = await timeline.paginateBackwards();
    } catch {
      atStart = true;
    } finally {
      paging = false;
    }
  }

  function stamp(timestampMs: number): string {
    const d = new Date(timestampMs);
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
  }

  function fileName(event: EventEntry): string {
    const c = event.content;
    if (c.kind === "file") return c.name;
    const ext = c.kind === "image" ? "jpg" : c.kind === "video" ? "mp4" : c.kind === "audio" ? "ogg" : "bin";
    return `PockyAPP-${stamp(event.timestampMs)}.${ext}`;
  }

  function sourceOf(event: EventEntry): string | null {
    const c = event.content;
    return "source" in c ? c.source : null;
  }

  function toggle(event: EventEntry) {
    selected = selected.includes(event.itemKey)
      ? selected.filter((key) => key !== event.itemKey)
      : [...selected, event.itemKey];
  }

  function open(event: EventEntry) {
    if (selected.length > 0) {
      toggle(event);
      return;
    }
    if (event.content.kind === "image") viewing = event.itemKey;
    else saveAll([event]);
  }

  async function saveAll(list: EventEntry[]) {
    busy = true;
    notice = "";
    try {
      for (const [index, event] of list.entries()) {
        const source = sourceOf(event);
        if (!source) continue;
        if (list.length > 1) notice = `Stahuji ${index + 1} z ${list.length}…`;
        await download(source, fileName(event));
      }
      notice = list.length > 1 ? "Staženo." : "";
    } catch {
      notice = "Některé soubory se nepodařilo stáhnout.";
    } finally {
      busy = false;
    }
  }

  async function share(list: EventEntry[]) {
    busy = true;
    notice = "";
    try {
      const files: File[] = [];
      for (const event of list) {
        const source = sourceOf(event);
        if (!source) continue;
        files.push(await mediaFile(source, fileName(event)));
      }
      if (!navigator.canShare({ files })) {
        notice = "Tento prohlížeč takové soubory sdílet neumí.";
        return;
      }
      await navigator.share({ files });
    } catch (err) {
      if (!(err instanceof DOMException && err.name === "AbortError")) notice = "Sdílení se nepovedlo.";
    } finally {
      busy = false;
    }
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); if (!viewing && !forwarding) close(); }}>
  <header>
    <button class="icon" onclick={close} aria-label="Zavřít"><Icon name="arrow-back" /></button>
    <div class="title">
      <h2>Album</h2>
      <small>{room.name}</small>
    </div>
    {#if visible.length > 0 && selected.length === 0}
      <button class="ghost" onclick={() => saveAll(visible)} disabled={busy}><Icon name="download" size={18} />Stáhnout vše</button>
    {/if}
  </header>

  <nav>
    {#each tabs as item (item.id)}
      <button class:on={tab === item.id} onclick={() => { tab = item.id; selected = []; }}>{item.label}</button>
    {/each}
  </nav>

  <div class="body">
    {#if failed}
      <p class="hint">Album se nepodařilo otevřít.</p>
    {:else if !loading && visible.length === 0 && atStart}
      <p class="hint">Tady zatím nic není.</p>
    {/if}

    {#each months as month (month.label)}
      <h3>{month.label}</h3>
      {#if tab === "visual"}
        <div class="grid">
          {#each month.items as event (event.itemKey)}
            {@const c = event.content}
            <div class="cell" class:picked={selected.includes(event.itemKey)}>
              <button class="thumb" onclick={() => open(event)} aria-label={fileName(event)}>
                {#if c.kind === "image"}
                  <MediaImage source={c.source} width={1} height={1} />
                {:else if c.kind === "video" && c.thumbnail}
                  <MediaImage source={c.thumbnail} width={1} height={1} />
                  <span class="badge"><Icon name="play" size={14} /></span>
                {:else}
                  <span class="tile"><Icon name="video" size={26} /></span>
                {/if}
              </button>
              <button class="check" onclick={() => toggle(event)} aria-label="Vybrat">
                {#if selected.includes(event.itemKey)}<Icon name="check" size={14} />{/if}
              </button>
            </div>
          {/each}
        </div>
      {:else}
        <ul>
          {#each month.items as event (event.itemKey)}
            {@const c = event.content}
            <li class:picked={selected.includes(event.itemKey)}>
              <button class="check" onclick={() => toggle(event)} aria-label="Vybrat">
                {#if selected.includes(event.itemKey)}<Icon name="check" size={14} />{/if}
              </button>
              <Icon name={c.kind === "audio" ? "mic" : "file"} />
              <span class="text">
                <strong>{c.kind === "file" ? c.name : `Hlasovka od ${event.senderName ?? shortName(event.senderId)}`}</strong>
                <small>
                  {new Date(event.timestampMs).toLocaleDateString("cs-CZ")}
                  {#if c.kind === "file" && c.size} · {fileSize(c.size)}{/if}
                </small>
              </span>
              <button class="ghost small" onclick={() => saveAll([event])} disabled={busy} aria-label="Stáhnout">
                <Icon name="download" size={18} />
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    {/each}

    {#if !atStart && !failed}<p class="hint">Načítám starší…</p>{/if}
    <div bind:this={sentinel} class="sentinel"></div>
  </div>

  {#if selected.length > 0 || notice}
    <footer>
      {#if selected.length > 0}
        <span>Vybráno: {picked.length}</span>
        <button class="ghost" onclick={() => (selected = [])}>Zrušit</button>
        <button onclick={() => saveAll(picked)} disabled={busy}><Icon name="download" size={18} />Stáhnout</button>
        {#if canShare}
          <button onclick={() => share(picked)} disabled={busy}><Icon name="share" size={18} />Sdílet</button>
        {/if}
        <button
          onclick={() => (forwarding = picked.flatMap((event) => (event.eventId ? [event.eventId] : [])))}
          disabled={busy}
        >
          <Icon name="forward" size={18} />Přeposlat
        </button>
      {/if}
      {#if notice}<span class="notice">{notice}</span>{/if}
    </footer>
  {/if}

  {#if viewing}
    <ImageViewer {images} start={viewing} onClose={() => (viewing = null)} />
  {/if}

  {#if forwarding}
    <ForwardDialog
      {client}
      {rooms}
      fromRoom={room.id}
      eventIds={forwarding}
      onClose={() => {
        forwarding = null;
        selected = [];
      }}
    />
  {/if}
</dialog>

<style>
  dialog {
    width: min(760px, 100vw);
    height: min(820px, 100vh);
    max-width: 100vw;
    max-height: 100vh;
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--bg);
    color: var(--ink);
  }

  dialog[open] {
    display: flex;
    flex-direction: column;
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.7);
  }

  header {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 14px 8px 8px;
  }

  .title {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  h2 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.25rem;
  }

  .title small {
    color: var(--ink-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
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

  nav {
    display: flex;
    gap: 6px;
    padding: 0 14px 10px;
    border-bottom: 1px solid var(--line);
  }

  nav button {
    padding: 6px 13px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: none;
    font-size: 0.85rem;
    color: var(--ink-muted);
  }

  nav button.on {
    border-color: transparent;
    background: color-mix(in srgb, var(--accent) 22%, transparent);
    color: var(--ink);
  }

  .body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 4px 14px 20px;
  }

  h3 {
    margin: 16px 2px 8px;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
    gap: 4px;
  }

  .cell {
    position: relative;
    aspect-ratio: 1;
    border-radius: 8px;
    overflow: hidden;
  }

  .cell.picked {
    outline: 3px solid var(--accent);
    outline-offset: -3px;
  }

  .thumb {
    width: 100%;
    height: 100%;
    padding: 0;
    border: 0;
    background: none;
  }

  .thumb :global(.media) {
    width: 100% !important;
    height: 100%;
    border-radius: 0;
  }

  .tile {
    display: grid;
    place-items: center;
    width: 100%;
    height: 100%;
    background: var(--surface);
    color: var(--accent-soft);
  }

  .badge {
    position: absolute;
    left: 6px;
    bottom: 6px;
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: rgb(0 0 0 / 0.6);
    color: #fff;
  }

  .check {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    padding: 0;
    border-radius: 50%;
    border: 2px solid #fff;
    background: rgb(0 0 0 / 0.35);
    color: #fff;
  }

  .cell .check {
    position: absolute;
    top: 6px;
    right: 6px;
    opacity: 0;
    transition: opacity 0.12s ease;
  }

  .cell:hover .check,
  .cell.picked .check,
  .grid:has(.picked) .check {
    opacity: 1;
  }

  .picked .check {
    border-color: transparent;
    background: var(--accent);
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 9px 10px;
    border-radius: 12px;
    color: var(--ink-muted);
  }

  li.picked {
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  li .check {
    border-color: var(--line);
    background: none;
  }

  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .text strong {
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .text small {
    font-size: 0.8rem;
  }

  .ghost {
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

  .ghost.small {
    padding: 6px;
  }

  footer {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 10px 14px calc(10px + env(safe-area-inset-bottom, 0px));
    border-top: 1px solid var(--line);
    background: var(--surface);
  }

  footer > span:first-child {
    flex: 1;
    font-weight: 600;
  }

  footer > button:not(.ghost) {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 13px;
    border: 0;
    border-radius: 10px;
    background: var(--accent);
    color: #fff;
    font-weight: 600;
  }

  .notice {
    color: var(--ink-muted);
    font-size: 0.85rem;
  }

  .hint {
    margin: 18px 4px;
    text-align: center;
    color: var(--ink-muted);
    font-size: 0.88rem;
  }

  .sentinel {
    height: 1px;
  }

  button:disabled {
    opacity: 0.5;
  }

  @media (max-width: 760px) {
    dialog {
      border-radius: 0;
      border: 0;
    }
  }
</style>
