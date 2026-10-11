import { writeFileSync } from "node:fs";
import { securityHeaders } from "../security.js";

const lines = Object.entries(securityHeaders).map(([name, value]) => `add_header ${name} "${value}" always;`);
writeFileSync(new URL("./headers.conf", import.meta.url), `${lines.join("\n")}\n`);
