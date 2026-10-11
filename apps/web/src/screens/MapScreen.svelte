<script lang="ts">
  import type { Map as MapLibre, Marker } from "maplibre-gl";
  import { onMount } from "svelte";
  import Avatar from "../components/Avatar.svelte";
  import Icon from "../components/Icon.svelte";
  import MapView from "../components/MapView.svelte";
  import { parseGeoUri, type PockyClient } from "../lib/core";
  import { mediaUrl } from "../lib/media";
  import { shortName, timeOfDay } from "../lib/text";
  import type { GeoPoint, LiveLocation } from "../lib/types";

  let {
    client,
    onBack,
    onOpenRoom,
  }: { client: PockyClient; onBack: () => void; onOpenRoom: (roomId: string) => void } = $props();

  interface Person extends LiveLocation {
    point?: GeoPoint;
  }

  let shares = $state<Person[]>([]);
  let now = $state(Date.now());
  let map = $state.raw<MapLibre | null>(null);
  const markers = new Map<string, Marker>();

  const live = $derived(shares.filter((share) => share.endsMs > now));
  const others = $derived(live.filter((share) => !share.isOwn));
  const own = $derived(live.filter((share) => share.isOwn));

  function key(share: LiveLocation): string {
    return `${share.roomId}|${share.userId}`;
  }

  function label(share: LiveLocation): string {
    return share.name ?? shortName(share.userId);
  }

  function ago(updatedMs?: number): string {
    if (!updatedMs) return "Čeká na polohu";
    const minutes = Math.floor((now - updatedMs) / 60_000);
    if (minutes < 1) return "Právě teď";
    if (minutes < 60) return `Před ${minutes} min`;
    return `Před ${Math.floor(minutes / 60)} h`;
  }

  function peopleText(count: number): string {
    if (count === 0) return "Teď s tebou nikdo nesdílí polohu";
    if (count === 1) return "1 člověk s tebou sdílí polohu";
    if (count <= 4) return `${count} lidé s tebou sdílí polohu`;
    return `${count} lidí s tebou sdílí polohu`;
  }

  function ownText(count: number): string {
    return count === 1 ? "Sdílíš polohu v 1 chatu" : count <= 4 ? `Sdílíš polohu ve ${count} chatech` : `Sdílíš polohu v ${count} chatech`;
  }

  function markerElement(share: LiveLocation): HTMLElement {
    const element = document.createElement("div");
    element.className = "pocky-person";
    element.textContent = label(share).slice(0, 2).toUpperCase();
    if (share.avatarUrl) {
      mediaUrl(share.avatarUrl, 96, 96).then(
        (url) => {
          const image = document.createElement("img");
          image.src = url;
          image.alt = "";
          element.replaceChildren(image);
        },
        () => undefined,
      );
    }
    return element;
  }

  let MarkerClass: typeof Marker | null = null;

  async function sync() {
    if (!map) return;
    MarkerClass ??= (await import("../lib/map")).Marker;
    const seen = new Set<string>();
    for (const share of others) {
      if (!share.point) continue;
      const id = key(share);
      seen.add(id);
      const position: [number, number] = [share.point.longitude, share.point.latitude];
      const marker = markers.get(id);
      if (marker) marker.setLngLat(position);
      else markers.set(id, new MarkerClass({ element: markerElement(share) }).setLngLat(position).addTo(map));
    }
    for (const [id, marker] of markers) {
      if (!seen.has(id)) {
        marker.remove();
        markers.delete(id);
      }
    }
  }

  $effect(() => {
    others;
    sync();
  });

  function focus(share: Person) {
    if (share.point) map?.flyTo({ center: [share.point.longitude, share.point.latitude], zoom: 14 });
  }

  function stopAll() {
    for (const share of own) client.stopLiveLocation(share.roomId).catch(() => undefined);
  }

  onMount(() => {
    client
      .watchLiveLocations(async (value: LiveLocation[]) => {
        shares = await Promise.all(
          value.map(async (share) => ({ ...share, point: share.geoUri ? await parseGeoUri(share.geoUri) : undefined })),
        );
      })
      .catch(() => undefined);
    const clock = setInterval(() => (now = Date.now()), 30_000);
    return () => {
      clearInterval(clock);
      for (const marker of markers.values()) marker.remove();
    };
  });
</script>

<section class="screen">
  <header>
    <button class="back" onclick={onBack} aria-label="Zpět"><Icon name="arrow-back" /></button>
    <div>
      <h1>Mapa</h1>
      <span>{peopleText(others.length)}</span>
    </div>
  </header>

  <div class="body">
    <MapView onReady={(created) => { map = created; sync(); }} />

    <div class="panel">
      {#if own.length > 0}
        <div class="own">
          <span class="dot"></span>
          <span>{ownText(own.length)}</span>
          <button onclick={stopAll}>Ukončit vše</button>
        </div>
      {/if}

      {#if others.length === 0}
        <div class="empty">
          <strong>Živou polohu tu uvidíš</strong>
          <p>Když s tebou někdo začne sdílet polohu, objeví se tady. Svou živou polohu pošleš z telefonu: v chatu přes přílohy, Poloha, Sdílet živou polohu.</p>
        </div>
      {:else}
        <ul>
          {#each others as share (key(share))}
            <li>
              <button class="person" onclick={() => focus(share)}>
                <Avatar id={share.roomId} name={label(share)} url={share.avatarUrl} size={40} aura="online" />
                <span class="text">
                  <strong>{label(share)}</strong>
                  <small>{ago(share.updatedMs)} · do {timeOfDay(share.endsMs)}</small>
                </span>
              </button>
              <button class="write" onclick={() => onOpenRoom(share.roomId)}>Napsat</button>
            </li>
          {/each}
        </ul>
      {/if}
    </div>
  </div>
</section>

<style>
  .screen {
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

  header div {
    display: flex;
    flex-direction: column;
  }

  h1 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.3rem;
  }

  header span {
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .back {
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    border: 0;
    border-radius: 10px;
    background: none;
  }

  .body {
    position: relative;
    flex: 1;
    min-height: 0;
  }

  .panel {
    position: absolute;
    left: 12px;
    top: 12px;
    width: min(320px, calc(100% - 24px));
    max-height: calc(100% - 24px);
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .own,
  .empty,
  ul {
    border-radius: 16px;
    background: color-mix(in srgb, var(--surface) 94%, transparent);
    border: 1px solid var(--line);
    box-shadow: 0 12px 30px -12px #000;
  }

  .own {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    font-size: 0.9rem;
  }

  .own span:nth-child(2) {
    flex: 1;
  }

  .own button {
    padding: 6px 10px;
    border-radius: 10px;
    border: 0;
    background: #dc2626;
    color: #fff;
    font-weight: 700;
  }

  .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--success);
  }

  .empty {
    padding: 14px;
  }

  .empty p {
    margin: 6px 0 0;
    font-size: 0.86rem;
    color: var(--ink-muted);
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 6px;
  }

  li {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .person {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px;
    border: 0;
    border-radius: 12px;
    background: none;
    text-align: left;
  }

  .person:hover {
    background: color-mix(in srgb, var(--ink) 6%, transparent);
  }

  .text {
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .text small {
    color: var(--ink-muted);
  }

  .write {
    padding: 7px 11px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
    font-size: 0.85rem;
  }
</style>
