<script lang="ts">
  import type { PockyClient } from "../lib/core";
  import { shortName } from "../lib/text";
  import type { Person } from "../lib/types";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";

  let {
    client,
    selected = $bindable([]),
    exclude = [],
    autofocus = false,
  }: { client: PockyClient; selected?: Person[]; exclude?: string[]; autofocus?: boolean } = $props();

  let term = $state("");
  let people = $state<Person[]>([]);
  let searching = $state(false);
  let searched = $state(false);
  let timer: ReturnType<typeof setTimeout> | undefined;
  let query = 0;

  const own = $derived(client.userId() ?? "");
  const results = $derived(people.filter((person) => person.userId !== own && !exclude.includes(person.userId)));

  function search() {
    clearTimeout(timer);
    const value = term.trim();
    searched = false;
    if (value.length < 2) {
      people = [];
      return;
    }
    timer = setTimeout(async () => {
      const id = ++query;
      searching = true;
      try {
        const found: Person[] = await client.searchPeople(value);
        if (id === query) {
          people = found;
          searched = true;
        }
      } catch {
        if (id === query) people = [];
      } finally {
        if (id === query) searching = false;
      }
    }, 300);
  }

  function isPicked(person: Person): boolean {
    return selected.some((item) => item.userId === person.userId);
  }

  function toggle(person: Person) {
    selected = isPicked(person) ? selected.filter((item) => item.userId !== person.userId) : [...selected, person];
  }

  function nameOf(person: Person): string {
    return person.displayName ?? shortName(person.userId);
  }
</script>

{#if selected.length > 0}
  <div class="chips">
    {#each selected as person (person.userId)}
      <button class="chip" onclick={() => toggle(person)} title="Odebrat">
        <Avatar id={person.userId} name={nameOf(person)} url={person.avatarUrl} size={22} />
        {nameOf(person)}
        <Icon name="close" size={14} />
      </button>
    {/each}
  </div>
{/if}

<label class="search">
  <Icon name="search" size={18} />
  <!-- svelte-ignore a11y_autofocus -->
  <input bind:value={term} oninput={search} placeholder="Jméno nebo @uživatel:server" autocomplete="off" spellcheck="false" {autofocus} />
</label>

<div class="list">
  {#if term.trim().length < 2}
    <p class="hint">Napiš jméno, přezdívku nebo uživatelské jméno.</p>
  {:else}
    {#each results as person (person.userId)}
      <button class="person" class:on={isPicked(person)} onclick={() => toggle(person)}>
        <Avatar id={person.userId} name={nameOf(person)} url={person.avatarUrl} size={40} />
        <span>
          <strong>{nameOf(person)}</strong>
          <small>{person.userId}</small>
        </span>
        <span class="mark">{#if isPicked(person)}<Icon name="check" size={16} />{/if}</span>
      </button>
    {:else}
      <p class="hint">{searching || !searched ? "Hledám…" : "Nikoho jsme nenašli. Zkus přesné uživatelské jméno."}</p>
    {/each}
  {/if}
</div>

<style>
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 4px 16px 0;
  }

  .chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 8px 3px 3px;
    border: 0;
    border-radius: 999px;
    background: color-mix(in srgb, var(--accent) 22%, transparent);
    font-size: 0.85rem;
  }

  .search {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 8px 16px;
    padding: 0 14px;
    height: 46px;
    border-radius: 12px;
    border: 1px solid var(--line);
    background: var(--bg);
    color: var(--ink-muted);
  }

  .search:focus-within {
    border-color: var(--accent);
  }

  .search input {
    flex: 1;
    height: 100%;
    border: 0;
    background: none;
    outline: none;
    color: var(--ink);
  }

  .list {
    flex: 1;
    min-height: 120px;
    overflow-y: auto;
    padding: 0 8px 8px;
  }

  .hint {
    margin: 10px 12px;
    font-size: 0.88rem;
    color: var(--ink-muted);
  }

  .person {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border: 0;
    border-radius: 12px;
    background: none;
    text-align: left;
  }

  .person:hover {
    background: color-mix(in srgb, var(--ink) 6%, transparent);
  }

  .person span:not(.mark) {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .person small {
    color: var(--ink-muted);
    overflow-wrap: anywhere;
  }

  .mark {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    border: 1.5px solid var(--line);
    color: #fff;
  }

  .on .mark {
    border-color: transparent;
    background: var(--accent);
  }
</style>
