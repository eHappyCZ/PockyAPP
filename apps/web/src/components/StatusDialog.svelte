<script lang="ts">
  import { onMount } from "svelte";
  import type { PresenceTracker } from "../lib/presence.svelte";
  import { errorText } from "../lib/text";

  let { tracker, onClose }: { tracker: PresenceTracker; onClose: () => void } = $props();

  const suggestions = ["🍺 Jdu ven", "💻 V práci", "😴 Spím", "🎮 Hraju", "📵 Nerušit", "🏖 Na dovolené"];
  const durations = [
    { id: "hour", label: "1 hodinu" },
    { id: "four", label: "4 hodiny" },
    { id: "today", label: "Dnes" },
    { id: "day", label: "24 hodin" },
    { id: "forever", label: "Do zrušení" },
  ] as const;

  let text = $state("");
  let duration = $state<(typeof durations)[number]["id"]>("today");
  let busy = $state(false);
  let error = $state("");
  let dialog: HTMLDialogElement;

  const valid = $derived(text.trim().length > 0 && [...text.trim()].length <= 60);

  onMount(() => {
    text = tracker.own?.text ?? "";
    dialog.showModal();
  });

  function until(): number | null {
    const now = Date.now();
    switch (duration) {
      case "hour":
        return now + 3_600_000;
      case "four":
        return now + 4 * 3_600_000;
      case "today": {
        const end = new Date();
        end.setHours(24, 0, 0, 0);
        return end.getTime();
      }
      case "day":
        return now + 24 * 3_600_000;
      default:
        return null;
    }
  }

  async function run(action: () => Promise<void>) {
    busy = true;
    error = "";
    try {
      await action();
      close();
    } catch (err) {
      error = err instanceof Error && err.name === "invalid_name"
        ? "Stav nesmí být prázdný ani delší než 60 znaků."
        : errorText(err);
    } finally {
      busy = false;
    }
  }

  function save(event: SubmitEvent) {
    event.preventDefault();
    if (valid) run(() => tracker.setStatus(text.trim(), until()));
  }

  function close() {
    dialog.close();
    onClose();
  }
</script>

<dialog bind:this={dialog} oncancel={(e) => { e.preventDefault(); close(); }}>
  <form onsubmit={save}>
    <h2>Tvůj stav</h2>
    <p>Uvidí ho lidé, se kterými si píšeš.</p>

    <input bind:value={text} maxlength="80" placeholder="Co je nového?" disabled={busy} />

    <div class="chips">
      {#each suggestions as suggestion (suggestion)}
        <button type="button" class:on={text === suggestion} onclick={() => (text = suggestion)}>{suggestion}</button>
      {/each}
    </div>

    <h3>Platí</h3>
    <div class="chips">
      {#each durations as option (option.id)}
        <button type="button" class:on={duration === option.id} onclick={() => (duration = option.id)}>{option.label}</button>
      {/each}
    </div>

    {#if error}<p class="error" role="alert">{error}</p>{/if}

    <div class="buttons">
      {#if tracker.own}
        <button type="button" class="clear" onclick={() => run(() => tracker.clearStatus())} disabled={busy}>Smazat stav</button>
      {/if}
      <span class="space"></span>
      <button type="button" onclick={close}>Zrušit</button>
      <button type="submit" class="primary" disabled={!valid || busy}>Uložit</button>
    </div>
  </form>
</dialog>

<style>
  dialog {
    width: min(440px, calc(100vw - 32px));
    padding: 22px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: var(--surface);
    color: var(--ink);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.6);
  }

  h2 {
    margin: 0 0 4px;
    font-family: var(--display);
    font-size: 1.3rem;
  }

  h3 {
    margin: 16px 0 8px;
    font-size: 0.85rem;
    color: var(--ink-muted);
    font-weight: 600;
  }

  p {
    margin: 0 0 14px;
    color: var(--ink-muted);
  }

  input {
    width: 100%;
    height: 46px;
    padding: 0 14px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--bg);
    outline: none;
    margin-bottom: 10px;
  }

  input:focus {
    border-color: var(--accent);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chips button {
    padding: 6px 11px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: none;
    font-size: 0.88rem;
  }

  .chips button.on {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 18%, transparent);
  }

  .error {
    margin: 12px 0 0;
    color: var(--danger);
  }

  .buttons {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 20px;
  }

  .space {
    flex: 1;
  }

  .buttons button {
    padding: 9px 14px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 600;
  }

  .buttons .primary {
    border-color: transparent;
    background: var(--accent);
    color: #fff;
  }

  .buttons .clear {
    color: var(--danger);
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
