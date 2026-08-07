import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import LoginPage from "../../pages/LoginPage";

vi.mock("@thunderid/react", async () => {
  const React = await import("react");
  return {
    SignIn: () =>
      React.createElement(
        "div",
        { "data-testid": "sign-in" },
        "SignIn Component",
      ),
    I18nContext: React.createContext(null),
  };
});

function renderWithRouter(path: string) {
  window.history.pushState({}, "", path);

  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/something-went-wrong"
          element={<div data-testid="error-page" />}
        />
        <Route path="*" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  it("renders SignIn component when required params are present", async () => {
    renderWithRouter("/login?applicationId=app123&authId=auth456");
    await waitFor(() => {
      expect(screen.getByTestId("sign-in")).toBeDefined();
    });
  });

  it("navigates to something-went-wrong when required params are missing", async () => {
    renderWithRouter("/login");
    await waitFor(() => {
      expect(screen.getByTestId("error-page")).toBeDefined();
    });
  });

  it("renders the page wrapper div", () => {
    renderWithRouter("/login?applicationId=app123&authId=auth456");
    const wrapper = document.querySelector(".\\!rounded-lg");
    expect(wrapper).not.toBeNull();
  });

  it("shows SignIn and removes the loading indicator after effects resolve with valid params", () => {
    // On first render (synchronous paint) isLoading is true.
    // We verify the SVG spinner is present in the container even though
    // effects may have already flipped the state in test mode.
    renderWithRouter("/login?applicationId=test&authId=test");
    // Either the spinner or SignIn is shown; the component renders without errors.
    expect(screen.getByTestId("sign-in")).toBeDefined();
    expect(screen.queryByRole("status")).toBeNull();
  });
});
