<script lang="ts">
  import Icon from "../components/Icon.svelte";
  import Logo from "../components/Logo.svelte";
  import { signIn, signUp } from "../lib/account";
  import { config } from "../lib/config";
  import { loadCore, type PockyClient } from "../lib/core";
  import { errorText } from "../lib/text";

  let { notice = "", onSignedIn }: { notice?: string; onSignedIn: (client: PockyClient) => void } = $props();

  let username = $state("");
  let password = $state("");
  let inviteCode = $state("");
  let registering = $state(false);
  let showPassword = $state(false);
  let busy = $state(false);
  let error = $state("");
  let server = $state(rememberedServer());
  let editingServer = $state(false);
  let serverInput = $state("");
  let checking = $state(false);
  let serverError = $state("");

  const serverFromId = $derived.by(() => {
    const id = username.trim();
    if (!config.allowOtherServers || !id.startsWith("@")) return null;
    const colon = id.indexOf(":");
    return colon > 1 ? id.slice(colon + 1).toLowerCase() : null;
  });
  const activeServer = $derived(serverFromId ?? server);

  function rememberedServer(): string {
    if (!config.allowOtherServers) return config.defaultServer;
    try {
      return localStorage.getItem("pocky-server") || config.defaultServer;
    } catch {
      return config.defaultServer;
    }
  }

  function startEditing() {
    serverInput = server;
    serverError = "";
    editingServer = true;
  }

  async function useServer() {
    const value = serverInput.trim() || config.defaultServer;
    checking = true;
    serverError = "";
    try {
      const core = await loadCore();
      const info = (await core.checkServer(value)) as { server: string };
      server = info.server;
      editingServer = false;
      try {
        localStorage.setItem("pocky-server", server);
      } catch {}
    } catch (err) {
      serverError = errorText(err);
    } finally {
      checking = false;
    }
  }

  $effect(() => {
    if (notice) error = notice;
  });

  const ready = $derived(
    username.trim().length > 0 &&
      password.length > (registering ? 7 : 0) &&
      (!registering || inviteCode.trim().length > 0) &&
      !busy,
  );

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (!ready) return;

    busy = true;
    error = "";
    try {
      const name = username.trim();
      onSignedIn(
        registering
          ? await signUp(name, password, inviteCode.trim(), activeServer)
          : await signIn(name, password, activeServer),
      );
      password = "";
      inviteCode = "";
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
    }
  }
</script>

