import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import NetworkErrorPage from "../../pages/NetworkErrorPage";

function renderWithRouter(state?: Record<string, unknown>) {
  const router = createMemoryRouter(
    [{ path: "*", element: <NetworkErrorPage /> }],
    {
      initialEntries: [{ pathname: "/network-error", state: state ?? null }],
    },
  );
  return render(<RouterProvider router={router} />);
}

describe("NetworkErrorPage", () => {
  const originalLocation = window.location;
  let replaceMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    replaceMock = vi.fn();

    // Redefine window.location with our mock replace method
    Object.defineProperty(window, "location", {
      writable: true,
      configurable: true,
      value: {
        ...window.location,
        replace: replaceMock,
      },
    });
    // replaceSpy = vi
    //   .spyOn(window.location, "replace")
    //   .mockImplementation(() => {});
    window.onbeforeunload = null;
  });

  afterEach(() => {
    // replaceSpy.mockRestore();
    // Restore the original location object after each test
    Object.defineProperty(window, "location", {
      writable: true,
      configurable: true,
      value: originalLocation,
    });
  });

  it("renders the no internet SVG icon", () => {
    renderWithRouter();
    expect(document.querySelector('svg[viewBox="0 0 102 102"]')).not.toBeNull();
  });

  it("renders the header text", () => {
    renderWithRouter();
    expect(screen.getByText("No Internet Connection")).toBeDefined();
  });

  it("renders the subheader text", () => {
    renderWithRouter();
    expect(
      screen.getByText("Please check your network connection and try again."),
    ).toBeDefined();
  });

  it("renders the try again button", () => {
    renderWithRouter();
    expect(screen.getByText("Try Again")).toBeDefined();
  });

  it("try again button has correct id", () => {
    renderWithRouter();
    expect(screen.getByText("Try Again").id).toBe("try_again");
  });

  it("navigates to the path from router state on Try Again", () => {
    renderWithRouter({ path: "/signin" });
    fireEvent.click(screen.getByText("Try Again"));
    expect(replaceMock).toHaveBeenCalledWith("/signin");
  });

  it('navigates to "/" when location.state has no path property', () => {
    renderWithRouter({});
    fireEvent.click(screen.getByText("Try Again"));
    expect(replaceMock).toHaveBeenCalledWith("/");
  });

  it('navigates to "/" when no location.state is set', () => {
    renderWithRouter();
    fireEvent.click(screen.getByText("Try Again"));
    expect(replaceMock).toHaveBeenCalledWith("/");
  });

  it('falls back to "/" when state path starts with "//"', () => {
    renderWithRouter({ path: "//evil.com" });
    fireEvent.click(screen.getByText("Try Again"));
    expect(replaceMock).toHaveBeenCalledWith("/");
  });

  it('falls back to "/" when state path does not start with "/"', () => {
    renderWithRouter({ path: "relative-path" });
    fireEvent.click(screen.getByText("Try Again"));
    expect(replaceMock).toHaveBeenCalledWith("/");
  });

  it("clears window.onbeforeunload on Try Again", () => {
    window.onbeforeunload = () => "";
    renderWithRouter({ path: "/" });
    fireEvent.click(screen.getByText("Try Again"));
    expect(window.onbeforeunload).toBeNull();
  });
});
