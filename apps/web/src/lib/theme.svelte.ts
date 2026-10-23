import tokens from "../../../../design/tokens.json";

export type ThemeMode = "system" | "light" | "dark";

const key = "pocky-theme";
const query = window.matchMedia("(prefers-color-scheme: dark)");

function stored(): ThemeMode {
  try {
    const value = localStorage.getItem(key);
    return value === "light" || value === "dark" ? value : "system";
  } catch {
    return "system";
  }
}

let mode = $state<ThemeMode>(stored());

function dark(): boolean {
  return mode === "dark" || (mode === "system" && query.matches);
}

function apply() {
  const isDark = dark();
  const palette: Record<string, string> = {
    ...tokens.color,
    ...(isDark ? { danger: "var(--danger)" } : { ...tokens.colorLight, danger: "#dc2626" }),
  };
  const root = document.documentElement;
  for (const [name, value] of Object.entries(palette)) {
    root.style.setProperty(`--${name}`, value);
  }
  root.dataset.theme = isDark ? "dark" : "light";
  root.style.colorScheme = isDark ? "dark" : "light";
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", palette.bg);
}

export const theme = {
  get mode() {
    return mode;
  },
  select(next: ThemeMode) {
    mode = next;
    try {
      if (next === "system") localStorage.removeItem(key);
      else localStorage.setItem(key, next);
    } catch {
    }
    apply();
  },
};

export function startTheme() {
  apply();
  query.addEventListener("change", () => {
    if (mode === "system") apply();
  });
}
