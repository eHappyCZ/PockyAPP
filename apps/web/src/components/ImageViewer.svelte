<script lang="ts">
  import { onMount } from "svelte";
  import { mediaUrl } from "../lib/media";
  import { dayLabel, shortName, timeOfDay } from "../lib/text";
  import type { EventEntry } from "../lib/types";
  import Icon from "./Icon.svelte";

  let { images, start, onClose }: { images: EventEntry[]; start: string; onClose: () => void } = $props();

  let index = $state(0);
  let url = $state<string | null>(null);
  let failed = $state(false);
  let dialog: HTMLDialogElement;

  const current = $derived(images[index]);
  const content = $derived(current?.content.kind === "image" ? current.content : null);
  const sender = $derived(current ? (current.senderName ?? shortName(current.senderId)) : "");

  onMount(() => {
    index = Math.max(0, images.findIndex((image) => image.itemKey === start));
    dialog.showModal();
  });

  $effect(() => {
    const source = content?.source;
    url = null;
    failed = false;
    if (!source) return;
    let alive = true;
    mediaUrl(source).then(
      (value) => alive && (url = value),
      () => alive && (failed = true),
    );
    return () => (alive = false);
  });

  function go(step: number) {
    const next = index + step;
    if (next >= 0 && next < images.length) index = next;
  }

  function keydown(event: KeyboardEvent) {
    if (event.key === "ArrowLeft") go(-1);
    else if (event.key === "ArrowRight") go(1);
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<svelte:window onkeydown={keydown} />

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    <div class="who">
      <strong>{sender}</strong>
      {#if current}<span>{dayLabel(current.timestampMs)} · {timeOfDay(current.timestampMs)}</span>{/if}
    </div>
    <span class="count">{index + 1} / {images.length}</span>
    {#if url}
      <a class="tool" href={url} download="pockyapp-foto" aria-label="Stáhnout"><Icon name="download" /></a>
    {/if}
    <button class="tool" onclick={close} aria-label="Zavřít"><Icon name="close" /></button>
  </header>

  <div class="stage" role="presentation" onclick={(e) => e.target === e.currentTarget && close()}>
    {#if url}
      <img src={url} alt={content?.caption ?? ""} />
    {:else if failed}
      <p>Fotku se nepodařilo načíst.</p>
    {:else}
      <p>Načítám…</p>
    {/if}

    {#if index > 0}
      <button class="nav prev" onclick={() => go(-1)} aria-label="Předchozí"><Icon name="arrow-back" /></button>
    {/if}
    {#if index < images.length - 1}
      <button class="nav next" onclick={() => go(1)} aria-label="Další"><Icon name="arrow-back" /></button>
    {/if}
  </div>

  {#if content?.caption}<p class="caption">{content.caption}</p>{/if}
</dialog>

<style>
  dialog {
    width: 100vw;
    height: 100vh;
    max-width: none;
    max-height: none;
    margin: 0;
    padding: 0;
    border: 0;
    background: #050409;
    color: var(--ink);
  }

  dialog[open] {
    display: flex;
    flex-direction: column;
  }

  dialog::backdrop {
    background: transparent;
  }

  header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px 10px 18px;
  }

  .who {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .who span,
  .count {
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .count {
    margin-right: 8px;
  }

  .tool {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border: 0;
    border-radius: 12px;
    background: none;
    color: var(--ink);
  }

  .tool:hover {
    background: color-mix(in srgb, var(--ink) 10%, transparent);
  }

  .stage {
    position: relative;
    flex: 1;
    min-height: 0;
    display: grid;
    place-items: center;
    padding: 0 64px 16px;
  }

  img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    border-radius: 6px;
  }

  .stage p {
    color: var(--ink-muted);
  }

  .nav {
    position: absolute;
    top: 50%;
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    margin-top: -22px;
    border: 1px solid var(--line);
    border-radius: 50%;
    background: color-mix(in srgb, var(--surface) 85%, transparent);
    color: var(--ink);
  }

  .prev {
    left: 12px;
  }

  .next {
    right: 12px;
    transform: scaleX(-1);
  }

  .caption {
    margin: 0;
    padding: 0 18px 18px;
    text-align: center;
    white-space: pre-wrap;
  }

  @media (max-width: 640px) {
    .stage {
      padding: 0 0 12px;
    }

    .nav {
      top: auto;
      bottom: 16px;
      margin-top: 0;
    }
  }
</style>
