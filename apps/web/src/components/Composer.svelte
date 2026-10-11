<script lang="ts">
  import { tick } from "svelte";
  import type { RoomTimeline } from "../lib/core";
  import { errorText } from "../lib/text";
  import type { Draft } from "../lib/types";
  import Icon from "./Icon.svelte";

  let {
    timeline,
    draft,
    onCancel,
    onTyping,
    onLocation,
  }: {
    timeline: RoomTimeline | null;
    draft: Draft | null;
    onCancel: () => void;
    onTyping: (active: boolean) => void;
    onLocation?: () => void;
  } = $props();

  let text = $state("");
  let file = $state<File | null>(null);
  let preview = $state<string | null>(null);
  let busy = $state(false);
  let error = $state("");
  let input: HTMLTextAreaElement;
  let picker: HTMLInputElement;

  const isImage = $derived(file !== null && /^image\/(jpeg|png|gif|webp)$/.test(file.type));
  const canSend = $derived(!busy && timeline !== null && (text.trim().length > 0 || file !== null));

  $effect(() => {
    if (!draft) return;
    if (draft.mode === "edit") text = draft.text;
    tick().then(() => {
      input?.focus();
      resize();
    });
  });

  function resize() {
    if (!input) return;
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 180)}px`;
  }

  function pick(event: Event) {
    const chosen = (event.currentTarget as HTMLInputElement).files?.[0] ?? null;
    (event.currentTarget as HTMLInputElement).value = "";
    clearFile();
    file = chosen;
    if (chosen && /^image\//.test(chosen.type)) preview = URL.createObjectURL(chosen);
    input?.focus();
  }

  function clearFile() {
    if (preview) URL.revokeObjectURL(preview);
    preview = null;
    file = null;
  }

  async function send() {
    if (!canSend || !timeline) return;
    busy = true;
    error = "";
    const body = text.trim();
    try {
      if (file) {
        const bytes = new Uint8Array(await file.arrayBuffer());
        if (isImage) {
          await timeline.sendImage(bytes, body || undefined, false);
        } else {
          await timeline.sendFile(bytes, file.name, file.type || "application/octet-stream");
          if (body) await timeline.sendText(body);
        }
        clearFile();
      } else if (draft?.mode === "edit") {
        await timeline.editText(draft.itemKey, body);
      } else if (draft?.mode === "reply" && draft.eventId) {
        await timeline.sendReply(body, draft.eventId);
      } else {
        await timeline.sendText(body);
      }
      text = "";
      onTyping(false);
      if (draft) onCancel();
      await tick();
      resize();
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
      input?.focus();
    }
  }

  function keydown(event: KeyboardEvent) {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      send();
    } else if (event.key === "Escape" && draft) {
      if (draft.mode === "edit") text = "";
      onCancel();
    }
  }

  function cancel() {
    if (draft?.mode === "edit") text = "";
    onCancel();
  }
</script>

<div class="composer">
  {#if draft}
    <div class="context">
      <Icon name={draft.mode === "edit" ? "edit" : "reply"} size={16} />
      <span class="what">
        <strong>{draft.mode === "edit" ? "Úprava zprávy" : `Odpověď pro ${draft.sender}`}</strong>
        <span>{draft.text}</span>
      </span>
      <button type="button" class="ghost" onclick={cancel} aria-label="Zrušit"><Icon name="close" size={18} /></button>
    </div>
  {/if}

  {#if file}
    <div class="context">
      {#if preview}<img src={preview} alt="" />{:else}<Icon name="file" size={18} />{/if}
      <span class="what">
        <strong>{isImage ? "Poslat fotku" : file.name}</strong>
        <span>{isImage ? "Text napsaný níž bude popisek." : "Soubor se pošle samostatně."}</span>
      </span>
      <button type="button" class="ghost" onclick={clearFile} aria-label="Zrušit"><Icon name="close" size={18} /></button>
    </div>
  {/if}

  {#if error}<p class="error" role="alert">{error}</p>{/if}

  <div class="bar">
    <button type="button" class="ghost" onclick={() => picker.click()} disabled={busy || draft?.mode === "edit"} aria-label="Přiložit">
      <Icon name="images" />
    </button>
    {#if onLocation}
      <button type="button" class="ghost" onclick={onLocation} disabled={busy || draft !== null || timeline === null} aria-label="Poslat polohu">
        <Icon name="location" />
      </button>
    {/if}
    <input bind:this={picker} type="file" hidden onchange={pick} />
    <textarea
      bind:this={input}
      bind:value={text}
      rows="1"
      placeholder="Napiš zprávu…"
      oninput={() => {
        resize();
        onTyping(text.trim().length > 0);
      }}
      onblur={() => onTyping(false)}
      onkeydown={keydown}
      disabled={timeline === null}
    ></textarea>
    <button type="button" class="send" onclick={send} disabled={!canSend} aria-label="Odeslat">
      <Icon name="send" />
    </button>
  </div>
</div>

<style>
  .composer {
    border-top: 1px solid var(--line);
    padding: 10px 12px 12px;
  }

  .context {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
    padding: 6px 6px 6px 12px;
    border-left: 3px solid var(--accent);
    border-radius: 8px;
    background: var(--surface);
    color: var(--accent-soft);
  }

  .context img {
    width: 44px;
    height: 44px;
    border-radius: 8px;
    object-fit: cover;
  }

  .what {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    font-size: 0.85rem;
  }

  .what strong {
    color: var(--ink);
  }

  .what span {
    color: var(--ink-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .bar {
    display: flex;
    align-items: flex-end;
    gap: 8px;
  }

  textarea {
    flex: 1;
    min-height: 44px;
    max-height: 180px;
    padding: 11px 14px;
    border-radius: 22px;
    border: 1px solid var(--line);
    background: var(--surface);
    resize: none;
    outline: none;
    line-height: 1.4;
  }

  textarea:focus {
    border-color: var(--accent);
  }

  button {
    flex: none;
    display: grid;
    place-items: center;
    border: 0;
    background: none;
  }

  .ghost {
    width: 40px;
    height: 44px;
    color: var(--ink-muted);
  }

  .context .ghost {
    height: 32px;
    width: 32px;
  }

  .send {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: var(--accent);
    color: #fff;
  }

  button:disabled {
    opacity: 0.4;
    cursor: default;
  }

  .error {
    margin: 0 0 8px;
    color: #f87171;
    font-size: 0.85rem;
  }
</style>
