<script lang="ts">
  import { onMount } from "svelte";
  import Avatar from "../components/Avatar.svelte";
  import Icon from "../components/Icon.svelte";
  import type { PockyClient } from "../lib/core";
  import { errorText, shortName } from "../lib/text";
  import type { Invite, Room } from "../lib/types";

  let { client, room, onBack }: { client: PockyClient; room: Room; onBack: () => void } = $props();

  let invite = $state<Invite | null>(null);
  let busy = $state(false);
  let error = $state("");

  const inviter = $derived(invite ? (invite.inviterName ?? shortName(invite.inviterId)) : "");

  onMount(() => {
    client.inviteInfo(room.id).then((value: Invite) => (invite = value)).catch(() => undefined);
  });

  async function act(accept: boolean) {
    busy = true;
    error = "";
    try {
      if (accept) await client.acceptInvite(room.id);
      else {
        await client.declineInvite(room.id);
        onBack();
      }
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
    }
  }
</script>

<section class="invite">
  <header>
    <button class="back" onclick={onBack} aria-label="Zpět"><Icon name="arrow-back" /></button>
  </header>
  <div class="card">
    <Avatar id={room.id} name={room.name} url={room.avatarUrl} size={88} square={!room.isDirect} />
    <h1>{room.name}</h1>
    {#if invite}
      <p>{room.isDirect ? `${inviter} ti chce napsat` : `Zve tě ${inviter}`}</p>
    {/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    <div class="buttons">
      <button onclick={() => act(false)} disabled={busy}>Odmítnout</button>
      <button class="primary" onclick={() => act(true)} disabled={busy}>Přijmout</button>
    </div>
  </div>
</section>

<style>
  .invite {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  header {
    padding: 12px 16px;
  }

  .back {
    display: none;
    place-items: center;
    width: 36px;
    height: 36px;
    border: 0;
    border-radius: 10px;
    background: none;
  }

  .card {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 24px;
    text-align: center;
  }

  h1 {
    margin: 14px 0 0;
    font-family: var(--display);
    font-size: 1.5rem;
  }

  p {
    margin: 0;
    color: var(--ink-muted);
  }

  .error {
    color: #f87171;
  }

  .buttons {
    display: flex;
    gap: 10px;
    margin-top: 20px;
  }

  .buttons button {
    min-width: 130px;
    padding: 11px 18px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: none;
    font-weight: 700;
  }

  .buttons .primary {
    border-color: transparent;
    background: var(--accent);
    color: #fff;
  }

  button:disabled {
    opacity: 0.5;
  }

  @media (max-width: 760px) {
    .back {
      display: grid;
    }
  }
</style>
