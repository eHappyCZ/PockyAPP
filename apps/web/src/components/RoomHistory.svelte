<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient, RoomTimeline } from "../lib/core";
  import { dayLabel, shortName, stateText, timeOfDay } from "../lib/text";
  import { applyDiffs } from "../lib/timeline";
  import type { EventEntry, TimelineDiff, TimelineEntry } from "../lib/types";

  const PAGE_CHANGES = 20;
  const MAX_PAGES = 10;

  let { client, roomId }: { client: PockyClient; roomId: string } = $props();

  let entries = $state<TimelineEntry[]>([]);
  let loaded = $state(false);
  let loading = $state(false);
  let atStart = $state(false);
  let failed = $state(false);
  let timeline: RoomTimeline | undefined;

  const changes = $derived(
    entries
      .map((entry) => entry.kind)
      .filter((kind): kind is { type: "event" } & EventEntry => kind.type === "event" && kind.content.kind === "state")
      .reverse(),
  );

  async function fill() {
    if (!timeline || loading || atStart) return;
    loading = true;
    const target = changes.length + PAGE_CHANGES;
    try {
      for (let page = 0; page < MAX_PAGES && !atStart && changes.length < target; page++) {
        atStart = await timeline.paginateBackwards();
      }
    } catch {
      atStart = true;
    } finally {
      loading = false;
      loaded = true;
    }
  }

  function newDay(index: number): boolean {
    if (index === 0) return true;
    const day = (ms: number) => new Date(ms).toDateString();
    return day(changes[index - 1].timestampMs) !== day(changes[index].timestampMs);
  }

  onMount(() => {
    let alive = true;
    client
      .openHistory(roomId)
      .then((opened) => {
        if (!alive) {
          opened.close();
          return;
        }
        timeline = opened;
        opened.watch((diffs: TimelineDiff[]) => {
          entries = applyDiffs(entries, diffs);
        });
        fill();
      })
      .catch(() => (failed = true));

    return () => {
      alive = false;
      timeline?.close();
    };
  });
</script>

{#if failed}
  <p class="muted">Historii změn se nepodařilo načíst.</p>
{:else if !loaded}
  <p class="muted">Načítám…</p>
{:else}
  {#if changes.length === 0 && atStart}
    <p class="muted">Zatím tu nejsou žádné změny.</p>
  {/if}
  <ol>
    {#each changes as change, index (change.itemKey)}
      {#if newDay(index)}
        <li class="day">{dayLabel(change.timestampMs)}</li>
      {/if}
      {#if change.content.kind === "state"}
        <li>
          <time>{timeOfDay(change.timestampMs)}</time>
          <span>{stateText(change.content, change.senderName ?? shortName(change.senderId))}</span>
        </li>
      {/if}
    {/each}
  </ol>
  {#if !atStart}
    <button class="older" onclick={fill} disabled={loading}>{loading ? "Načítám…" : "Načíst starší"}</button>
  {/if}
{/if}

<style>
  ol {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  li {
    display: flex;
    gap: 12px;
    padding: 6px 2px;
    font-size: 0.9rem;
  }

  li.day {
    padding-top: 14px;
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--ink-muted);
  }

  time {
    flex: none;
    color: var(--ink-muted);
    font-size: 0.8rem;
  }

  .muted {
    color: var(--ink-muted);
    font-size: 0.85rem;
  }

  .older {
    display: block;
    margin: 14px auto 0;
    padding: 8px 16px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: var(--surface);
    color: var(--accent);
    font-weight: 600;
  }
</style>
