<script lang="ts">
  const files = import.meta.glob<string>("../../../../design/icons/*.svg", {
    query: "?raw",
    import: "default",
    eager: true,
  });

  let { name, size = 20 }: { name: string; size?: number } = $props();

  const svg = $derived(
    (files[`../../../../design/icons/${name}.svg`] ?? "")
      .replace(/\swidth="24"/, ` width="${size}"`)
      .replace(/\sheight="24"/, ` height="${size}"`)
      .replace("<svg", '<svg aria-hidden="true"'),
  );
</script>

<span class="icon">{@html svg}</span>

<style>
  .icon {
    display: inline-flex;
    flex: none;
  }
</style>
