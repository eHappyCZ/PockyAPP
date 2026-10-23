<script lang="ts">
  import { previewContent, shortName } from "../lib/text";
  import type { PinnedMessage } from "../lib/types";
  import Icon from "./Icon.svelte";

  let {
    messages,
    canPin,
    onUnpin,
  }: { messages: PinnedMessage[]; canPin: boolean; onUnpin: (eventId: string) => void } = $props();

  let dialog = $state<HTMLDialogElement>();

  const latest = $derived(messages[messages.length - 1]);

  function author(message: PinnedMessage): string {
    return message.senderName ?? shortName(message.senderId);
  }
</script>

{#if latest}
  <button type="button" class="bar" onclick={() => dialog?.showModal()}>
    <Icon name="pin" size={16} />
    <span class="text">
      <strong>{author(latest)}</strong>
      <span>{previewContent(latest.content)}</span>
    </span>
    {#if messages.length > 1}<span class="count">{messages.length} připnutých</span>{/if}
  </button>
{/if}

<dialog bind:this={dialog}>
  <h2>Připnuté zprávy</h2>
  {#if messages.length === 0}
    <p class="empty">Zatím tu nic není připnuté.</p>
  {/if}
  <ul>
    {#each [...messages].reverse() as message (message.eventId)}
      <li>
        <div>
          <strong>{author(message)}</strong>
          <span>{previewContent(message.content)}</span>
        </div>
        {#if canPin}
          <button type="button" class="unpin" onclick={() => onUnpin(message.eventId)}>Odepnout</button>
        {/if}
      </li>
    {/each}
  </ul>
  <div class="buttons">
    <button type="button" onclick={() => dialog?.close()}>Zavřít</button>
  </div>
</dialog>

<style>
  .bar {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px 16px;
    border: 0;
    border-bottom: 1px solid var(--line);
    background: var(--surface);
    color: var(--accent-soft);
    text-align: left;
  }

  .text {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
    font-size: 0.85rem;
  }

  .text strong {
    font-size: 0.78rem;
  }

  .text span {
    color: var(--ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .count {
    color: var(--ink-muted);
    font-size: 0.75rem;
  }

  dialog {
    width: min(460px, calc(100vw - 32px));
    padding: 20px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  h2 {
    margin: 0 0 12px;
    font-family: var(--display);
    font-size: 1.2rem;
  }

  .empty {
    color: var(--ink-muted);
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    max-height: 50vh;
    overflow-y: auto;
  }

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 0;
    border-bottom: 1px solid var(--line);
  }

  li div {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
  }

  li strong {
    color: var(--accent-soft);
    font-size: 0.8rem;
  }

  li span {
    overflow-wrap: anywhere;
  }

  .unpin {
    padding: 6px 10px;
    border-radius: 8px;
    border: 1px solid var(--line);
    background: none;
    color: var(--danger);
    font-weight: 600;
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .buttons button {
    padding: 9px 16px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }
</style>
