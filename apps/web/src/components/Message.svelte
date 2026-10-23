<script lang="ts">
  import { messageHtml } from "../lib/format";
  import { contentText, previewContent, shieldText, shortName, timeOfDay } from "../lib/text";
  import type { EventEntry, MessageActions } from "../lib/types";
  import AudioMessage from "./AudioMessage.svelte";
  import Avatar from "./Avatar.svelte";
  import FileMessage from "./FileMessage.svelte";
  import Icon from "./Icon.svelte";
  import LocationPreview from "./LocationPreview.svelte";
  import MediaImage from "./MediaImage.svelte";
  import VideoMessage from "./VideoMessage.svelte";

  let {
    event,
    first,
    showSender,
    actions,
  }: { event: EventEntry; first: boolean; showSender: boolean; actions: MessageActions } = $props();

  const quickReactions = ["❤️", "👍", "😂", "😮", "😢", "🔥"];

  let tools = $state(false);

  const sender = $derived(event.senderName ?? shortName(event.senderId));
  const c = $derived(event.content);
  const quiet = $derived(c.kind === "redacted" || c.kind === "unableToDecrypt");
  const visual = $derived(
    c.kind === "image" ||
      c.kind === "sticker" ||
      c.kind === "video" ||
      c.kind === "location" ||
      (c.kind === "liveLocation" && !!c.geoUri),
  );
  const textual = $derived(c.kind === "text" || c.kind === "notice" || c.kind === "emote");
  const canReact = $derived(!quiet && event.eventId !== undefined);

  function copy() {
    navigator.clipboard?.writeText(contentText(c)).catch(() => undefined);
    tools = false;
  }

  function run(action: () => void) {
    action();
    tools = false;
  }
</script>

