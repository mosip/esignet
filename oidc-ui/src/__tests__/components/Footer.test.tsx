import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import Footer from "../../components/Footer";

const mockFetchThemeConfig = vi.fn();

vi.mock("../../services/config.service", () => ({
  fetchThemeConfig: (...args: unknown[]) => mockFetchThemeConfig(...args),
}));

describe("Footer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders nothing when config has footer: false", async () => {
    mockFetchThemeConfig.mockResolvedValue({ footer: false });
    const { container } = render(<Footer />);
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    expect(container.querySelector("#footer")).toBeNull();
  });

  it("renders footer when config has footer: true", async () => {
    mockFetchThemeConfig.mockResolvedValue({ footer: true });
    render(<Footer />);
    await waitFor(() => {
      expect(screen.getByText("Powered by")).toBeDefined();
    });
  });

  it("renders nothing initially while loading", () => {
    mockFetchThemeConfig.mockReturnValue(new Promise(() => {}));
    const { container } = render(<Footer />);
    expect(container.querySelector("#footer")).toBeNull();
  });

  it("handles config fetch error gracefully", async () => {
    mockFetchThemeConfig.mockRejectedValue(new Error("fail"));
    const { container } = render(<Footer />);
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    expect(container.querySelector("#footer")).toBeNull();
  });

  it("skips setConfig when component unmounts before fetch resolves (mounted guard)", async () => {
    // Resolve only after we unmount — covers the `if (mounted)` false branch.
    let resolveFetch!: (value: unknown) => void;
    mockFetchThemeConfig.mockReturnValue(
      new Promise((resolve) => {
        resolveFetch = resolve;
      }),
    );

    const consoleSpy = vi.spyOn(console, "error");

    const { unmount } = render(<Footer />);
    // Unmount before the fetch resolves — sets mounted = false.
    unmount();
    // Now resolve; the guard prevents a state update on the unmounted component.
    await act(async () => {
      resolveFetch({ footer: true });
    });
    expect(consoleSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("unmounted"),
    );
    consoleSpy.mockRestore();
    // If the guard fires correctly no "state update on unmounted" error is thrown.
    expect(mockFetchThemeConfig).toHaveBeenCalled();
  });
});
