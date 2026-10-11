import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { svelte } from "@sveltejs/vite-plugin-svelte";
import { defineConfig, type Plugin } from "vite";
import { securityHeaders } from "./security.js";

function integrity(): Plugin {
  let outDir = "dist";
  return {
    name: "pocky-integrity",
    apply: "build",
    configResolved(config) {
      outDir = config.build.outDir;
    },
    closeBundle() {
      const file = join(outDir, "index.html");
      const html = readFileSync(file, "utf8").replace(
        /<(script|link)([^>]*?)\s(src|href)="(\/assets\/[^"]+)"([^>]*)>/g,
        (tag, name: string, before: string, attr: string, path: string, after: string) => {
          const hash = createHash("sha384").update(readFileSync(join(outDir, path))).digest("base64");
          return `<${name}${before} ${attr}="${path}" integrity="sha384-${hash}"${after}>`;
        },
      );
      writeFileSync(file, html);
    },
  };
}

export default defineConfig({
  plugins: [svelte(), integrity()],
  server: { port: 5180, fs: { allow: ["../.."] } },
  preview: { port: 5180, headers: securityHeaders },
  build: { target: "es2022", assetsInlineLimit: 0, chunkSizeWarningLimit: 1100 },
});