<div class="message" class:own={event.isOwn} class:first class:tools role="group" onmouseleave={() => (tools = false)}>
  {#if showSender && !event.isOwn}
    <span class="avatar">
      {#if first}<Avatar id={event.senderId} name={sender} url={event.senderAvatar} size={32} />{/if}
    </span>
  {/if}

  <div class="column">
    {#if showSender && first && !event.isOwn}
      <span class="sender">{sender}</span>
    {/if}

    <div class="bubble" class:quiet class:visual onclick={() => (tools = !tools)} role="presentation">
      {#if canReact}
        <div class="toolbar">
          {#each quickReactions as key (key)}
            <button type="button" class="emoji" onclick={(e) => { e.stopPropagation(); run(() => actions.react(event, key)); }}>{key}</button>
          {/each}
          <span class="sep"></span>
          {#if event.canReply}
            <button type="button" title="Odpovědět" onclick={(e) => { e.stopPropagation(); run(() => actions.reply(event)); }}><Icon name="reply" size={17} /></button>
          {/if}
          {#if event.canEdit && textual}
            <button type="button" title="Upravit" onclick={(e) => { e.stopPropagation(); run(() => actions.edit(event)); }}><Icon name="edit" size={17} /></button>
          {/if}
          {#if textual}
            <button type="button" title="Kopírovat" onclick={(e) => { e.stopPropagation(); copy(); }}><Icon name="copy" size={17} /></button>
          {/if}
          <button type="button" title="Přeposlat" onclick={(e) => { e.stopPropagation(); run(() => actions.forward(event)); }}><Icon name="forward" size={17} /></button>
          <button type="button" title="Informace o zprávě" onclick={(e) => { e.stopPropagation(); run(() => actions.info(event)); }}><Icon name="info" size={17} /></button>
          {#if event.canRedact}
            <button type="button" class="danger" title="Smazat" onclick={(e) => { e.stopPropagation(); run(() => actions.remove(event)); }}><Icon name="trash" size={17} /></button>
          {/if}
        </div>
      {/if}
      {#if event.inReplyTo}
        <div class="reply">
          <strong>{event.inReplyTo.senderName ?? (event.inReplyTo.senderId ? shortName(event.inReplyTo.senderId) : "")}</strong>
          <span>{event.inReplyTo.content ? previewContent(event.inReplyTo.content) : "Zpráva"}</span>
        </div>
      {/if}

      {#if c.kind === "image"}
        <button
          type="button"
          class="open"
          aria-label="Zvětšit fotku"
          onclick={(e) => {
            e.stopPropagation();
            actions.openImage(event);
          }}
        >
          <MediaImage source={c.source} width={c.width} height={c.height} alt={c.caption ?? ""} />
        </button>
        {#if c.caption}<p class="body">{c.caption}</p>{/if}
      {:else if c.kind === "sticker"}
        <MediaImage source={c.source} width={c.width} height={c.height} alt={c.body} />
      {:else if c.kind === "location"}
        <LocationPreview geoUri={c.geoUri} description={c.description} onOpen={(point) => actions.openLocation(point, sender)} />
      {:else if c.kind === "liveLocation" && c.geoUri}
        <LocationPreview geoUri={c.geoUri} live={c.isLive} onOpen={(point) => actions.openLocation(point, sender)} />
      {:else if c.kind === "file"}
        <FileMessage name={c.name} source={c.source} size={c.size} mime={c.mime} />
      {:else if c.kind === "video"}
        <VideoMessage source={c.source} thumbnail={c.thumbnail} width={c.width} height={c.height} durationMs={c.durationMs} />
        {#if c.caption}<p class="body">{c.caption}</p>{/if}
      {:else if c.kind === "audio"}
        <AudioMessage source={c.source} durationMs={c.durationMs} waveform={c.waveform} caption={c.caption} voice={c.voice} />
      {:else if c.kind === "text" || c.kind === "notice"}
        <div class="body rich" class:formatted={!!c.html}>{@html messageHtml(c.body, c.html)}</div>
      {:else if c.kind === "emote"}
        <div class="body rich" class:formatted={!!c.html}>* {sender} {@html messageHtml(c.body, c.html)}</div>
      {:else}
        <p class="body">{contentText(c)}</p>
      {/if}

      <span class="meta">
        {#if event.shield}
          <span class="shield" class:danger={event.shield.danger} title={shieldText(event.shield)}>
            <Icon name="shield-alert" size={13} />
          </span>
        {/if}
        {#if event.isEdited}<span>upraveno</span>{/if}
        <span>{timeOfDay(event.timestampMs)}</span>
        {#if event.isOwn}
          {#if event.sendState === "sending"}
            <Icon name="clock" size={13} />
          {:else if event.sendState === "failed"}
            <span class="failed">Neodesláno</span>
          {:else}
            <span class="tick" class:read={event.readBy.length > 0}>{event.readBy.length > 0 ? "✓✓" : "✓"}</span>
          {/if}
        {/if}
      </span>
    </div>

    {#if event.reactions.length > 0}
      <div class="reactions">
        {#each event.reactions as reaction (reaction.key)}
          <button type="button" class="reaction" class:mine={reaction.reactedByMe} onclick={() => actions.react(event, reaction.key)}>
            {reaction.key}{#if reaction.count > 1}<b>{reaction.count}</b>{/if}
          </button>
        {/each}
      </div>
    {/if}
  </div>
</div>

<style>
  .message {
    display: flex;
    align-items: flex-end;
    gap: 8px;
    padding: 1px 16px;
  }

  .message.first {
    margin-top: 8px;
  }

  .own {
    justify-content: flex-end;
  }

  .avatar {
    width: 32px;
    flex: none;
  }

  .column {
    display: flex;
    flex-direction: column;
    max-width: min(560px, 78%);
  }

  .own .column {
    align-items: flex-end;
  }

  .sender {
    margin: 0 0 3px 12px;
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--accent-soft);
  }

  .bubble {
    position: relative;
    padding: 8px 12px 6px;
    border-radius: 18px;
    background: var(--surface);
    border: 1px solid var(--line);
    min-width: 72px;
  }

  .own .bubble {
    background: var(--bubble-out);
    border-color: transparent;
  }

  .first:not(.own) .bubble {
    border-top-left-radius: 6px;
  }

  .own.first .bubble {
    border-top-right-radius: 6px;
  }

  .bubble.visual {
    padding: 4px 4px 6px;
  }

  .bubble.quiet .body {
    font-style: italic;
    color: var(--ink-muted);
  }

  .own .bubble.quiet .body {
    color: color-mix(in srgb, #fff 75%, transparent);
  }

  .body {
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .open {
    display: block;
    padding: 0;
    border: 0;
    background: none;
    cursor: zoom-in;
  }

  .rich.formatted {
    white-space: normal;
  }

  .rich :global(a) {
    color: inherit;
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  .rich :global(p),
  .rich :global(ul),
  .rich :global(ol),
  .rich :global(blockquote),
  .rich :global(pre) {
    margin: 0 0 6px;
  }

  .rich :global(:last-child) {
    margin-bottom: 0;
  }

  .rich :global(ul),
  .rich :global(ol) {
    padding-left: 1.3em;
  }

  .rich :global(blockquote) {
    padding-left: 10px;
    border-left: 3px solid color-mix(in srgb, currentColor 40%, transparent);
    opacity: 0.85;
  }

  .rich :global(code) {
    padding: 1px 5px;
    border-radius: 5px;
    background: color-mix(in srgb, #000 30%, transparent);
    font-size: 0.9em;
  }

  .rich :global(pre) {
    padding: 8px 10px;
    border-radius: 8px;
    background: color-mix(in srgb, #000 30%, transparent);
    overflow-x: auto;
    white-space: pre;
  }

  .rich :global(pre code) {
    padding: 0;
    background: none;
  }

  .rich :global(h1),
  .rich :global(h2),
  .rich :global(h3) {
    margin: 0 0 4px;
    font-size: 1.05em;
  }

  .visual .body {
    padding: 6px 8px 0;
  }

  .reply {
    display: flex;
    flex-direction: column;
    margin: 2px 0 6px;
    padding: 4px 10px;
    border-left: 3px solid var(--accent-soft);
    border-radius: 6px;
    background: color-mix(in srgb, #000 22%, transparent);
    font-size: 0.85rem;
  }

  .reply strong {
    color: var(--accent-soft);
  }

  .reply span {
    color: var(--ink-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .own .reply span {
    color: color-mix(in srgb, #fff 75%, transparent);
  }

  .meta {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 5px;
    margin-top: 2px;
    font-size: 0.7rem;
    color: var(--ink-muted);
  }

  .visual .meta {
    padding: 0 8px;
  }

  .own .meta {
    color: color-mix(in srgb, #fff 70%, transparent);
  }

  .shield {
    display: inline-flex;
    color: var(--gold);
  }

  .shield.danger {
    color: #f87171;
  }

  .tick.read {
    color: var(--success);
    font-weight: 700;
  }

  .failed {
    color: #fca5a5;
    font-weight: 700;
  }

  .reactions {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: -4px;
    padding: 0 8px;
  }

  .reaction {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 1px 7px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--line);
    font-size: 0.85rem;
  }

  .reaction {
    font: inherit;
    color: inherit;
    cursor: pointer;
  }

  .toolbar {
    position: absolute;
    bottom: calc(100% + 4px);
    left: 0;
    z-index: 2;
    display: none;
    align-items: center;
    gap: 2px;
    padding: 4px;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--line);
    box-shadow: 0 10px 30px -10px #000;
    flex-wrap: wrap;
    width: max-content;
    max-width: calc(100vw - 40px);
  }

  .own .toolbar {
    left: auto;
    right: 0;
  }

  .message:hover .toolbar,
  .tools .toolbar {
    display: flex;
  }

  .toolbar button {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border: 0;
    border-radius: 8px;
    background: none;
    color: var(--ink-muted);
  }

  .toolbar button:hover {
    background: color-mix(in srgb, var(--ink) 8%, transparent);
    color: var(--ink);
  }

  .toolbar .emoji {
    font-size: 1.1rem;
  }

  .toolbar .danger:hover {
    color: #f87171;
  }

  .sep {
    width: 1px;
    height: 20px;
    margin: 0 4px;
    background: var(--line);
  }

  .reaction.mine {
    border-color: var(--accent);
  }

  .reaction b {
    font-size: 0.75rem;
    color: var(--ink-muted);
  }
</style>
