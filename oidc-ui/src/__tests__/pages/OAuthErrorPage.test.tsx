import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import OAuthErrorPage from "../../pages/OAuthErrorPage";

describe("OAuthErrorPage", () => {
  it("renders the under construction image", () => {
    render(<OAuthErrorPage />);
    expect(screen.getByAltText("login_failed")).toBeDefined();
  });

  it("renders the login failed title", () => {
    render(<OAuthErrorPage />);
    expect(screen.getByText("We couldn’t complete your login.")).toBeDefined();
  });

  it("renders the login failed description", () => {
    render(<OAuthErrorPage />);
    expect(
      screen.getByText(/There was a problem with the login request/),
    ).toBeDefined();
  });

  it("has the correct CSS classes", () => {
    const { container } = render(<OAuthErrorPage />);
    const card = container.firstElementChild;
    expect(card?.className).toContain("multipurpose-login-card");
    expect(card?.className).toContain("section-background");
  });
});
