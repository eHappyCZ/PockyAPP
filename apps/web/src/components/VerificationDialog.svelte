<script lang="ts">
  import { onMount } from "svelte";
  import type { Verification } from "../lib/core";
  import { emojiNames } from "../lib/emoji";
  import { shortName } from "../lib/text";
  import type { VerificationStep } from "../lib/types";
  import Icon from "./Icon.svelte";

  let { verification, onClose }: { verification: Verification; onClose: () => void } = $props();

  let step = $state<VerificationStep>({ step: "requested" });
  let busy = $state(false);
  let dialog: HTMLDialogElement;

  const finished = $derived(step.step === "done" || step.step === "cancelled");
  const own = $derived(verification.isSelfVerification());
  const other = $derived(shortName(verification.otherUserId()));

  onMount(() => {
    if (verification.isIncoming()) step = { step: "incoming" };
    dialog.showModal();
    verification.watch((value: VerificationStep) => (step = value)).catch(() => undefined);
  });

  async function act(action: () => Promise<void>) {
    busy = true;
    try {
      await action();
    } catch {
      step = { step: "cancelled", byUs: true, reason: "" };
    } finally {
      busy = false;
    }
  }

  function close() {
    if (!finished) verification.cancel().catch(() => undefined);
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <header>
    <Icon name={own ? "device" : "fingerprint"} />
    <h2>{own ? "Ověření zařízení" : `Ověření: ${other}`}</h2>
  </header>

  {#if step.step === "requested"}
    <p>
      {own
        ? "Otevři jiné přihlášené zařízení (např. PockyAPP v telefonu nebo Element) a přijmi žádost o ověření."
        : `Čekám, až ${other} přijme žádost o ověření ve vašem chatu. Nejlíp, když jste spolu nebo si voláte.`}
    </p>
    <div class="buttons"><button onclick={close}>Zrušit</button></div>
  {:else if step.step === "incoming"}
    <p>{own ? "Jiné tvoje zařízení žádá o ověření." : `${other} tě chce ověřit. Porovnáte spolu stejné symboly.`}</p>
    <div class="buttons">
      <button onclick={close} disabled={busy}>Odmítnout</button>
      <button class="primary" onclick={() => act(() => verification.accept())} disabled={busy}>Přijmout</button>
    </div>
  {:else if step.step === "ready" || step.step === "starting"}
    <p>{own ? "Čekám na druhé zařízení…" : `Čekám na ${other}…`}</p>
    <div class="buttons"><button onclick={close}>Zrušit</button></div>
  {:else if step.step === "emojis"}
    <p>
      {own
        ? "Zkontroluj, že se na obou zařízeních zobrazují stejné symboly ve stejném pořadí."
        : `Porovnej s ${other}, že vidíte stejné symboly ve stejném pořadí.`}
    </p>
    <ul class="emojis">
      {#each step.emojis as emoji (emoji.index)}
        <li><span>{emoji.symbol}</span><small>{emojiNames[emoji.index] ?? emoji.description}</small></li>
      {/each}
    </ul>
    <div class="buttons">
      <button class="danger" onclick={() => act(() => verification.mismatch())} disabled={busy}>Neshodují se</button>
      <button class="primary" onclick={() => act(() => verification.confirm())} disabled={busy}>Shodují se</button>
    </div>
  {:else if step.step === "confirmed"}
    <p>{own ? "Čekám na potvrzení na druhém zařízení…" : `Čekám, až potvrdí i ${other}…`}</p>
  {:else if step.step === "done"}
    <p class="done"><Icon name="check-circle" /> <strong>{own ? "Zařízení je ověřené" : `${other} je ověřený kontakt`}</strong></p>
    <p>
      {own
        ? "Klíče k šifrovaným zprávám se přenesou z druhého zařízení."
        : "Když se mu někdy změní šifrovací identita, PockyAPP tě upozorní."}
    </p>
    <div class="buttons"><button class="primary" onclick={close}>Zavřít</button></div>
  {:else}
    <p><strong>Ověření bylo zrušeno</strong></p>
    <div class="buttons"><button onclick={close}>Zavřít</button></div>
  {/if}
</dialog>

<style>
  dialog {
    width: min(440px, calc(100vw - 32px));
    padding: 24px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.65);
  }

  header {
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--accent-soft);
    margin-bottom: 10px;
  }

  h2 {
    margin: 0;
    font-family: var(--display);
    font-size: 1.3rem;
    color: var(--ink);
  }

  p {
    margin: 0 0 16px;
    color: var(--ink-muted);
  }

  p strong {
    color: var(--ink);
  }

  .done {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--success);
  }

  .emojis {
    list-style: none;
    margin: 0 0 20px;
    padding: 0;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px 6px;
  }

  .emojis li {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    text-align: center;
  }

  .emojis span {
    font-size: 2rem;
    line-height: 1.1;
  }

  .emojis small {
    font-size: 0.75rem;
    color: var(--ink-muted);
  }

  .buttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  button {
    padding: 10px 16px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .primary {
    border-color: transparent;
    background: var(--accent);
    color: #fff;
  }

  .danger {
    color: var(--danger);
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
