import type { Feature, Polygon } from "geojson";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import { type GeoJSONSource, Map as MapLibre, setWorkerUrl, type StyleSpecification } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { config } from "./config";

export { Marker } from "maplibre-gl";

setWorkerUrl(workerUrl);

export const homeCenter: [number, number] = [17.3, 49.4];
export const homeZoom = 6;
const coveredArea: [[number, number], [number, number]] = [
  [10.4, 46.9],
  [24.2, 51.9],
];

let style: Promise<StyleSpecification> | null = null;

export function mapStyle(): Promise<StyleSpecification> {
  style ??= fetch("/map/style.json")
    .then((response) => response.json())
    .then((value: StyleSpecification) => {
      const base = `${location.origin}/map/`;
      const sources = Object.fromEntries(
        Object.entries(value.sources).map(([name, source]) =>
          "url" in source && source.url ? [name, { ...source, url: config.mapTiles }] : [name, source],
        ),
      );
      return {
        ...value,
        sources,
        glyphs: value.glyphs?.replace("asset://map/", base),
        sprite: typeof value.sprite === "string" ? value.sprite.replace("asset://map/", base) : value.sprite,
      };
    });
  return style;
}

export async function createMap(
  container: HTMLElement,
  options: { center?: [number, number]; zoom?: number; interactive?: boolean; preserve?: boolean } = {},
): Promise<MapLibre> {
  return new MapLibre({
    container,
    style: await mapStyle(),
    center: options.center ?? homeCenter,
    zoom: options.zoom ?? homeZoom,
    minZoom: 5.5,
    maxZoom: 18,
    maxBounds: coveredArea,
    interactive: options.interactive ?? true,
    attributionControl: false,
    canvasContextAttributes: { preserveDrawingBuffer: options.preserve ?? false },
  });
}

interface SnapshotJob {
  center: [number, number];
  zoom: number;
  resolve: (url: string) => void;
  reject: (err: unknown) => void;
}

const snapshotWidth = 480;
const snapshotHeight = 300;
const snapshots = new Map<string, Promise<string>>();
const queue: SnapshotJob[] = [];
let painter: Promise<MapLibre> | null = null;
let busy = false;

function snapshotMap(): Promise<MapLibre> {
  painter ??= (async () => {
    const host = document.createElement("div");
    host.style.cssText = `position:fixed;left:-10000px;top:0;width:${snapshotWidth / 2}px;height:${snapshotHeight / 2}px;`;
    document.body.append(host);
    const map = await createMap(host, { interactive: false, preserve: true });
    await map.once("load");
    return map;
  })();
  return painter;
}

async function drain() {
  if (busy) return;
  busy = true;
  try {
    while (queue.length > 0) {
      const job = queue.shift()!;
      try {
        const map = await snapshotMap();
        map.jumpTo({ center: job.center, zoom: job.zoom });
        await map.once("idle");
        const blob = await new Promise<Blob | null>((resolve) => map.getCanvas().toBlob(resolve, "image/png"));
        if (!blob) throw new Error("snapshot failed");
        job.resolve(URL.createObjectURL(blob));
      } catch (err) {
        job.reject(err);
      }
    }
  } finally {
    busy = false;
  }
}

export function mapSnapshot(longitude: number, latitude: number, zoom: number): Promise<string> {
  const key = `${longitude.toFixed(5)},${latitude.toFixed(5)},${zoom.toFixed(2)}`;
  let url = snapshots.get(key);
  if (!url) {
    url = new Promise<string>((resolve, reject) => {
      queue.push({ center: [longitude, latitude], zoom, resolve, reject });
      drain();
    });
    url.catch(() => snapshots.delete(key));
    snapshots.set(key, url);
  }
  return url;
}

export function circlePolygon(longitude: number, latitude: number, radiusM: number): Feature<Polygon> {
  const points: [number, number][] = [];
  const dLat = radiusM / 111_320;
  const dLon = radiusM / (111_320 * Math.cos((latitude * Math.PI) / 180));
  for (let i = 0; i <= 64; i++) {
    const angle = (i / 64) * 2 * Math.PI;
    points.push([longitude + dLon * Math.cos(angle), latitude + dLat * Math.sin(angle)]);
  }
  return { type: "Feature", properties: {}, geometry: { type: "Polygon", coordinates: [points] } };
}

export function showArea(map: MapLibre, id: string, point: { longitude: number; latitude: number; accuracyM?: number }) {
  const data = circlePolygon(point.longitude, point.latitude, point.accuracyM ?? 0);
  const source = map.getSource(id) as GeoJSONSource | undefined;
  if (source) {
    source.setData(data);
    return;
  }
  map.addSource(id, { type: "geojson", data });
  map.addLayer({ id: `${id}-fill`, type: "fill", source: id, paint: { "fill-color": "#8b5cf6", "fill-opacity": 0.18 } });
  map.addLayer({ id: `${id}-line`, type: "line", source: id, paint: { "line-color": "#8b5cf6", "line-width": 2 } });
}

export function pinElement(): HTMLElement {
  const element = document.createElement("div");
  element.className = "pocky-pin";
  return element;
}
