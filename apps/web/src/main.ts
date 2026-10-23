import "@fontsource-variable/bricolage-grotesque/opsz.css";
import "./app.css";
import { mount } from "svelte";
import tokens from "../../../design/tokens.json";
import App from "./App.svelte";

for (const [name, value] of Object.entries(tokens.color)) {
  document.documentElement.style.setProperty(`--${name}`, value);
}

mount(App, { target: document.getElementById("app")! });

if (import.meta.env.PROD && "serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js").catch(() => undefined);
}
