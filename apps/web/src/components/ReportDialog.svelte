<script lang="ts">
  import { onMount } from "svelte";

  let { onSend, onClose }: { onSend: (reason: string) => void; onClose: () => void } = $props();

  let reason = $state("");
  let dialog: HTMLDialogElement;

  onMount(() => dialog.showModal());

  function close() {
    dialog.close();
    onClose();
  }

  function send(event: SubmitEvent) {
    event.preventDefault();
    onSend(reason.trim());
    close();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <form onsubmit={send}>
    <h2>Nahlásit zprávu</h2>
    <p>Zprávu uvidí správce serveru. Můžeš připsat, co je špatně.</p>
    <textarea bind:value={reason} maxlength="500" rows="3" placeholder="Důvod (nepovinné)"></textarea>
    <div class="buttons">
      <button type="button" onclick={close}>Zrušit</button>
      <button type="submit" class="danger">Nahlásit</button>
    </div>
  </form>
</dialog>

<style>
  dialog {
    width: min(420px, calc(100vw - 32px));
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
    margin: 0 0 8px;
    font-family: var(--display);
    font-size: 1.2rem;
  }

  p {
    margin: 0 0 12px;
    color: var(--ink-muted);
  }

  textarea {
    width: 100%;
    padding: 10px 12px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--bg);
    color: var(--ink);
    font: inherit;
    resize: vertical;
    outline: none;
  }

  textarea:focus {
    border-color: var(--accent);
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 16px;
  }

  .buttons button {
    padding: 9px 16px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .buttons .danger {
    border-color: transparent;
    background: #dc2626;
    color: #fff;
  }
</style>
