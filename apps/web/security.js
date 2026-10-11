import { readFileSync } from "node:fs";

const config = JSON.parse(readFileSync(new URL("./pocky.config.json", import.meta.url), "utf8"));
const tiles = config.mapTiles ? [new URL(config.mapTiles).origin] : [];
const servers = config.allowOtherServers ? ["https:"] : config.connect;
const connect = ["'self'", ...new Set([...servers, ...tiles])].join(" ");

export const contentSecurityPolicy = [
  "default-src 'none'",
  "script-src 'self' 'wasm-unsafe-eval'",
  "style-src 'self'",
  "img-src 'self' blob: data:",
  "font-src 'self'",
  `connect-src ${connect}`,
  "worker-src 'self' blob:",
  "media-src 'self' blob:",
  "manifest-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
].join("; ");

export const securityHeaders = {
  "Content-Security-Policy": contentSecurityPolicy,
  "Strict-Transport-Security": "max-age=63072000; includeSubDomains",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Permissions-Policy": "geolocation=(self), camera=(), microphone=(), payment=(), usb=(), interest-cohort=()",
  "Cross-Origin-Opener-Policy": "same-origin",
};
