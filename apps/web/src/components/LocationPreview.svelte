<script lang="ts">
  import { mapsEnabled } from "../lib/config";
  import { parseGeoUri } from "../lib/core";
  import { formatRadius, isArea, metersPerPixel, previewZoom } from "../lib/geo";
  import type { GeoPoint } from "../lib/types";
  import Icon from "./Icon.svelte";

  let {
    geoUri,
    live = false,
    description,
    onOpen,
  }: { geoUri: string; live?: boolean; description?: string; onOpen: (point: GeoPoint) => void } = $props();

  const width = 240;
  const height = 150;

  let point = $state<GeoPoint | null>(null);
  let image = $state<string | null>(null);

  const area = $derived(point ? isArea(point.accuracyM) : false);
  const zoom = $derived(point ? previewZoom(point.latitude, point.accuracyM, width) : 15);
  const circle = $derived(point && area ? (2 * point.accuracyM!) / metersPerPixel(point.latitude, zoom) : 0);
  const title = $derived(
    live ? "Živá poloha" : point && area ? `Přibližně · okolí ${formatRadius(point.accuracyM!)}` : "Poloha",
  );

  $effect(() => {
    let alive = true;
    image = null;
    parseGeoUri(geoUri).then((value) => {
      if (!alive || !value) return;
      point = value;
      if (!mapsEnabled) return;
      import("../lib/map")
        .then(({ mapSnapshot }) => mapSnapshot(value.longitude, value.latitude, previewZoom(value.latitude, value.accuracyM, width)))
        .then(
        (url) => alive && (image = url),
        () => undefined,
      );
    });
    return () => (alive = false);
  });
</script>

<button type="button" class="location" onclick={() => point && onOpen(point)} disabled={!point}>
  <span class="map" style:width="{width}px" style:height="{height}px">
    {#if image}<img src={image} alt="" />{/if}
    {#if area}
      <span class="circle" style:width="{circle}px" style:height="{circle}px"></span>
      <span class="dot"></span>
    {:else if point}
      <span class="pin"><Icon name="location" size={32} /></span>
    {/if}
  </span>
  <span class="title">
    {#if live}<i class="live"></i>{/if}
    {title}
  </span>
  {#if description}<span class="description">{description}</span>{/if}
</button>

<style>
  .location {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding: 0;
    border: 0;
    background: none;
    text-align: left;
    color: inherit;
  }

  .map {
    position: relative;
    display: grid;
    place-items: center;
    max-width: 100%;
    border-radius: 14px;
    overflow: hidden;
    background: var(--bg);
  }

  .map img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .circle {
    position: absolute;
    border-radius: 50%;
    background: color-mix(in srgb, var(--accent) 22%, transparent);
    border: 2px solid var(--accent);
    max-width: 400%;
  }

  .dot {
    position: absolute;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--accent);
    border: 2px solid #fff;
  }

  .pin {
    position: relative;
    top: -14px;
    color: var(--accent);
    filter: drop-shadow(0 2px 3px #000);
  }

  .title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
    padding: 0 6px;
  }

  .live {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--success);
  }

  .description {
    padding: 0 6px;
    font-size: 0.9rem;
  }
</style>
