<script lang="ts">
  import { onDestroy } from "svelte";
  import { mediaUrl } from "../lib/media";
  import Icon from "./Icon.svelte";

  let {
    source,
    durationMs,
    waveform,
    caption,
    voice,
  }: { source: string; durationMs?: number; waveform: number[]; caption: string; voice: boolean } = $props();

  const bars = 40;
  let audio: HTMLAudioElement | null = null;
  let playing = $state(false);
  let progress = $state(0);
  let busy = $state(false);
  let failed = $state(false);
  let elapsed = $state(0);

  const levels = $derived.by(() => {
    if (waveform.length === 0) return Array.from({ length: bars }, (_, i) => 0.3 + 0.25 * Math.abs(Math.sin(i * 1.7)));
    const max = Math.max(...waveform, 1);
    return Array.from({ length: bars }, (_, i) => {
      const value = waveform[Math.floor((i / bars) * waveform.length)] ?? 0;
      return Math.max(0.12, value / max);
    });
  });

  function clock(ms: number): string {
    const seconds = Math.round(ms / 1000);
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
  }

  async function toggle(event: MouseEvent) {
    event.stopPropagation();
    if (audio) {
      if (playing) audio.pause();
      else audio.play().catch(() => (failed = true));
      return;
    }
    busy = true;
    failed = false;
    try {
      audio = new Audio(await mediaUrl(source));
      audio.ontimeupdate = () => {
        elapsed = audio!.currentTime * 1000;
        progress = audio!.duration ? audio!.currentTime / audio!.duration : 0;
      };
      audio.onplay = () => (playing = true);
      audio.onpause = () => (playing = false);
      audio.onended = () => {
        playing = false;
        progress = 0;
        elapsed = 0;
      };
      await audio.play();
    } catch {
      failed = true;
      playing = false;
      audio = null;
    } finally {
      busy = false;
    }
  }

  onDestroy(() => audio?.pause());
</script>

<div class="audio">
  <button type="button" class="toggle" onclick={toggle} disabled={busy} aria-label={playing ? "Pozastavit" : "Přehrát"}>
    <Icon name={busy ? "clock" : playing ? "pause" : "play"} size={20} />
  </button>
  <div class="track">
    {#if voice}
      <span class="wave">
        {#each levels as level, i (i)}
          <i class:done={i / bars < progress} style:height="{Math.round(level * 100)}%"></i>
        {/each}
      </span>
    {:else}
      <span class="name">{caption}</span>
    {/if}
    <small>{failed ? "Nahrávku se nepodařilo přehrát." : clock(playing || elapsed > 0 ? elapsed : (durationMs ?? 0))}</small>
  </div>
</div>

<style>
  .audio {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 220px;
  }

  .toggle {
    flex: none;
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border: 0;
    border-radius: 50%;
    background: color-mix(in srgb, #fff 18%, transparent);
    color: inherit;
  }

  .track {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .wave {
    display: flex;
    align-items: center;
    gap: 2px;
    height: 28px;
  }

  .wave i {
    flex: 1;
    min-height: 3px;
    border-radius: 2px;
    background: color-mix(in srgb, currentColor 40%, transparent);
  }

  .wave i.done {
    background: currentColor;
  }

  .name {
    overflow-wrap: anywhere;
  }

  small {
    opacity: 0.75;
    font-size: 0.75rem;
  }
</style>
