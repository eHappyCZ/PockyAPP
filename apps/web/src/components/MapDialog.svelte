<script lang="ts">
  import type { Map as MapLibre } from "maplibre-gl";
  import { onMount } from "svelte";
  import { formatRadius, isArea, previewZoom } from "../lib/geo";
  import type { GeoPoint } from "../lib/types";
  import Icon from "./Icon.svelte";
  import MapView from "./MapView.svelte";

  let { point, title, onClose }: { point: GeoPoint; title: string; onClose: () => void } = $props();

  let dialog: HTMLDialogElement;
  const area = $derived(isArea(point.accuracyM));

  onMount(() => dialog.showModal());

  async function ready(map: MapLibre) {
    const { Marker, pinElement, showArea } = await import("../lib/map");
    if (area) {
      showArea(map, "shared-area", point);
    } else {
      new Marker({ element: pinElement() }).setLngLat([point.longitude, point.latitude]).addTo(map);
    }
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    <div>
      <strong>{title}</strong>
      <span>{area ? `Přibližně · okolí ${formatRadius(point.accuracyM!)}` : `${point.latitude.toFixed(5)}, ${point.longitude.toFixed(5)}`}</span>
    </div>
    <button onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
  </header>
  <div class="map">
    <MapView
      center={[point.longitude, point.latitude]}
      zoom={previewZoom(point.latitude, point.accuracyM, 420) - 0.5}
      onReady={ready}
    />
  </div>
</dialog>

<style>
  dialog {
    width: min(760px, calc(100vw - 32px));
    height: min(620px, calc(100vh - 64px));
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
    overflow: hidden;
  }

  dialog[open] {
    display: flex;
    flex-direction: column;
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.65);
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 12px 12px 18px;
    border-bottom: 1px solid var(--line);
  }

  header div {
    display: flex;
    flex-direction: column;
  }

  header span {
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  header button {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 10px;
    background: none;
  }

  .map {
    flex: 1;
    min-height: 0;
  }
</style>
