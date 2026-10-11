<script lang="ts">
  import type { PresenceTracker } from "../lib/presence.svelte";
  import type { Aura, Room } from "../lib/types";
  import Avatar from "./Avatar.svelte";

  let {
    tracker,
    userId,
    name,
    rooms,
    online,
    onEdit,
  }: {
    tracker: PresenceTracker;
    userId: string;
    name: string;
    rooms: Room[];
    online: boolean;
    onEdit: () => void;
  } = $props();

  const people = $derived(
    rooms
      .filter((room) => room.isDirect && room.directUserId && tracker.statuses[room.directUserId])
      .map((room) => ({
        id: room.id,
        name: room.name,
        avatar: room.avatarUrl,
        status: tracker.statuses[room.directUserId!],
        aura: auraOf(room.directUserId!),
      })),
  );

  function auraOf(id: string): Aura | undefined {
    const state = tracker.presence[id];
    return state === "online" ? "online" : state === "unavailable" ? "away" : undefined;
  }
</script>

<div class="strip">
  <button class="person" onclick={onEdit}>
    <span class="bubble" class:empty={!tracker.own}>{tracker.own?.text ?? "Přidat stav"}</span>
    <Avatar id={userId} {name} size={50} aura={online && tracker.privacy.sharePresence ? "online" : "own"} />
    <span class="name">Ty</span>
  </button>
  {#each people as person (person.id)}
    <div class="person">
      <span class="bubble">{person.status}</span>
      <Avatar id={person.id} name={person.name} url={person.avatar} size={50} aura={person.aura} />
      <span class="name">{person.name}</span>
    </div>
  {/each}
</div>

<style>
  .strip {
    display: flex;
    gap: 14px;
    padding: 4px 18px 14px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .person {
    flex: none;
    width: 86px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 0;
    border: 0;
    background: none;
    color: inherit;
  }

  .bubble {
    max-width: 100%;
    min-height: 2.6em;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    padding: 3px 8px;
    border-radius: 10px;
    background: var(--surface);
    border: 1px solid var(--line);
    font-size: 0.7rem;
    line-height: 1.25;
    text-align: center;
    overflow-wrap: anywhere;
  }

  .bubble.empty {
    color: var(--accent-soft);
    border-style: dashed;
  }

  .name {
    max-width: 100%;
    font-size: 0.75rem;
    color: var(--ink-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
</style>
