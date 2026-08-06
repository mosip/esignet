import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import React from "react";

import ResendOtpComponent from "../../components/ResendOtpComponent/ResendOtpComponent";
import ResendOtpRenderer from "../../components/ResendOtpComponent/ResendOtpRenderer";
import type { ResendOtpFlowComponent } from "../../components/ResendOtpComponent/ResendOtpModel";

import type { ComponentRenderContext } from "@thunderid/react";

const mockT = vi.fn((key: string) => key);

// Capture the captchaChanged callback that the component passes to CaptchaComponent
// so tests can call it directly. vi.hoisted() is required because vi.mock() factories
// are hoisted before imports — a plain variable would be undefined inside the factory.
const captchaChangedCapture = vi.hoisted(() => ({
  fn: undefined as ((token: string | null | undefined) => void) | undefined,
}));

vi.mock(import("@thunderid/react"), async (importOriginal) => {
  const original = (await importOriginal()) as any;
  return {
    ...original,
    useTranslation: () => ({ t: mockT }),
    Button: ({
      children,
      disabled,
      onClick,
      id,
    }: {
      children: React.ReactNode;
      disabled?: boolean;
      onClick?: () => void;
      id?: string;
    }) =>
      React.createElement(
        "button",
        { disabled, onClick, id, "data-testid": "resend-button" },
        children,
      ),
  };
});

vi.mock("../../components/index", async (importOriginal) => {
  const original = (await importOriginal()) as any;
  return {
    ...original,
    CaptchaComponent: ({
      captchaChanged,
    }: {
      captchaChanged?: (token: string | null | undefined) => void;
    }) => {
      captchaChangedCapture.fn = captchaChanged;
      return React.createElement("div", { "data-testid": "captcha" });
    },
  };
});

function makeComponent(
  overrides: Partial<ResendOtpFlowComponent> = {},
): ResendOtpFlowComponent {
  return {
    id: "resend-otp",
    type: "RESEND_OTP",
    timeLeft: 0,
    ...overrides,
  } as ResendOtpFlowComponent;
}

function makeContext(
  overrides: Partial<ComponentRenderContext> = {},
): ComponentRenderContext {
  return {
    onSubmit: vi.fn(),
    onInputChange: vi.fn(),
    formValues: {},
    formErrors: {},
    touchedFields: {},
    resetForm: vi.fn(),
    ...overrides,
  } as unknown as ComponentRenderContext;
}

// ── Tests without fake timers ──────────────────────────────────────────────────
//
// The component initialises timeLeft=true and flips it to false via useEffect.
// vi.useFakeTimers() can intercept React 18's internal scheduler, preventing that
// effect from flushing. Any test that needs timeLeft=false must use real timers.
describe("ResendOtpComponent — rendering and initial state", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    captchaChangedCapture.fn = undefined;
  });

  it("renders the resend button", () => {
    render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext()}
      />,
    );
    expect(screen.getByTestId("resend-button")).toBeDefined();
  });

  it("button is enabled when timeLeft is 0", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 0 })}
        context={makeContext()}
      />,
    );
    expect(screen.getByTestId("resend-button")).not.toBeDisabled();
  });

  it("button is disabled while the countdown is active", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 60 })}
        context={makeContext()}
      />,
    );
    expect(screen.getByTestId("resend-button")).toBeDisabled();
  });

  it("shows the countdown timer heading while time remains", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 90 })}
        context={makeContext()}
      />,
    );
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
  });

  it("does not show captcha when timeLeft > 0", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 60,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.queryByTestId("captcha")).toBeNull();
  });
});

describe("ResendOtpComponent — submit without captcha", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    captchaChangedCapture.fn = undefined;
  });

  it("calls resetForm then onSubmit with an empty payload when no captcha is configured", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 0 })}
        context={context}
      />,
    );
    fireEvent.click(screen.getByTestId("resend-button"));
    expect(context.resetForm).toHaveBeenCalled();
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "resend-otp" }),
      {},
      true,
    );
  });

  it("does not throw and skips submit body when onSubmit is absent — covers if(onSubmit) false branch", () => {
    const context = makeContext({ onSubmit: undefined as any });
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 0 })}
        context={context}
      />,
    );
    expect(() =>
      fireEvent.click(screen.getByTestId("resend-button")),
    ).not.toThrow();
  });

  it("does not throw when resetForm is absent — covers resetForm && resetForm() false branch", () => {
    const context = makeContext({ resetForm: undefined as any });
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 0 })}
        context={context}
      />,
    );
    expect(() =>
      fireEvent.click(screen.getByTestId("resend-button")),
    ).not.toThrow();
    expect(context.onSubmit).toHaveBeenCalled();
  });
});

