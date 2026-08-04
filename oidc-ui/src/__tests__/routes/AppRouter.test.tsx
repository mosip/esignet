import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import AppRouter from "../../routes/AppRouter";

vi.mock("../../services/config.service", () => ({
  fetchThemeConfig: () =>
    Promise.resolve({ footer: false, background_logo: false }),
}));

vi.mock("react-detect-offline", () => ({
  Detector: ({ render }: { render: (props: { online: boolean }) => null }) =>
    render({ online: true }),
}));

describe("AppRouter", () => {
  it("renders something wrong page on /something-went-wrong route", async () => {
    const router = createMemoryRouter([{ path: "*", element: <AppRouter /> }], {
      initialEntries: [
        { pathname: "/something-went-wrong", state: { code: 500 } },
      ],
    });
    render(<RouterProvider router={router} />);
    await waitFor(() => {
      expect(screen.getByText(/Something went wrong/)).toBeDefined();
    });
  });

  it("renders network error page on /network-error route", async () => {
    const router = createMemoryRouter([{ path: "*", element: <AppRouter /> }], {
      initialEntries: ["/network-error"],
    });
    render(<RouterProvider router={router} />);
    await waitFor(() => {
      expect(screen.getByText("No Internet Connection")).toBeDefined();
    });
  });

  it("renders page not found for unknown routes", async () => {
    const router = createMemoryRouter([{ path: "*", element: <AppRouter /> }], {
      initialEntries: ["unknown-route"],
    });
    render(<RouterProvider router={router} />);
    await waitFor(() => {
      expect(screen.getByText("404 Not Found")).toBeDefined();
    });
  });
});
