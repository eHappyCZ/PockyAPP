<script lang="ts">
  import { onMount } from "svelte";
  import type { PockyClient } from "../lib/core";

  let {
    client,
    onConfirm,
    onClose,
  }: { client: PockyClient; onConfirm: () => void; onClose: () => void } = $props();

  let danger = $state(false);
  let dialog: HTMLDialogElement;

  onMount(() => {
    dialog.showModal();
    client
      .logoutWarning()
      .then((warning: { losesHistory: boolean }) => (danger = warning.losesHistory))
      .catch(() => undefined);
  });

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  {#if danger}
    <h2 class="warn">Ztratíš přístup k šifrovaným zprávám</h2>
    <p>Toto je tvoje jediné zařízení a klíče nejsou zálohované. Po odhlášení už starší šifrované zprávy nepůjde přečíst.</p>
  {:else}
    <h2>Odhlásit se?</h2>
    <p>V tomto prohlížeči se smažou všechna data aplikace.</p>
  {/if}
  <div class="buttons">
    <button type="button" onclick={close}>Zrušit</button>
    <button type="button" class="danger" onclick={onConfirm}>{danger ? "Přesto odhlásit" : "Odhlásit"}</button>
  </div>
</dialog>

<style>
  dialog {
    width: min(400px, calc(100vw - 32px));
    padding: 22px;
    border: 1px solid var(--line);
    border-radius: 18px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  h2 {
    margin: 0 0 6px;
    font-family: var(--display);
    font-size: 1.25rem;
  }

  .warn {
    color: var(--danger);
  }

  p {
    margin: 0 0 18px;
    color: var(--ink-muted);
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  button {
    padding: 9px 16px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .danger {
    border-color: transparent;
    background: #dc2626;
    color: #fff;
  }
</style>
