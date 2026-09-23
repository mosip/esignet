import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import LoginPage from "../../pages/LoginPage";

const signInPropsCapture = vi.hoisted(
  () => ({ current: {} as Record<string, unknown> }),
);

vi.mock("@thunderid/react", async () => {
  const React = await import("react");
  return {
    SignIn: (props: Record<string, unknown>) => {
      signInPropsCapture.current = props;
      return React.createElement(
        "div",
        { "data-testid": "sign-in" },
        "SignIn Component",
      );
    },
    I18nContext: React.createContext(null),
  };
});

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  it("renders the SignIn component", () => {
    renderLoginPage();
    expect(screen.getByTestId("sign-in")).toBeDefined();
  });

  it("renders the page wrapper div", () => {
    renderLoginPage();
    const wrapper = document.querySelector(".\\!rounded-lg");
    expect(wrapper).not.toBeNull();
  });

  it("clears window.onbeforeunload when onSuccess is called", async () => {
    window.onbeforeunload = () => "unsaved changes";

    renderLoginPage();
    await waitFor(() => screen.getByTestId("sign-in"));

    act(() => {
      (signInPropsCapture.current.onSuccess as () => void)();
    });

    expect(window.onbeforeunload).toBeNull();
  });

  it("clears window.onbeforeunload when onError is called", async () => {
    window.onbeforeunload = () => "unsaved changes";

    renderLoginPage();
    await waitFor(() => screen.getByTestId("sign-in"));

    act(() => {
      (signInPropsCapture.current.onError as () => void)();
    });

    expect(window.onbeforeunload).toBeNull();
  });
});
