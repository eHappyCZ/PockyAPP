<script lang="ts">
  let { size = 48 }: { size?: number } = $props();
</script>

<svg width={size * 0.8} height={size} viewBox="0 0 255 320" aria-hidden="true">
  <defs>
    <linearGradient id="logo-fill" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="var(--logo-light)" />
      <stop offset="1" stop-color="var(--logo-deep)" />
    </linearGradient>
  </defs>
  <path
    d="M0 55A55 55 0 0 1 55 0H147.5A107.5 107.5 0 0 1 147.5 215H107Q85 215 85 237V277.5A42.5 42.5 0 0 1 0 277.5Z"
    fill="url(#logo-fill)"
  />
  <circle cx="74" cy="107.5" r="20" fill="var(--ink)" />
  <circle cx="130" cy="107.5" r="20" fill="var(--ink)" />
  <circle cx="186" cy="107.5" r="20" fill="var(--ink)" />
</svg>
