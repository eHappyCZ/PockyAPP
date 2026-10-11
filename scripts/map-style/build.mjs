import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { layers, namedFlavor } from "@protomaps/basemaps";
import { difference } from "@turf/difference";
import { featureCollection, feature, polygon } from "@turf/helpers";
import { simplify } from "@turf/simplify";
import { union } from "@turf/union";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const out = join(root, "apps/android/app/src/main/assets/map");
const tokens = JSON.parse(await readFile(join(root, "design/tokens.json"), "utf8")).color;

const TILES = "https://maps.pokecio.com/tiles/cz-sk.json";
const ASSETS = "https://protomaps.github.io/basemaps-assets";
const FONTS = {
  "Noto Sans Regular": "NotoSans-Regular",
  "Noto Sans Medium": "NotoSans-Medium",
  "Noto Sans Italic": "NotoSans-Italic",
};
const LATIN = ["0-255", "256-511", "8192-8447"];
const BOUNDARIES = "https://polygons.openstreetmap.fr/get_geojson.py";
const COUNTRIES = [51684, 14296];
const UPCOMING = [
  { id: 51477, label: [10.4, 51.1] },
  { id: 16239, label: [14.2, 47.6] },
  { id: 49715, label: [19.3, 52.1] },
];
const RANGES = {
  "Noto Sans Regular": [...LATIN, "512-767", "768-1023", "1024-1279", "7680-7935"],
  "Noto Sans Medium": LATIN,
  "Noto Sans Italic": LATIN,
};

const earth = tokens.surface;
const halo = tokens.bg;
const flavor = {
  ...namedFlavor("dark"),
  background: tokens.bg,
  earth,
  water: "#1b1733",
  ocean_label: tokens["ink-muted"],
  park_a: "#141d1b",
  park_b: "#15221e",
  wood_a: "#141a19",
  wood_b: "#141a19",
  scrub_a: "#16181a",
  scrub_b: "#16181a",
  glacier: "#1a1824",
  sand: "#1a1820",
  beach: "#1f1c27",
  hospital: "#1b1520",
  school: "#18151f",
  industrial: "#16141d",
  military: "#1a1620",
  zoo: "#16181a",
  aerodrome: "#18161f",
  runway: "#2a2536",
  pedestrian: "#1a1722",
  pier: "#2a2536",
  buildings: "#0d0b13",
  other: "#29243a",
  minor_service: "#262135",
  minor_a: "#2f2943",
  minor_b: "#29243a",
  link: "#3a3254",
  major: "#3a3254",
  highway: "#5b47a0",
  bridges_other: "#29243a",
  bridges_minor: "#2f2943",
  bridges_link: "#3a3254",
  bridges_major: "#3a3254",
  bridges_highway: "#5b47a0",
  tunnel_other: "#1f1b2c",
  tunnel_minor: "#1f1b2c",
  tunnel_link: "#262135",
  tunnel_major: "#262135",
  tunnel_highway: "#352a5e",
  railway: "#3d3650",
  boundaries: "#6b5a9e",
  roads_label_minor: "#7d7890",
  roads_label_minor_halo: halo,
  roads_label_major: tokens["ink-muted"],
  roads_label_major_halo: halo,
  subplace_label: "#8a85a0",
  subplace_label_halo: halo,
  city_label: tokens.ink,
  city_label_halo: halo,
  state_label: "#5f5878",
  state_label_halo: halo,
  country_label: tokens["accent-soft"],
  address_label: "#6d6882",
  address_label_halo: halo,
  landcover: {
    grassland: "#151b19",
    barren: "#1a1820",
    urban_area: "#16141e",
    farmland: "#16191a",
    glacier: "#1e1c28",
    scrub: "#171a19",
    forest: "#131c19",
  },
};
for (const key of Object.keys(flavor)) {
  if (key.endsWith("_casing") || key.endsWith("_casing_early") || key.endsWith("_casing_late")) {
    flavor[key] = halo;
  }
}

function localFont(name) {
  if (FONTS[name]) return FONTS[name];
  if (name.includes("Italic")) return FONTS["Noto Sans Italic"];
  if (name.includes("Medium") || name.includes("Bold")) return FONTS["Noto Sans Medium"];
  return FONTS["Noto Sans Regular"];
}

function renameFonts(value) {
  if (typeof value === "string") return value.startsWith("Noto Sans") ? localFont(value) : value;
  if (Array.isArray(value)) return value.map(renameFonts);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, renameFonts(v)]));
  }
  return value;
}

const styleLayers = layers("protomaps", flavor, { lang: "cs" }).map((layer) =>
  layer.layout ? { ...layer, layout: renameFonts(layer.layout) } : layer,
);

