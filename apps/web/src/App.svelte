<script lang="ts">
  import { onMount } from "svelte";
  import Logo from "./components/Logo.svelte";
  import { forget, refreshSession, resume } from "./lib/account";
  import type { PockyClient } from "./lib/core";
  import { useClient } from "./lib/media";
  import Chats from "./screens/Chats.svelte";
  import Login from "./screens/Login.svelte";

  let client = $state<PockyClient | null>(null);
  let starting = $state(true);
  let notice = $state(sessionStorage.getItem("pocky-notice") ?? "");
  sessionStorage.removeItem("pocky-notice");

  function watch(value: PockyClient) {
    value
      .watchSession(async (event: string) => {
        if (event === "refreshed") {
          await refreshSession(value);
          return;
        }
        await forget();
        sessionStorage.setItem("pocky-notice", "Přihlášení vypršelo. Přihlas se znovu.");
        location.reload();
      })
      .catch(() => undefined);
  }

  $effect(() => useClient(client));

  onMount(async () => {
    try {
      client = await resume();
      if (client) watch(client);
    } catch {
      await forget();
      notice = "Přihlášení vypršelo. Přihlas se znovu.";
    } finally {
      starting = false;
    }
  });

  function signedOut() {
    client = null;
    location.reload();
  }
</script>

{#if starting}
  <div class="splash"><Logo size={72} /></div>
{:else if client}
  <Chats {client} onSignedOut={signedOut} />
{:else}
  <Login
    {notice}
    onSignedIn={(value) => {
      client = value;
      watch(value);
    }}
  />
{/if}

<style>
  .splash {
    height: 100%;
    display: grid;
    place-items: center;
  }
</style>
