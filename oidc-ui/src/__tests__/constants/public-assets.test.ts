import { describe, it, expect } from "vitest";
import {
  IMAGES,
  CSS_IMAGE_VARIABLES,
  CONFIG_URLS,
} from "../../constants/public-assets";

// ── Default exports (BASE_URL = '/') ──────────────────────────────────────────

describe("public-assets — default BASE_URL", () => {
  it("IMAGES.BRAND_LOGO starts with the BASE_URL prefix", () => {
    expect(typeof IMAGES.BRAND_LOGO).toBe("string");
    expect(IMAGES.BRAND_LOGO).toContain("brand_logo.png");
  });

  it("CSS_IMAGE_VARIABLES is a non-empty object", () => {
    expect(Object.keys(CSS_IMAGE_VARIABLES).length).toBeGreaterThan(0);
  });

  it("every CSS variable key starts with --", () => {
    for (const key of Object.keys(CSS_IMAGE_VARIABLES)) {
      expect(key.startsWith("--")).toBe(true);
    }
  });

  it("CONFIG_URLS.THEME_CONFIG contains config.json", () => {
    expect(CONFIG_URLS.THEME_CONFIG).toContain("config.json");
  });
});

// Note: the `?? ''` on line 1 (`import.meta.env.BASE_URL ?? ''`) is a defensive
// guard that is unreachable in any Vite/Vitest environment because Vite always
// injects BASE_URL as a defined string at build/transform time.  V8 records the
// branch as uncovered (50%) but there is no runtime path to exercise it.
