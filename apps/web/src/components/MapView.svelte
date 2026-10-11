<script lang="ts">
  import type { Map as MapLibre } from "maplibre-gl";
  import { onMount } from "svelte";

  let {
    center,
    zoom,
    onReady,
  }: { center?: [number, number]; zoom?: number; onReady?: (map: MapLibre) => void } = $props();

  let container: HTMLDivElement;
  let failed = $state(false);

  onMount(() => {
    let map: MapLibre | null = null;
    let alive = true;
    import("../lib/map")
      .then(({ createMap }) => createMap(container, { center, zoom }))
      .then((created) => {
        if (!alive) {
          created.remove();
          return;
        }
        map = created;
        created.once("load", () => onReady?.(created));
      })
      .catch(() => (failed = true));
    const observer = new ResizeObserver(() => map?.resize());
    observer.observe(container);
    return () => {
      alive = false;
      observer.disconnect();
      map?.remove();
    };
  });
</script>

<div class="map" bind:this={container}>
  {#if failed}<p>Mapu se nepodařilo načíst.</p>{/if}
</div>

<style>
  .map {
    position: relative;
    width: 100%;
    height: 100%;
    background: var(--bg);
  }

  p {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    margin: 0;
    color: var(--ink-muted);
  }
</style>
