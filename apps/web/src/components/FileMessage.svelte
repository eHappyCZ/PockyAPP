<script lang="ts">
  import { download } from "../lib/media";
  import { fileSize } from "../lib/text";
  import Icon from "./Icon.svelte";

  let { name, source, size, mime }: { name: string; source: string; size?: number; mime?: string } = $props();

  let busy = $state(false);
  let failed = $state(false);

  async function save(event: MouseEvent) {
    event.stopPropagation();
    if (busy) return;
    busy = true;
    failed = false;
    try {
      await download(source, name, mime);
    } catch {
      failed = true;
    } finally {
      busy = false;
    }
  }
</script>

<button type="button" class="file" onclick={save} disabled={busy}>
  <span class="icon"><Icon name={busy ? "clock" : "download"} size={20} /></span>
  <span class="text">
    <strong>{name}</strong>
    <small>{failed ? "Soubor se nepodařilo stáhnout." : busy ? "Stahuji…" : fileSize(size)}</small>
  </span>
</button>

<style>
  .file {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 200px;
    max-width: 320px;
    padding: 2px 0;
    border: 0;
    background: none;
    text-align: left;
    color: inherit;
  }

  .icon {
    flex: none;
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    background: color-mix(in srgb, #000 25%, transparent);
  }

  .text {
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .text strong {
    font-weight: 600;
    overflow-wrap: anywhere;
  }

  .text small {
    opacity: 0.75;
  }
</style>
