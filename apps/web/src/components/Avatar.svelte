<script lang="ts">
  import { mediaUrl } from "../lib/media";
  import type { Aura } from "../lib/types";

  const colors = ["#7C3AED", "#8B5CF6", "#6366F1", "#DB2777", "#0EA5E9", "#059669", "#D97706"];

  let {
    id,
    name,
    url,
    size = 48,
    square = false,
    aura,
  }: { id: string; name: string; url?: string; size?: number; square?: boolean; aura?: Aura } = $props();

  let image = $state<string | null>(null);

  $effect(() => {
    image = null;
    if (!url) return;
    let alive = true;
    mediaUrl(url, size * 2, size * 2).then(
      (value) => alive && (image = value),
      () => undefined,
    );
    return () => (alive = false);
  });

  function hash(value: string): number {
    let h = 0;
    for (let i = 0; i < value.length; i++) h = (Math.imul(31, h) + value.charCodeAt(i)) | 0;
    return h;
  }

  function initials(value: string): string {
    const words = value
      .replace(/^[#@! ]+/, "")
      .split(/[ ._\-·]/)
      .filter((word) => word.trim());
    if (words.length === 0) return "?";
    const letters = words.length === 1 ? [...words[0]].slice(0, 2).join("") : [...words[0]][0] + [...words[1]][0];
    return letters.toUpperCase();
  }

  const color = $derived(colors[((hash(id) % colors.length) + colors.length) % colors.length]);
</script>

<span class="holder" class:square style:width="{size}px" style:height="{size}px">
  <span
    class="avatar"
    style:background={color}
    style:font-size="{size * 0.36}px"
  >
    {#if image}
      <img src={image} alt="" />
    {:else}
      {initials(name)}
    {/if}
  </span>
  {#if aura}<span class="ring {aura}"></span>{/if}
</span>

<style>
  .holder {
    position: relative;
    flex: none;
    display: inline-block;
    border-radius: 50%;
  }

  .holder.square {
    border-radius: 30%;
  }

  .avatar {
    width: 100%;
    height: 100%;
    display: grid;
    place-items: center;
    overflow: hidden;
    border-radius: inherit;
    color: #fff;
    font-weight: 800;
    letter-spacing: 0.02em;
  }

  img {
    width: 100%;
    height: 100%;
    border-radius: inherit;
    object-fit: cover;
  }

  .ring {
    --w: 2.5px;
    position: absolute;
    inset: -4.5px;
    padding: var(--w);
    border-radius: 50%;
    pointer-events: none;
    -webkit-mask:
      linear-gradient(#000 0 0) content-box,
      linear-gradient(#000 0 0);
    -webkit-mask-composite: xor;
    mask:
      linear-gradient(#000 0 0) content-box,
      linear-gradient(#000 0 0);
    mask-composite: exclude;
  }

  .square .ring {
    border-radius: calc(30% + 4px);
  }

  .online {
    background: var(--success);
    animation: breath 1.4s ease-in-out infinite alternate;
    filter: drop-shadow(0 0 4px color-mix(in srgb, var(--success) 60%, transparent));
  }

  .away {
    background: var(--gold);
    opacity: 0.55;
  }

  .typing {
    background: conic-gradient(from var(--turn, 0deg), transparent, var(--accent), var(--accent-soft), transparent 75%);
    animation: spin 1.1s linear infinite;
  }

  .own {
    background: conic-gradient(from var(--turn, 0deg), var(--gradient-start), var(--accent), var(--gradient-end), var(--success), var(--gradient-start));
    animation: spin 6s linear infinite;
  }

  @property --turn {
    syntax: "<angle>";
    initial-value: 0deg;
    inherits: false;
  }

  @keyframes spin {
    to {
      --turn: 360deg;
    }
  }

  @keyframes breath {
    from {
      opacity: 0.45;
    }
    to {
      opacity: 1;
    }
  }
</style>
