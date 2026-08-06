import { describe, it, expect, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import React from "react";

// vi.hoisted ensures the context is created before vi.mock hoists the factory.
const { mockI18nContext } = vi.hoisted(() => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createContext } = require("react") as typeof import("react");
  return {
    mockI18nContext: createContext<{
      t: (key: string) => string;
      currentLanguage: string;
    } | null>(null),
  };
});

vi.mock("@thunderid/react", () => ({
  I18nContext: mockI18nContext,
}));

import { useAppTranslation } from "../../hooks/useAppTranslation";

describe("useAppTranslation — outside ThunderIDProvider (no context value)", () => {
  it("returns isTranslationAvailable: false", () => {
    const { result } = renderHook(() => useAppTranslation());
    expect(result.current.isTranslationAvailable).toBe(false);
  });

  it("translates known fallback keys", () => {
    const { result } = renderHook(() => useAppTranslation());
    expect(result.current.t("app.back")).toBe("Back");
    expect(result.current.t("app.network_error.title")).toBe(
      "No Internet Connection",
    );
    expect(result.current.t("app.otp.resend_timer")).toBe(
      "You can resend the OTP in",
    );
  });

  it("returns the raw key when no fallback entry exists", () => {
    const { result } = renderHook(() => useAppTranslation());
    expect(result.current.t("some.unknown.key")).toBe("some.unknown.key");
  });

  it("covers every documented fallback key with an actual translation", () => {
    const { result } = renderHook(() => useAppTranslation());
    const knownKeys = [
      "app.footer.powered_by",
      "app.network_error.title",
      "app.network_error.description",
      "app.network_error.try_again",
      "app.otp.resend_timer",
      "app.otp.timed_out",
      "app.otp.submit",
      "app.back",
      "app.esignet_details",
      "errors.heading",
      "errors.something_went_wrong",
      "errors.unexpected_error",
      "errors.page_not_found",
      "errors.network.unavailable",
      "messages.loading.placeholder",
    ];
    for (const key of knownKeys) {
      expect(result.current.t(key)).not.toBe(key);
    }
  });
});

describe("useAppTranslation — inside ThunderIDProvider (context value present)", () => {
  it("returns isTranslationAvailable: true", () => {
    const sdkT = vi.fn((key: string) => `sdk:${key}`);
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(
        mockI18nContext.Provider,
        { value: { t: sdkT, currentLanguage: "en" } },
        children,
      );

    const { result } = renderHook(() => useAppTranslation(), { wrapper });
    expect(result.current.isTranslationAvailable).toBe(true);
  });

  it("delegates to the SDK t() function", () => {
    const sdkT = vi.fn((key: string) => `sdk:${key}`);
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(
        mockI18nContext.Provider,
        { value: { t: sdkT, currentLanguage: "en" } },
        children,
      );

    const { result } = renderHook(() => useAppTranslation(), { wrapper });
    expect(result.current.t("app.back")).toBe("sdk:app.back");
    expect(sdkT).toHaveBeenCalledWith("app.back");
  });
});
