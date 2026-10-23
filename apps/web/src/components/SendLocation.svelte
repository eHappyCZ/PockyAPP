<script lang="ts">
  import type { Map as MapLibre } from "maplibre-gl";
  import { onMount } from "svelte";
  import type { RoomTimeline } from "../lib/core";
  import { metersPerPixel } from "../lib/geo";
  import { errorText } from "../lib/text";
  import type { LocationPrecision } from "../lib/types";
  import Icon from "./Icon.svelte";
  import MapView from "./MapView.svelte";

  let {
    timeline,
    initialPrecision,
    onClose,
  }: { timeline: RoomTimeline; initialPrecision: LocationPrecision; onClose: () => void } = $props();

  const options: { id: LocationPrecision; label: string; radius: number }[] = [
    { id: "exact", label: "Přesně", radius: 0 },
    { id: "neighborhood", label: "Okolí ~500 m", radius: 500 },
    { id: "city", label: "Město ~5 km", radius: 5000 },
  ];

  let precision = $state<LocationPrecision>("city");
  let map = $state.raw<MapLibre | null>(null);
  let circle = $state(0);
  let accuracy: number | undefined;
  let busy = $state(false);
  let locating = $state(false);
  let error = $state("");
  let dialog: HTMLDialogElement;

  const radius = $derived(options.find((o) => o.id === precision)!.radius);

  onMount(() => {
    precision = initialPrecision;
    dialog.showModal();
    locate();
  });

  function measure() {
    if (!map) return;
    const { lat } = map.getCenter();
    circle = radius > 0 ? (2 * radius) / metersPerPixel(lat, map.getZoom()) : 0;
  }

  $effect(() => {
    radius;
    measure();
  });

  function ready(created: MapLibre) {
    map = created;
    created.on("move", measure);
    created.on("dragstart", () => (accuracy = undefined));
    measure();
  }

  function locate() {
    if (!navigator.geolocation) {
      error = "Prohlížeč neumí zjistit polohu. Posuň mapu na místo.";
      return;
    }
    locating = true;
    error = "";
    navigator.geolocation.getCurrentPosition(
      (position) => {
        locating = false;
        accuracy = position.coords.accuracy;
        map?.flyTo({ center: [position.coords.longitude, position.coords.latitude], zoom: precision === "city" ? 11 : 15 });
      },
      (err) => {
        locating = false;
        error =
          err.code === err.PERMISSION_DENIED
            ? "Bez povolení polohy stačí posunout mapu na místo."
            : "Polohu se nepodařilo zjistit. Posuň mapu na místo.";
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 },
    );
  }

  async function send() {
    if (!map || busy) return;
    busy = true;
    error = "";
    const { lng, lat } = map.getCenter();
    try {
      await timeline.sendLocation(lat, lng, accuracy, precision);
      close();
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
    }
  }

  function close() {
    dialog.close();
    onClose();
  }

</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    <strong>Poslat polohu</strong>
    <button class="icon" onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
  </header>

  <div class="map">
    <MapView onReady={ready} />
    {#if circle > 0}
      <span class="area" style:width="{circle}px" style:height="{circle}px"></span>
      <span class="dot"></span>
    {:else}
      <span class="pin"><span class="pocky-pin"></span></span>
    {/if}
    <button class="locate" onclick={locate} disabled={locating}>
      <Icon name="locate" size={18} />{locating ? "Hledám…" : "Moje poloha"}
    </button>
  </div>

  <footer>
    <div class="chips">
      {#each options as option (option.id)}
        <button class:on={precision === option.id} onclick={() => (precision = option.id)}>{option.label}</button>
      {/each}
    </div>
    <p class="hint">
      {precision === "exact" ? "Pošle se místo pod špendlíkem." : "Pošle se jen oblast v kruhu, ne přesné místo."}
    </p>
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    <button class="send" onclick={send} disabled={!map || busy}>{busy ? "Posílám…" : "Poslat polohu"}</button>
  </footer>
</dialog>

<style>
  dialog {
    width: min(620px, calc(100vw - 32px));
    height: min(720px, calc(100vh - 48px));
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
    padding: 10px 10px 10px 18px;
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

  .map {
    position: relative;
    flex: 1;
    min-height: 0;
    display: grid;
    place-items: center;
  }

  .map > :global(.map) {
    position: absolute;
    inset: 0;
  }

  .area {
    position: absolute;
    border-radius: 50%;
    background: color-mix(in srgb, var(--accent) 20%, transparent);
    border: 2px solid var(--accent);
    pointer-events: none;
  }

  .dot {
    position: absolute;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--accent);
    border: 2px solid #fff;
    pointer-events: none;
  }

  .pin {
    position: absolute;
    transform: translateY(-14px);
    pointer-events: none;
  }

  .locate {
    position: absolute;
    right: 12px;
    bottom: 12px;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 9px 14px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: var(--surface);
    font-weight: 600;
  }

  footer {
    padding: 14px 16px 16px;
    border-top: 1px solid var(--line);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chips button {
    padding: 7px 12px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: none;
    font-size: 0.9rem;
  }

  .chips button.on {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 18%, transparent);
  }

  .hint {
    margin: 10px 0;
    font-size: 0.85rem;
    color: var(--ink-muted);
  }

  .error {
    margin: 0 0 10px;
    color: var(--danger);
    font-size: 0.85rem;
  }

  .send {
    width: 100%;
    height: 46px;
    border: 0;
    border-radius: 12px;
    background: var(--accent);
    color: #fff;
    font-weight: 700;
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
