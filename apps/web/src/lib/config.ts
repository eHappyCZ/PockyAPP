import settings from "../../pocky.config.json";

export interface PockyConfig {
  defaultServer: string;
  allowOtherServers: boolean;
  connect: string[];
  mapTiles: string;
}

export const config: PockyConfig = settings;

export const mapsEnabled = config.mapTiles.length > 0;