<main>
  <form onsubmit={submit}>
    <div class="brand">
      <span class="logo"><Logo size={64} /></span>
      <div class="name">
        <strong>PockyAPP</strong>
        <span class="beta">BETA</span>
      </div>
    </div>

    <h1>{#if registering}Vytvoř si účet{:else}Chat, který <span>žije</span>{/if}.</h1>
    <p class="subtitle">
      {registering ? "Zvol si jméno a heslo a vlož pozvánkový kód." : "Přihlas se svým Matrix účtem."}
    </p>

    <label for="username">Přezdívka nebo uživatelské jméno</label>
    <div class="field">
      <span class="affix">@</span>
      <input
        id="username"
        bind:value={username}
        autocomplete="username"
        autocapitalize="off"
        spellcheck="false"
        placeholder="jmeno"
        disabled={busy}
      />
      {#if !serverFromId}
        <span class="affix">:{activeServer}</span>
      {/if}
    </div>
    <p class="hint">Stačí jen jméno, @ a server doplníme sami.</p>

    <label for="password">Heslo</label>
    <div class="field">
      <input
        id="password"
        type={showPassword ? "text" : "password"}
        bind:value={password}
        autocomplete={registering ? "new-password" : "current-password"}
        disabled={busy}
      />
      <button
        type="button"
        class="eye"
        aria-label={showPassword ? "Skrýt heslo" : "Zobrazit heslo"}
        onclick={() => (showPassword = !showPassword)}
      >
        <Icon name={showPassword ? "eye-off" : "eye"} />
      </button>
    </div>

    {#if registering}
      <label for="invite">Pozvánkový kód</label>
      <div class="field">
        <input
          id="invite"
          bind:value={inviteCode}
          autocomplete="off"
          autocapitalize="off"
          spellcheck="false"
          placeholder="kód"
          disabled={busy}
        />
      </div>
      <p class="hint">Kód ti pošle správce serveru. Na pokecio.com ho dostaneš na reg@pokecio.com. Heslo aspoň 8 znaků.</p>
    {/if}

    {#if error}
      <p class="error" role="alert"><Icon name="alert-circle" size={18} />{error}</p>
    {/if}

    <button class="submit" type="submit" disabled={!ready}>
      {#if registering}{busy ? "Zakládám účet…" : "Vytvořit účet"}{:else}{busy ? "Přihlašuji…" : "Přihlásit se"}{/if}
    </button>

    <p class="register">
      {registering ? "Už máš účet?" : "Nemáš účet?"}
      <button
        type="button"
        class="switch"
        disabled={busy}
        onclick={() => {
          registering = !registering;
          error = "";
        }}
      >{registering ? "Přihlásit se" : "Vytvořit účet"}</button>
    </p>

    {#if editingServer}
      <div class="server-form">
        <label for="server">Matrix server</label>
        <div class="field">
          <input
            id="server"
            bind:value={serverInput}
            autocapitalize="off"
            spellcheck="false"
            placeholder="example.org"
            disabled={checking}
            onkeydown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                useServer();
              }
            }}
          />
        </div>
        <p class="hint">Jméno serveru z adresy účtu, třeba example.org, nebo přímá adresa https://…</p>
        {#if serverError}
          <p class="error" role="alert"><Icon name="alert-circle" size={18} />{serverError}</p>
        {/if}
        <div class="server-actions">
          <button type="button" class="link" onclick={() => (editingServer = false)} disabled={checking}>Zrušit</button>
          <button type="button" class="link strong" onclick={useServer} disabled={checking}>
            {checking ? "Ověřuji…" : "Použít"}
          </button>
        </div>
      </div>
    {:else}
      <div class="server">
        <span>Server</span>
        <strong>{activeServer}</strong>
        {#if config.allowOtherServers}
          <button type="button" class="change" onclick={startEditing}>Změnit</button>
        {/if}
      </div>
    {/if}
  </form>
</main>

<style>
  main {
    min-height: 100%;
    display: grid;
    place-items: center;
    padding: 32px 16px;
  }

  form {
    width: 100%;
    max-width: 400px;
    display: flex;
    flex-direction: column;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 28px;
  }

  .logo {
    display: grid;
    filter: drop-shadow(0 6px 22px color-mix(in srgb, var(--accent) 55%, transparent));
  }

  .name {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .name strong {
    font-family: var(--display);
    font-size: 1.6rem;
    font-weight: 700;
  }

  .beta {
    padding: 2px 9px;
    border-radius: 999px;
    border: 1px solid color-mix(in srgb, var(--gold) 60%, transparent);
    color: var(--gold);
    font-size: 0.7rem;
    font-weight: 800;
    letter-spacing: 0.08em;
  }

  h1 {
    font-family: var(--display);
    font-size: 2.3rem;
    font-weight: 700;
    line-height: 1.05;
    letter-spacing: -0.02em;
    margin: 0 0 8px;
  }

  h1 span {
    color: var(--accent-soft);
  }

  .subtitle {
    color: var(--ink-muted);
    margin: 0 0 28px;
  }

  label {
    font-size: 0.88rem;
    font-weight: 600;
    margin-bottom: 8px;
  }

  .field {
    display: flex;
    align-items: center;
    height: 50px;
    padding: 0 14px;
    border-radius: var(--radius);
    background: var(--surface);
    border: 1px solid var(--line);
    margin-bottom: 18px;
  }

  .field:focus-within {
    border-color: var(--accent);
  }

  .field input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: 0;
    background: none;
    outline: none;
    font-size: 1rem;
  }

  .affix {
    color: var(--ink-muted);
  }

  .affix:first-child {
    margin-right: 2px;
  }

  .hint {
    margin: -12px 0 18px;
    font-size: 0.82rem;
    color: var(--ink-muted);
  }

  .eye {
    display: grid;
    padding: 6px;
    margin-right: -6px;
    border: 0;
    background: none;
    color: var(--ink-muted);
  }

  .error {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: -4px 0 16px;
    color: var(--danger);
    font-size: 0.92rem;
  }

  .submit {
    height: 52px;
    border: 0;
    border-radius: var(--radius);
    background: linear-gradient(135deg, var(--gradient-start), var(--gradient-end));
    color: #fff;
    font-weight: 700;
    font-size: 1rem;
  }

  .submit:disabled {
    opacity: 0.5;
    cursor: default;
  }

  .register {
    text-align: center;
    color: var(--ink-muted);
    margin: 22px 0;
  }

  .switch {
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent-soft);
    font: inherit;
    font-weight: 600;
  }

  .switch:disabled {
    opacity: 0.6;
  }

  .change,
  .link {
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent-soft);
    font: inherit;
    font-weight: 600;
    cursor: pointer;
  }

  .link.strong {
    color: var(--ink);
  }

  .server-form {
    display: flex;
    flex-direction: column;
  }

  .server-actions {
    display: flex;
    justify-content: flex-end;
    gap: 18px;
    margin-top: 6px;
  }

  .server {
    align-self: center;
    display: flex;
    gap: 8px;
    padding: 8px 14px;
    border-radius: 999px;
    border: 1px solid var(--line);
    font-size: 0.85rem;
    color: var(--ink-muted);
  }

  .server strong {
    color: var(--ink);
    font-weight: 600;
  }
</style>
