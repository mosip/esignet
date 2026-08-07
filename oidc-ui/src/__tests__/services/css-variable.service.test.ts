import { describe, it, expect, beforeEach } from "vitest";
import { initializeCSSVariables } from "../../services/css-variable.service";
import { CSS_IMAGE_VARIABLES } from "../../constants/public-assets";

describe("css-variable.service", () => {
  beforeEach(() => {
    // Reset inline styles
    document.documentElement.removeAttribute("style");
    document.body.removeAttribute("style");
  });

  describe("initializeCSSVariables", () => {
    it("sets CSS variables on document root", () => {
      initializeCSSVariables();

      for (const [variable, url] of Object.entries(CSS_IMAGE_VARIABLES)) {
        const value = document.documentElement.style.getPropertyValue(variable);
        expect(value).toBe(`url("${url}")`);
      }
    });

    it("sets CSS variables on document body", () => {
      initializeCSSVariables();

      for (const [variable, url] of Object.entries(CSS_IMAGE_VARIABLES)) {
        const value = document.body.style.getPropertyValue(variable);
        expect(value).toBe(`url("${url}")`);
      }
    });

    it("can be called multiple times without error", () => {
      expect(() => {
        initializeCSSVariables();
        initializeCSSVariables();
      }).not.toThrow();
    });

    it("skips body when document.body is null — covers if-false branch (line 18)", () => {
      // jsdom always has a body; temporarily remove it to hit the false branch.
      const originalBody = document.body;
      originalBody.remove();
      expect(document.body).toBeNull();

      // Should not throw — the if-guard prevents the null-body access.
      expect(() => initializeCSSVariables()).not.toThrow();

      // Verify root variables were still set even though body was skipped.
      const firstVar = Object.keys(CSS_IMAGE_VARIABLES)[0];
      expect(
        document.documentElement.style.getPropertyValue(firstVar),
      ).not.toBe("");

      // Restore body for subsequent tests.
      document.documentElement.appendChild(originalBody);
    });
  });
});
