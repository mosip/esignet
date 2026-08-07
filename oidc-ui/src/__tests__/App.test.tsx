import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import React from "react";

const mockInitializeCSSVariables = vi.fn();

vi.mock("../services/css-variable.service", () => ({
  initializeCSSVariables: () => mockInitializeCSSVariables(),
}));

// Mock AppLayout's children so they render quickly without side-effects.
// We deliberately do NOT mock RouterProvider or createBrowserRouter so that
// AppLayout (and the private LanguageDirectionSync inside it) actually renders,
// giving us coverage over those lines.
vi.mock("../components/NavHeader", () => ({
  default: () => React.createElement("div", { "data-testid": "nav-header" }),
}));
vi.mock("../routes/AppRouter", () => ({
  default: () => React.createElement("div", { "data-testid": "app-router" }),
}));
vi.mock("../components/Footer", () => ({
  default: () => React.createElement("div", { "data-testid": "footer" }),
}));

// Capture the module-level queryClient from App.tsx by intercepting the
// QueryClientProvider render.  We pass the client straight through to the real
// provider so nothing else breaks; we just store a reference for the retry tests.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const capturedQC = vi.hoisted(() => ({ instance: null as any }));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    QueryClientProvider: ({
      client,
      children,
    }: Parameters<typeof actual.QueryClientProvider>[0]) => {
      capturedQC.instance = client;
      return React.createElement(
        actual.QueryClientProvider,
        { client },
        children,
      );
    },
  };
});

// Provide a controllable I18nContext so LanguageDirectionSync can be driven
// with specific language values in tests.
const mockI18nContext = vi.hoisted(() => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createContext } = require("react") as typeof import("react");
  return createContext<{
    t: (k: string) => string;
    currentLanguage: string;
  } | null>(null);
});

vi.mock("@thunderid/react", () => ({
  I18nContext: mockI18nContext,
}));

import App from "../App";

// ── Core App rendering ─────────────────────────────────────────────────────────

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockInitializeCSSVariables.mockReturnValue(undefined);
    document.documentElement.dir = "ltr";
    document.documentElement.lang = "";
  });

  it("renders the nav header after CSS variables are initialised", async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getByTestId("nav-header")).toBeDefined();
    });
  });

  it("renders the app router after loading", async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getByTestId("app-router")).toBeDefined();
    });
  });

  it("calls initializeCSSVariables on mount", async () => {
    render(<App />);
    await waitFor(() => {
      expect(mockInitializeCSSVariables).toHaveBeenCalledTimes(1);
    });
  });
});

// ── LanguageDirectionSync (rendered via the real AppLayout) ────────────────────
//
// By wrapping <App /> in a mockI18nContext.Provider, the context value flows
// down through RouterProvider → AppLayout → LanguageDirectionSync.  The real
// source lines 20-31 in App.tsx now execute, giving us branch coverage.

describe("LanguageDirectionSync — via real AppLayout render", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockInitializeCSSVariables.mockReturnValue(undefined);
    document.documentElement.dir = "ltr";
    document.documentElement.lang = "";
  });

  it("sets document.dir to rtl when currentLanguage is Arabic", async () => {
    render(
      <mockI18nContext.Provider value={{ t: (k) => k, currentLanguage: "ar" }}>
        <App />
      </mockI18nContext.Provider>,
    );
    await waitFor(() => {
      expect(document.documentElement.dir).toBe("rtl");
    });
  });

  it("sets document.dir to ltr when currentLanguage is English", async () => {
    render(
      <mockI18nContext.Provider value={{ t: (k) => k, currentLanguage: "en" }}>
        <App />
      </mockI18nContext.Provider>,
    );
    await waitFor(() => {
      expect(document.documentElement.dir).toBe("ltr");
    });
  });

  it("sets document.lang to the current language code", async () => {
    render(
      <mockI18nContext.Provider
        value={{ t: (k) => k, currentLanguage: "fr-CA" }}
      >
        <App />
      </mockI18nContext.Provider>,
    );
    await waitFor(() => {
      expect(document.documentElement.lang).toBe("fr-CA");
    });
  });

  it("sets ltr for a region-tagged LTR language (en-US)", async () => {
    render(
      <mockI18nContext.Provider
        value={{ t: (k) => k, currentLanguage: "en-US" }}
      >
        <App />
      </mockI18nContext.Provider>,
    );
    await waitFor(() => {
      expect(document.documentElement.dir).toBe("ltr");
    });
  });

  it("does not change document.dir when I18nContext has no language (null context)", async () => {
    // LanguageDirectionSync early-returns when i18n is null.
    render(<App />);
    await waitFor(() => {
      expect(screen.getByTestId("nav-header")).toBeDefined();
    });
    expect(document.documentElement.dir).toBe("ltr");
  });
});

// ── queryClient retry policy (lines 58-61 in App.tsx) ─────────────────────────
//
// We access the module-level queryClient via the capturedQC interceptor above.
// Calling retryFn() directly instruments the actual source lines.

describe("queryClient retry policy", () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockInitializeCSSVariables.mockReturnValue(undefined);
    capturedQC.instance = null;
    // Render App so QueryClientProvider fires and capturedQC.instance is set.
    render(<App />);
    await waitFor(() => expect(screen.getByTestId("nav-header")).toBeDefined());
  });

  function retryFn(): (failureCount: number, error: unknown) => boolean {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const fn = capturedQC.instance?.getDefaultOptions()?.queries?.retry as any;
    if (typeof fn !== "function")
      throw new Error("retry option is not a function");
    return fn;
  }

  it("returns false for any 4xx client error (400-499)", () => {
    const retry = retryFn();
    expect(retry(0, { response: { status: 400 } })).toBe(false);
    expect(retry(0, { response: { status: 404 } })).toBe(false);
    expect(retry(0, { response: { status: 499 } })).toBe(false);
  });

  it("returns true for the first 3 failures on 5xx server errors", () => {
    const retry = retryFn();
    expect(retry(0, { response: { status: 500 } })).toBe(true);
    expect(retry(2, { response: { status: 503 } })).toBe(true);
  });

  it("returns false when failureCount reaches 3 (non-4xx error)", () => {
    const retry = retryFn();
    expect(retry(3, { response: { status: 500 } })).toBe(false);
    expect(retry(10, new Error("network"))).toBe(false);
  });

  it("returns true for network errors with no response (no status)", () => {
    const retry = retryFn();
    expect(retry(0, new Error("network failure"))).toBe(true);
    expect(retry(2, "plain string error")).toBe(true);
  });
});
