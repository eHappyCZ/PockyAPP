import "@fontsource-variable/bricolage-grotesque/opsz.css";
import "./app.css";
import { mount } from "svelte";
import App from "./App.svelte";
import { startTheme } from "./lib/theme.svelte";

startTheme();

mount(App, { target: document.getElementById("app")! });

if (import.meta.env.PROD && "serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js").catch(() => undefined);
}
