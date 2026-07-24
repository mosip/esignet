import { describe, it, expect, beforeEach } from "vitest";
import {
  parsePositiveInt,
  checkConfigProperty,
  getPollingConfig,
  isNonEmptyString,
} from "../../utils/parsing";

describe("parsing utilities", () => {
  describe("isNonEmptyString", () => {
    it("returns true for a non-empty string", () => {
      expect(isNonEmptyString("hello")).toBe(true);
    });

    it("returns false for an empty or whitespace-only string", () => {
      expect(isNonEmptyString("")).toBe(false);
      expect(isNonEmptyString("   ")).toBe(false);
    });

    it("returns false for non-string values", () => {
      expect(isNonEmptyString(42)).toBe(false);
      expect(isNonEmptyString(null)).toBe(false);
      expect(isNonEmptyString(undefined)).toBe(false);
    });
  });

  describe("parsePositiveInt", () => {
    it("parses a valid positive integer", () => {
      expect(parsePositiveInt(42, 10)).toBe(42);
    });

    it("parses a positive string number", () => {
      expect(parsePositiveInt("100", 10)).toBe(100);
    });

    it("returns default for negative number", () => {
      expect(parsePositiveInt(-5, 10)).toBe(10);
    });

    it("returns default for zero", () => {
      expect(parsePositiveInt(0, 10)).toBe(10);
    });

    it("returns default for NaN", () => {
      expect(parsePositiveInt("abc", 10)).toBe(10);
    });

    it("returns default for undefined", () => {
      expect(parsePositiveInt(undefined, 10)).toBe(10);
    });
  });

  describe("checkConfigProperty", () => {
    it("returns true when property exists", () => {
      expect(checkConfigProperty({ key: "value" }, "key")).toBe(true);
    });

    it("returns false when property does not exist", () => {
      expect(checkConfigProperty({ key: "value" }, "other")).toBe(false);
    });

    it("returns false for null config", () => {
      expect(checkConfigProperty(null, "key")).toBe(false);
    });

    it("returns false for undefined config", () => {
      expect(checkConfigProperty(undefined, "key")).toBe(false);
    });

    it("returns true when property value is falsy", () => {
      expect(checkConfigProperty({ key: false }, "key")).toBe(true);
    });
  });

  describe("getPollingConfig", () => {
    beforeEach(() => {
      window._env_ = {
        DEFAULT_LANG: "en",
        DEFAULT_WELLKNOWN: "",
        DEFAULT_THEME: "",
        DEFAULT_FAVICON: "favicon.ico",
        DEFAULT_TITLE: "eSignet",
        DEFAULT_ID_PROVIDER_NAME: "eSignet",
        DEFAULT_FONT_URL: "",
      };
    });

    it("returns default values when no polling env vars set", () => {
      const config = getPollingConfig();
      expect(config.interval).toBe(10000);
      expect(config.timeout).toBe(5000);
      expect(config.enabled).toBe(true);
      expect(typeof config.url).toBe("string");
    });

    it("uses custom polling interval when set", () => {
      window._env_.POLLING_INTERVAL = "3000";
      const config = getPollingConfig();
      expect(config.interval).toBe(3000);
    });

    it("uses custom polling timeout when set", () => {
      window._env_.POLLING_TIMEOUT = "2000";
      const config = getPollingConfig();
      expect(config.timeout).toBe(2000);
    });

    it("disables polling when POLLING_ENABLED is false", () => {
      window._env_.POLLING_ENABLED = "false";
      const config = getPollingConfig();
      expect(config.enabled).toBe(false);
    });

    it("enables polling when POLLING_ENABLED is true", () => {
      window._env_.POLLING_ENABLED = "true";
      const config = getPollingConfig();
      expect(config.enabled).toBe(true);
    });

    it("uses custom polling URL when set", () => {
      window._env_.POLLING_URL = "https://example.com/health";
      const config = getPollingConfig();
      expect(config.url).toBe("https://example.com/health");
    });
  });
});
