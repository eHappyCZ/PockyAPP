<script lang="ts">
  import { mediaUrl } from "../lib/media";
  import Icon from "./Icon.svelte";
  import MediaImage from "./MediaImage.svelte";

  let {
    source,
    thumbnail,
    width,
    height,
    durationMs,
  }: { source: string; thumbnail?: string; width?: number; height?: number; durationMs?: number } = $props();

  let url = $state<string | null>(null);
  let busy = $state(false);
  let failed = $state(false);

  const duration = $derived(
    durationMs ? `${Math.floor(durationMs / 60000)}:${String(Math.floor((durationMs / 1000) % 60)).padStart(2, "0")}` : "",
  );
  const ratio = $derived(width && height ? width / height : 16 / 9);

  async function play(event: MouseEvent) {
    event.stopPropagation();
    if (busy) return;
    busy = true;
    failed = false;
    try {
      url = await mediaUrl(source);
    } catch {
      failed = true;
    } finally {
      busy = false;
    }
  }
</script>

{#if url}
  <!-- svelte-ignore a11y_media_has_caption -->
  <video src={url} controls autoplay playsinline style:aspect-ratio={ratio}></video>
{:else}
  <button type="button" class="poster" onclick={play} style:aspect-ratio={ratio}>
    {#if thumbnail}<MediaImage source={thumbnail} {width} {height} />{/if}
    <span class="play">
      <Icon name={busy ? "clock" : "play"} size={26} />
    </span>
    {#if duration}<span class="time">{duration}</span>{/if}
    {#if failed}<span class="error">Video se nepodařilo načíst.</span>{/if}
  </button>
{/if}

<style>
  video,
  .poster {
    display: block;
    width: 320px;
    max-width: 100%;
    max-height: 420px;
    border-radius: 12px;
    background: #000;
  }

  .poster {
    position: relative;
    display: grid;
    place-items: center;
    padding: 0;
    border: 0;
    overflow: hidden;
    color: #fff;
  }

  .poster :global(.media) {
    position: absolute;
    inset: 0;
    width: 100% !important;
    aspect-ratio: auto !important;
    border-radius: 0;
  }

  .play {
    position: relative;
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: rgb(0 0 0 / 0.55);
    border: 2px solid rgb(255 255 255 / 0.8);
  }

  .time {
    position: absolute;
    right: 8px;
    bottom: 8px;
    padding: 1px 6px;
    border-radius: 6px;
    background: rgb(0 0 0 / 0.6);
    font-size: 0.75rem;
  }

  .error {
    position: absolute;
    bottom: 8px;
    left: 8px;
    font-size: 0.8rem;
  }
</style>
