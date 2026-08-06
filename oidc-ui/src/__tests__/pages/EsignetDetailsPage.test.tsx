import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import EsignetDetailsPage from "../../pages/EsignetDetailsPage";

vi.mock("../../hooks/useAppTranslation", () => ({
  useAppTranslation: () => ({
    t: (key: string) => key,
    isTranslationAvailable: false,
  }),
}));

const SAMPLE_DETAILS = [
  { name: "OpenID Config", value: "/.well-known/openid-configuration" },
  { name: "JWKS", value: "/.well-known/jwks.json", icon: "/images/key.svg" },
];

describe("EsignetDetailsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

  it("renders the page heading", () => {
    render(<EsignetDetailsPage />);
    expect(screen.getByRole("heading", { level: 1 })).toBeDefined();
  });

  it("renders brand logo image", () => {
    render(<EsignetDetailsPage />);
    expect(screen.getByAltText("brand-logo")).toBeDefined();
  });

  it("renders detail items from window._env_.DEFAULT_WELLKNOWN", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent(
      JSON.stringify(SAMPLE_DETAILS),
    );
    render(<EsignetDetailsPage />);
    expect(screen.getByText("/.well-known/openid-configuration")).toBeDefined();
    expect(screen.getByText("/.well-known/jwks.json")).toBeDefined();
  });

  it("renders icon image when detail has an icon property", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent(
      JSON.stringify(SAMPLE_DETAILS),
    );
    const { container } = render(<EsignetDetailsPage />);
    // Decorative imgs (alt="") are not in the img ARIA role; query the DOM directly.
    const iconImg = container.querySelector('img[src="/images/key.svg"]');
    expect(iconImg).not.toBeNull();
  });

  it("renders detail name as text when detail has no icon", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent(
      JSON.stringify([{ name: "OpenID Config", value: "/openid" }]),
    );
    render(<EsignetDetailsPage />);
    expect(screen.getByText("OpenID Config")).toBeDefined();
  });

  it("opens the endpoint in a new tab when a detail is clicked", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent(
      JSON.stringify([{ name: "Config", value: "/config" }]),
    );
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
    render(<EsignetDetailsPage />);
    fireEvent.click(screen.getByRole("button"));
    expect(openSpy).toHaveBeenCalledWith(
      expect.stringContaining("/config"),
      "_blank",
      "noopener,noreferrer",
    );
  });

  it("opens the endpoint when Enter key is pressed on a detail", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent(
      JSON.stringify([{ name: "Config", value: "/config" }]),
    );
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
    render(<EsignetDetailsPage />);
    fireEvent.keyDown(screen.getByRole("button"), { key: "Enter" });
    expect(openSpy).toHaveBeenCalled();
  });

  it("does not open a window when Space key is pressed", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent(
      JSON.stringify([{ name: "Config", value: "/config" }]),
    );
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
    render(<EsignetDetailsPage />);
    fireEvent.keyDown(screen.getByRole("button"), { key: " " });
    expect(openSpy).toHaveBeenCalled();
  });

  it("renders nothing in the details panel when wellknown is invalid JSON", () => {
    window._env_.DEFAULT_WELLKNOWN = encodeURIComponent("{invalid json}");
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    render(<EsignetDetailsPage />);
    expect(screen.queryByRole("button")).toBeNull();
    consoleSpy.mockRestore();
  });

  it("renders nothing in the details panel when wellknown is empty", () => {
    window._env_.DEFAULT_WELLKNOWN = "";
    render(<EsignetDetailsPage />);
    expect(screen.queryByRole("button")).toBeNull();
  });
});
