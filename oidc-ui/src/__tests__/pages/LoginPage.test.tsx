import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import LoginPage from "../../pages/LoginPage";

vi.mock("@thunderid/react", async () => {
  const React = await import("react");
  return {
    SignIn: () => React.createElement("div", { "data-testid": "sign-in" }, "SignIn Component"),
    I18nContext: React.createContext(null),
  };
});

describe("LoginPage", () => {
  it("renders the loading indicator initially", () => {
    render(
      <MemoryRouter initialEntries={["/login?applicationId=test&authId=test"]}>
        <LoginPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("status")).toBeDefined();
  });
});