describe("ResendOtpComponent — captcha interaction", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    captchaChangedCapture.fn = undefined;
  });

  it("shows captcha widget when timeLeft is 0 and captcha is configured", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.getByTestId("captcha")).toBeDefined();
  });

  it("blocks submission, calls onInputChange, and disables the button when captcha token is missing", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={context}
      />,
    );
    // Button starts enabled (timeLeft=false, captchaError=false)
    expect(screen.getByTestId("resend-button")).not.toBeDisabled();
    fireEvent.click(screen.getByTestId("resend-button"));
    expect(context.onSubmit).not.toHaveBeenCalled();
    expect(context.onInputChange).toHaveBeenCalledWith(
      "resend-otp_captcha",
      "",
    );
    // setCaptchaError(true) → disabled={false || true} = true
    expect(screen.getByTestId("resend-button")).toBeDisabled();
  });

  it("shows the captcha error message in the UI after a blocked submission", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.queryByText("validations.required.field.error")).toBeNull();
    fireEvent.click(screen.getByTestId("resend-button"));
    // captchaError state is now true → error div renders with the translation key
    expect(screen.getByText("validations.required.field.error")).toBeDefined();
  });

  it("captchaChanged with a valid token clears the error and keeps the button enabled", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    // captchaChangedCapture.fn is set when CaptchaComponent renders
    act(() => {
      captchaChangedCapture.fn?.("valid-token");
    });
    // setCaptchaError(false) → disabled={false || false} = false
    expect(screen.getByTestId("resend-button")).not.toBeDisabled();
    expect(screen.queryByText("validations.required.field.error")).toBeNull();
  });

  it('captchaChanged with null sets captcha error and disables the button — covers null branch of token ?? ""', () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    act(() => {
      captchaChangedCapture.fn?.(null);
    });
    // setCaptchaError(true) → disabled={false || true} = true
    expect(screen.getByTestId("resend-button")).toBeDisabled();
  });

  it("captchaChanged with an empty string sets captcha error — covers trim().length === 0 branch", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    act(() => {
      captchaChangedCapture.fn?.("");
    });
    expect(screen.getByTestId("resend-button")).toBeDisabled();
  });

  it("submits with captcha_token in payload after a valid captchaChanged call", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={context}
      />,
    );
    act(() => {
      captchaChangedCapture.fn?.("my-token");
    });
    fireEvent.click(screen.getByTestId("resend-button"));
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "resend-otp" }),
      { captcha_token: "my-token" },
      true,
    );
  });
});

// ── Tests that advance fake timers to control the countdown ───────────────────
describe("ResendOtpComponent — countdown timer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    captchaChangedCapture.fn = undefined;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("hides the countdown heading when timer reaches zero", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 1 })}
        context={makeContext()}
      />,
    );
    act(() => {
      vi.advanceTimersByTime(1500);
    });
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();
  });

  it("button becomes enabled after the countdown expires", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 1 })}
        context={makeContext()}
      />,
    );
    act(() => {
      vi.advanceTimersByTime(1500);
    });
    expect(screen.getByTestId("resend-button")).not.toBeDisabled();
  });

  it("decrements the countdown — covers return prev - 1 branch (prev > 1)", () => {
    // timeLeft: 2 → first tick: prev=2 > 1, returns prev-1=1 (false branch of if prev<=1)
    render(
      <ResendOtpComponent
        component={makeComponent({ timeLeft: 2 })}
        context={makeContext()}
      />,
    );
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();
  });

  it("shows captcha widget after timer expires when captcha is configured", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({
          timeLeft: 1,
          captcha: { provider: "hcaptcha", siteKey: "k" },
        })}
        context={makeContext()}
      />,
    );
    act(() => {
      vi.advanceTimersByTime(1500);
    });
    expect(screen.getByTestId("captcha")).toBeDefined();
  });
});

describe("ResendOtpRenderer", () => {
  it("wraps ResendOtpComponent in a back-button-renderer div", () => {
    const { container } = render(
      <>{ResendOtpRenderer(makeComponent(), makeContext())}</>,
    );
    expect(container.querySelector(".back-button-renderer")).not.toBeNull();
  });
});
