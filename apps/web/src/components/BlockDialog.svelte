<script lang="ts">
  import { onMount } from "svelte";

  let { name, onConfirm, onClose }: { name: string; onConfirm: () => void; onClose: () => void } = $props();

  let dialog: HTMLDialogElement;

  onMount(() => dialog.showModal());

  function close() {
    dialog.close();
    onClose();
  }

  function confirm() {
    onConfirm();
    close();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <h2>Blokovat {name}?</h2>
  <p>Jeho zprávy ani pozvánky neuvidíš. Blokování zrušíš ve svém profilu.</p>
  <div class="buttons">
    <button type="button" onclick={close}>Zrušit</button>
    <button type="button" class="danger" onclick={confirm}>Blokovat</button>
  </div>
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
    overflow-wrap: anywhere;
  }

  p {
    margin: 0;
    color: var(--ink-muted);
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 18px;
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
