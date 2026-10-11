<script lang="ts">
  import { mediaUrl } from "../lib/media";

  let {
    source,
    width,
    height,
    alt = "",
  }: { source: string; width?: number; height?: number; alt?: string } = $props();

  const maxSide = 320;
  const ratio = $derived(width && height ? width / height : 4 / 3);
  const box = $derived(
    ratio >= 1
      ? { w: maxSide, h: Math.round(maxSide / ratio) }
      : { w: Math.round(maxSide * ratio), h: maxSide },
  );

  let url = $state<string | null>(null);
  let failed = $state(false);

  $effect(() => {
    url = null;
    failed = false;
    let alive = true;
    mediaUrl(source, 640, 640).then(
      (value) => alive && (url = value),
      () => alive && (failed = true),
    );
    return () => (alive = false);
  });
</script>

<span class="media" style:width="{box.w}px" style:aspect-ratio="{box.w} / {box.h}">
  {#if url}
    <img src={url} {alt} />
  {:else if failed}
    <span class="note">Obrázek se nepodařilo načíst.</span>
  {/if}
</span>

<style>
  .media {
    display: grid;
    place-items: center;
    max-width: 100%;
    border-radius: 12px;
    overflow: hidden;
    background: color-mix(in srgb, var(--ink-muted) 12%, transparent);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  .note {
    padding: 12px;
    font-size: 0.85rem;
    color: var(--ink-muted);
  }
</style>