async function boundary(id, params) {
  const response = await fetch(`${BOUNDARIES}?id=${id}&params=${params}`);
  if (!response.ok) throw new Error(`${response.status} boundary ${id}`);
  return feature(await response.json());
}

async function area(ids, params, tolerance) {
  const shapes = [];
  for (const id of ids) shapes.push(await boundary(id, params));
  const merged = shapes.length > 1 ? union(featureCollection(shapes)) : shapes[0];
  return simplify(merged, { tolerance, highQuality: true });
}

const covered = await area(COUNTRIES, "0.000100-0.000020-0.000020", 0.0003);
const upcoming = [];
for (const country of UPCOMING) {
  const shape = await area([country.id], "0.004000-0.001000-0.001000", 0.002);
  upcoming.push(difference(featureCollection([shape, covered])));
}
const world = polygon([[[-180, -85], [180, -85], [180, 85], [-180, 85], [-180, -85]]]);
const outside = difference(featureCollection([world, covered]));

const coverageLayers = [
  {
    id: "outside_mask",
    type: "fill",
    source: "outside",
    paint: { "fill-color": tokens.bg, "fill-opacity": 1 },
  },
  {
    id: "upcoming_fill",
    type: "fill",
    source: "upcoming",
    paint: { "fill-color": "#0f0d16" },
  },
  {
    id: "upcoming_edge",
    type: "line",
    source: "upcoming",
    paint: {
      "line-color": "#2c2640",
      "line-width": ["interpolate", ["linear"], ["zoom"], 5, 1.2, 10, 2],
    },
  },
  {
    id: "upcoming_label",
    type: "symbol",
    source: "upcoming_labels",
    layout: {
      "text-field": "Připravujeme",
      "text-font": [FONTS["Noto Sans Medium"]],
      "text-size": ["interpolate", ["linear"], ["zoom"], 5, 12, 10, 18],
      "text-letter-spacing": 0.12,
      "text-transform": "uppercase",
      "text-allow-overlap": true,
    },
    paint: {
      "text-color": "#6f6a85",
    },
  },
  {
    id: "covered_glow",
    type: "line",
    source: "covered",
    paint: {
      "line-color": tokens.accent,
      "line-opacity": 0.25,
      "line-width": ["interpolate", ["linear"], ["zoom"], 5, 4, 12, 10],
      "line-blur": ["interpolate", ["linear"], ["zoom"], 5, 3, 12, 8],
    },
  },
  {
    id: "covered_edge",
    type: "line",
    source: "covered",
    paint: {
      "line-color": tokens["accent-soft"],
      "line-opacity": 0.7,
      "line-width": ["interpolate", ["linear"], ["zoom"], 5, 1, 12, 1.8],
    },
  },
];

const style = {
  version: 8,
  name: "Pokecio",
  glyphs: "asset://map/fonts/{fontstack}/{range}.pbf",
  sprite: "asset://map/sprites/dark",
  sources: {
    protomaps: {
      type: "vector",
      url: TILES,
      attribution: "© OpenStreetMap",
    },
    outside: { type: "geojson", data: outside },
    covered: { type: "geojson", data: covered },
    upcoming: { type: "geojson", data: featureCollection(upcoming) },
    upcoming_labels: {
      type: "geojson",
      data: featureCollection(
        UPCOMING.map((country) => feature({ type: "Point", coordinates: country.label })),
      ),
    },
  },
  layers: [...styleLayers, ...coverageLayers],
};

await mkdir(out, { recursive: true });
await writeFile(join(out, "style.json"), JSON.stringify(style));

async function download(url, file) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${url}`);
  await mkdir(dirname(file), { recursive: true });
  await writeFile(file, Buffer.from(await response.arrayBuffer()));
}

function field(number, bytes) {
  return Buffer.concat([Buffer.from([(number << 3) | 2, bytes.length]), bytes]);
}

function emptyRange(name, range) {
  const stack = Buffer.concat([field(1, Buffer.from(name)), field(2, Buffer.from(range))]);
  return field(1, stack);
}

for (const [stack, local] of Object.entries(FONTS)) {
  const dir = join(out, "fonts", local);
  await rm(dir, { recursive: true, force: true });
  await mkdir(dir, { recursive: true });
  for (let start = 0; start < 65536; start += 256) {
    const range = `${start}-${start + 255}`;
    const file = join(dir, `${range}.pbf`);
    if (RANGES[stack].includes(range)) {
      await download(`${ASSETS}/fonts/${encodeURIComponent(stack)}/${range}.pbf`, file);
    } else {
      await writeFile(file, emptyRange(local, range));
    }
  }
}
for (const name of ["dark.json", "dark.png", "dark@2x.json", "dark@2x.png"]) {
  await download(`${ASSETS}/sprites/v4/${name}`, join(out, "sprites", name));
}

console.log(`${styleLayers.length} layers -> ${out}`);
