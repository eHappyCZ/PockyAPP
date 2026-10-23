<script lang="ts">
  import { onDestroy, tick } from "svelte";
  import type { RoomTimeline } from "../lib/core";
  import { canRecord, VoiceRecorder, type Recording } from "../lib/recorder";
  import { errorText } from "../lib/text";
  import type { Draft } from "../lib/types";
  import { videoMeta } from "../lib/video";
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

  interface Attachment {
    id: number;
    file: File;
    kind: "image" | "video" | "file";
    preview?: string;
  }

  const maxAttachments = 10;

  let text = $state("");
  let attachments = $state<Attachment[]>([]);
  let fullQuality = $state(false);
  let busy = $state(false);
  let progress = $state("");
  let error = $state("");
  let input = $state<HTMLTextAreaElement>();
  let picker = $state<HTMLInputElement>();
  let nextId = 0;

  let recorder: VoiceRecorder | null = null;
  let recording = $state(false);
  let recordedMs = $state(0);
  let liveLevels = $state<number[]>([]);
  let voice = $state<Recording | null>(null);
  let voiceUrl = $state<string | null>(null);
  let player: HTMLAudioElement | null = null;
  let playing = $state(false);

  const hasImages = $derived(attachments.some((item) => item.kind === "image"));
  const canSend = $derived(
    !busy && !recording && timeline !== null && (text.trim().length > 0 || attachments.length > 0 || voice !== null),
  );
  const showMic = $derived(
    canRecord && !recording && !voice && attachments.length === 0 && text.trim().length === 0 && draft === null,
  );

  $effect(() => {
    if (!draft) return;
    if (draft.mode === "edit") text = draft.text;
    tick().then(() => {
      input?.focus();
      resize();
    });
  });

  onDestroy(() => {
    recorder?.cancel();
    discardVoice();
    clearAttachments();
  });

  function resize() {
    if (!input) return;
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 180)}px`;
  }

  function kindOf(file: File): Attachment["kind"] {
    if (/^image\/(jpeg|png|gif|webp)$/.test(file.type)) return "image";
    if (/^video\//.test(file.type)) return "video";
    return "file";
  }

  function pick(event: Event) {
    const target = event.currentTarget as HTMLInputElement;
    const chosen = Array.from(target.files ?? []);
    target.value = "";
    const room = maxAttachments - attachments.length;
    if (chosen.length > room) error = `Najednou jde poslat nejvýš ${maxAttachments} souborů.`;
    for (const file of chosen.slice(0, room)) {
      const kind = kindOf(file);
      attachments.push({ id: nextId++, file, kind, preview: kind === "image" ? URL.createObjectURL(file) : undefined });
    }
    input?.focus();
  }

  function removeAttachment(id: number) {
    const item = attachments.find((value) => value.id === id);
    if (item?.preview) URL.revokeObjectURL(item.preview);
    attachments = attachments.filter((value) => value.id !== id);
  }

  function clearAttachments() {
    for (const item of attachments) if (item.preview) URL.revokeObjectURL(item.preview);
    attachments = [];
  }

  async function sendAttachments(target: RoomTimeline, body: string) {
    let caption: string | undefined = body || undefined;
    const total = attachments.length;
    for (const [index, item] of attachments.entries()) {
      progress = total > 1 ? `Posílám ${index + 1} z ${total}…` : "Posílám…";
      const bytes = new Uint8Array(await item.file.arrayBuffer());
      if (item.kind === "image") {
        await target.sendImage(bytes, caption, fullQuality);
        caption = undefined;
      } else if (item.kind === "video") {
        const meta = await videoMeta(item.file).catch(() => ({ width: 0, height: 0, durationMs: 0, thumbnail: undefined }));
        await target.sendVideo(bytes, item.file.type, meta.width, meta.height, meta.durationMs, meta.thumbnail, caption);
        caption = undefined;
      } else {
        await target.sendFile(bytes, item.file.name, item.file.type || "application/octet-stream");
      }
      removeAttachment(item.id);
    }
    if (caption) await target.sendText(caption);
  }

  async function send() {
    if (!canSend || !timeline) return;
    busy = true;
    error = "";
    const body = text.trim();
    try {
      if (voice) {
        await timeline.sendVoice(voice.bytes, voice.mime, voice.durationMs, new Float32Array(voice.levels));
        discardVoice();
      } else if (attachments.length > 0) {
        await sendAttachments(timeline, body);
        fullQuality = false;
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
      progress = "";
      input?.focus();
    }
  }

  async function startRecording() {
    error = "";
    recorder = new VoiceRecorder();
    liveLevels = [];
    recordedMs = 0;
    try {
      await recorder.start((level, elapsed) => {
        liveLevels = [...liveLevels.slice(-39), level];
        recordedMs = elapsed;
      });
      recording = true;
    } catch {
      recorder.cancel();
      recorder = null;
      error = "Mikrofon není dostupný. Povol ho v nastavení prohlížeče.";
    }
  }

  async function stopRecording() {
    const active = recorder;
    recorder = null;
    recording = false;
    if (!active) return;
    const result = await active.stop();
    if (!result || result.durationMs < 700) {
      error = result ? "Nahrávka je moc krátká." : "";
      return;
    }
    voice = result;
    voiceUrl = URL.createObjectURL(new Blob([result.bytes as Uint8Array<ArrayBuffer>], { type: result.mime }));
  }

  function cancelRecording() {
    recorder?.cancel();
    recorder = null;
    recording = false;
  }

  function discardVoice() {
    player?.pause();
    player = null;
    playing = false;
    if (voiceUrl) URL.revokeObjectURL(voiceUrl);
    voiceUrl = null;
    voice = null;
  }

  function togglePlayback() {
    if (!voiceUrl) return;
    if (!player) {
      player = new Audio(voiceUrl);
      player.onplay = () => (playing = true);
      player.onpause = () => (playing = false);
      player.onended = () => (playing = false);
    }
    if (playing) player.pause();
    else player.play().catch(() => (playing = false));
  }

  function clock(ms: number): string {
    const seconds = Math.floor(ms / 1000);
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
  }

  function size(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} kB`;
    return `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
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

  {#if attachments.length > 0}
    <div class="tray">
      {#each attachments as item (item.id)}
        <div class="item">
          {#if item.preview}
            <img src={item.preview} alt="" />
          {:else}
            <span class="tile"><Icon name={item.kind === "video" ? "video" : "file"} size={22} /></span>
          {/if}
          <span class="label">{item.kind === "image" ? size(item.file.size) : item.file.name}</span>
          <button type="button" class="drop" onclick={() => removeAttachment(item.id)} disabled={busy} aria-label="Odebrat">
            <Icon name="close" size={14} />
          </button>
        </div>
      {/each}
    </div>
    <div class="options">
      {#if hasImages}
        <label class="quality">
          <input type="checkbox" bind:checked={fullQuality} disabled={busy} />
          Plná kvalita fotek
        </label>
      {/if}
      <span>{progress || "Text napsaný níž bude popisek."}</span>
    </div>
  {/if}

  {#if error}<p class="error" role="alert">{error}</p>{/if}

  {#if recording}
    <div class="bar">
      <button type="button" class="ghost" onclick={cancelRecording} aria-label="Zahodit"><Icon name="trash" /></button>
      <div class="voice live">
        <span class="dot"></span>
        <span class="wave">
          {#each liveLevels as level, i (i)}
            <i style:height="{Math.max(10, Math.round(level * 100))}%"></i>
          {/each}
        </span>
        <span class="time">{clock(recordedMs)}</span>
      </div>
      <button type="button" class="send" onclick={stopRecording} aria-label="Dokončit nahrávání"><Icon name="stop" /></button>
    </div>
  {:else if voice}
    <div class="bar">
      <button type="button" class="ghost" onclick={discardVoice} disabled={busy} aria-label="Zahodit"><Icon name="trash" /></button>
      <div class="voice">
        <button type="button" class="play" onclick={togglePlayback} aria-label={playing ? "Pozastavit" : "Přehrát"}>
          <Icon name={playing ? "pause" : "play"} size={18} />
        </button>
        <span class="caption">Hlasová zpráva</span>
        <span class="time">{clock(voice.durationMs)}</span>
      </div>
      <button type="button" class="send" onclick={send} disabled={!canSend} aria-label="Odeslat"><Icon name="send" /></button>
    </div>
  {:else}
    <div class="bar">
      <button
        type="button"
        class="ghost"
        onclick={() => picker?.click()}
        disabled={busy || draft?.mode === "edit" || attachments.length >= maxAttachments}
        aria-label="Přiložit"
      >
        <Icon name="images" />
      </button>
      {#if onLocation}
        <button type="button" class="ghost" onclick={onLocation} disabled={busy || draft !== null || timeline === null} aria-label="Poslat polohu">
          <Icon name="location" />
        </button>
      {/if}
      <input bind:this={picker} type="file" multiple hidden onchange={pick} />
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
      {#if showMic}
        <button type="button" class="send" onclick={startRecording} disabled={timeline === null} aria-label="Nahrát hlasovou zprávu">
          <Icon name="mic" />
        </button>
      {:else}
        <button type="button" class="send" onclick={send} disabled={!canSend} aria-label="Odeslat">
          <Icon name="send" />
        </button>
      {/if}
    </div>
  {/if}
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

  .tray {
    display: flex;
    gap: 8px;
    padding-bottom: 6px;
    overflow-x: auto;
  }

  .item {
    position: relative;
    flex: none;
    width: 76px;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .item img,
  .tile {
    width: 76px;
    height: 76px;
    border-radius: 10px;
    object-fit: cover;
  }

  .tile {
    display: grid;
    place-items: center;
    background: var(--surface);
    border: 1px solid var(--line);
    color: var(--accent-soft);
  }

  .label {
    font-size: 0.72rem;
    color: var(--ink-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .drop {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: rgb(0 0 0 / 0.65);
    color: #fff;
  }

  .options {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 8px;
    font-size: 0.8rem;
    color: var(--ink-muted);
  }

  .quality {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--ink);
    cursor: pointer;
  }

  .quality input {
    accent-color: var(--accent);
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

  .voice {
    flex: 1;
    min-width: 0;
    height: 44px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 14px 0 6px;
    border-radius: 22px;
    border: 1px solid var(--line);
    background: var(--surface);
  }

  .voice.live {
    padding-left: 16px;
    border-color: color-mix(in srgb, #f87171 50%, transparent);
  }

  .dot {
    flex: none;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #f87171;
    animation: pulse 1s ease-in-out infinite alternate;
  }

  .wave {
    flex: 1;
    min-width: 0;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 2px;
    overflow: hidden;
  }

  .wave i {
    flex: none;
    width: 3px;
    border-radius: 2px;
    background: var(--accent-soft);
  }

  .play {
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--accent) 25%, transparent);
    color: var(--ink);
  }

  .caption {
    flex: 1;
    color: var(--ink-muted);
    font-size: 0.9rem;
  }

  .time {
    font-variant-numeric: tabular-nums;
    font-size: 0.85rem;
    color: var(--ink-muted);
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

  @keyframes pulse {
    from {
      opacity: 0.35;
    }
    to {
      opacity: 1;
    }
  }
</style>
