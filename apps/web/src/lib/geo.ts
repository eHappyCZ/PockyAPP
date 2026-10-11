export const exactLimitM = 150;
const exactZoom = 15;
const earthMeters = 40_075_016.686;

export function metersPerPixel(latitude: number, zoom: number): number {
  return (earthMeters * Math.cos((latitude * Math.PI) / 180)) / (512 * 2 ** zoom);
}

export function isArea(accuracyM?: number): boolean {
  return (accuracyM ?? 0) > exactLimitM;
}

export function previewZoom(latitude: number, accuracyM: number | undefined, widthPx: number): number {
  if (!isArea(accuracyM)) return exactZoom;
  const wanted = (accuracyM! * 2.6) / widthPx;
  const zoom = exactZoom - Math.log2(wanted / metersPerPixel(latitude, exactZoom));
  return Math.min(exactZoom, Math.max(6, zoom));
}

export function formatRadius(meters: number): string {
  return meters >= 1000 ? `~${Math.round(meters / 1000)} km` : `~${Math.round(meters / 50) * 50} m`;
}
