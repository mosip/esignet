import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import AppRouter from "../../routes/AppRouter";

const mockFetchThemeConfig = vi.fn();

vi.mock("../../services/config.service", () => ({
  fetchThemeConfig: (...args: unknown[]) => mockFetchThemeConfig(...args),
}));

let onlineStatus = true;
vi.mock("react-detect-offline", () => ({
  Detector: ({ render }: { render: (props: { online: boolean }) => null }) =>
    render({ online: onlineStatus }),
}));

function renderWithDataRouter(
  initialPath: string,
  state?: Record<string, unknown>,
) {
  // Split pathname from query string — createMemoryRouter's pathname must not
  // include the search string, otherwise location.pathname !== LOGIN and the
  // event-listener branch inside AppRouter is never exercised.
  const qIdx = initialPath.indexOf("?");
  const pathname = qIdx === -1 ? initialPath : initialPath.slice(0, qIdx);
  const search = qIdx === -1 ? undefined : initialPath.slice(qIdx);

  const router = createMemoryRouter([{ path: "*", element: <AppRouter /> }], {
    initialEntries: [{ pathname, search, state: state ?? null }],
  });
  return render(<RouterProvider router={router} />);
}

describe("AppRouter — routing", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onlineStatus = true;
    mockFetchThemeConfig.mockResolvedValue({
      footer: false,
      background_logo: false,
    });
  });

  it("renders something-went-wrong page", async () => {
    renderWithDataRouter("/something-went-wrong", { code: 500 });
    await waitFor(() => {
      expect(screen.getByText(/Something went wrong/)).toBeDefined();
    });
  });

  it("renders network error page", async () => {
    renderWithDataRouter("/network-error");
    await waitFor(() => {
      expect(screen.getByText("No Internet Connection")).toBeDefined();
    });
  });

  it("renders page not found for unknown routes", async () => {
    renderWithDataRouter("/unknown-route");
    await waitFor(() => {
      expect(screen.getByText("Page Not Found")).toBeDefined();
    });
  });
});

describe("AppRouter — config fetch error", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onlineStatus = true;
  });

  it("renders the router after a failed config fetch (falls back gracefully)", async () => {
    mockFetchThemeConfig.mockRejectedValue(new Error("config error"));
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    renderWithDataRouter("/page-not-found");
    await waitFor(() => {
      expect(screen.getByText("Page Not Found")).toBeDefined();
    });
    consoleSpy.mockRestore();
  });
});

describe("AppRouter — background logo variants", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onlineStatus = true;
  });

  it("renders custom background_logo images on the /signin route when configured", async () => {
    mockFetchThemeConfig.mockResolvedValue({
      background_logo: true,
    });
    const { container } = renderWithDataRouter(
      "/signin?applicationId=x&authId=y",
    );
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    // background-logo div is rendered for the /signin route when background_logo: true
    await waitFor(() => {
      expect(container.querySelector(".background-logo")).not.toBeNull();
      expect(container.querySelector(".top_left_bg_logo")).toBeNull();
    });
  });

  it("renders default background corner images when background_logo is false", async () => {
    mockFetchThemeConfig.mockResolvedValue({
      background_logo: false,
    });
    const { container } = renderWithDataRouter(
      "/signin?applicationId=x&authId=y",
    );
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    await waitFor(() => {
      expect(container.querySelector(".top_left_bg_logo")).not.toBeNull();
      expect(container.querySelector(".background-logo")).toBeNull();
    });
  });
});

describe("AppRouter — signin gesture guard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onlineStatus = true;
    mockFetchThemeConfig.mockResolvedValue({ background_logo: false });
    window.onbeforeunload = null;
  });

  afterEach(() => {
    window.onbeforeunload = null;
  });

  it("sets window.onbeforeunload on first user click when on /signin route", async () => {
    renderWithDataRouter("/signin?applicationId=x&authId=y");
    // Wait for the component to mount and register the click listener.
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    // Simulate the first user gesture — triggers registerOnFirstGesture.
    fireEvent.click(document);
    expect(window.onbeforeunload).not.toBeNull();
  });

  it("clears window.onbeforeunload when navigating away from /signin", async () => {
    window.onbeforeunload = () => "";
    renderWithDataRouter("/something-went-wrong", { code: 404 });
    await waitFor(() => {
      expect(screen.getByText(/Something went wrong/)).toBeDefined();
    });
    // The useEffect cleanup for location.pathname !== LOGIN sets onbeforeunload = null.
    expect(window.onbeforeunload).toBeNull();
  });
});

describe("AppRouter — offline detection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchThemeConfig.mockResolvedValue({ background_logo: false });
  });

  it("stays on current page when online", async () => {
    onlineStatus = true;
    renderWithDataRouter("/something-went-wrong", { code: 404 });
    await waitFor(() => {
      expect(screen.getByText(/Something went wrong/)).toBeDefined();
    });
  });

  it("redirects to network error when offline", async () => {
    onlineStatus = false;
    renderWithDataRouter("/signin?applicationId=x&authId=y");
    await waitFor(() => {
      expect(screen.getByText("No Internet Connection")).toBeDefined();
    });
  });
});
