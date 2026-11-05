import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import React from "react";

import ResendOtpComponent from "../../components/ResendOtpComponent/ResendOtpComponent";
import ResendOtpRenderer from "../../components/ResendOtpComponent/ResendOtpRenderer";
import type { ResendOtpFlowComponent } from "../../components/ResendOtpComponent/ResendOtpModel";

import type { ComponentRenderContext } from "@thunderid/react";

// Returns the key itself so assertions can match on translation keys.
const mockT = vi.fn((key: string) => key);

// Capture the captchaChanged callback that the component passes to CaptchaComponent
// so tests can call it directly. vi.hoisted() is required because vi.mock() factories
// are hoisted before imports — a plain variable would be undefined inside the factory.
const captchaChangedCapture = vi.hoisted(() => ({
  fn: undefined as ((token: string | null | undefined) => void) | undefined,
}));

vi.mock(import("@thunderid/react"), async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    useTranslation: () => ({
      t: mockT,
      availableLanguages: [],
      currentLanguage: "en",
      setLanguage: vi.fn(),
    }),
  };
});

vi.mock("../../components/index", async (importOriginal) => {
  const original = (await importOriginal()) as Record<string, unknown>;
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

const CAPTCHA = { provider: "hcaptcha" as const, siteKey: "k" };

// The mocked t() returns the key, so assertions match on translation keys.
const TEXT = {
  prompt: "otp.didnt_receive_otp",
  captchaPrompt: "otp.resend_captcha_prompt",
  requiredError: "validations.required.field.error",
  limitReached: "otp.resend_limit_reached",
};

function makeComponent(
  overrides: Partial<ResendOtpFlowComponent> = {},
): ResendOtpFlowComponent {
  return {
    id: "resend-otp",
    type: "RESEND_OTP",
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
    isLoading: false,
    additionalData: {},
    ...overrides,
  } as unknown as ComponentRenderContext;
}

const resendLink = () => screen.getByTestId("thunderid-resend-otp-button");
const queryResendLink = () =>
  screen.queryByTestId("thunderid-resend-otp-button");
const cancelLink = () => screen.getByTestId("thunderid-resend-otp-cancel");

beforeEach(() => {
  vi.clearAllMocks();
  captchaChangedCapture.fn = undefined;
});

describe("ResendOtpComponent — countdown state", () => {
  it("uses timeLeft from backend additionalData for the countdown", () => {
    render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({
          additionalData: { timeLeft: "180", attemptsLeft: "2" },
        })}
      />,
    );
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
    expect(screen.getByText(/3:00/)).toBeDefined();
    expect(queryResendLink()).toBeNull();
  });

  it("does not show the captcha while the countdown is active", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={makeContext({ additionalData: { timeLeft: "60" } })}
      />,
    );
    expect(screen.queryByTestId("captcha")).toBeNull();
  });
});

describe("ResendOtpComponent — collapsed state once the countdown elapses", () => {
  it("shows the prompt and the resend action", () => {
    render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({ additionalData: { timeLeft: "0" } })}
      />,
    );
    expect(screen.getByText(TEXT.prompt)).toBeDefined();
    expect(resendLink()).toBeDefined();
  });

  it("keeps the captcha hidden until the user asks to resend", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={makeContext()}
      />,
    );
    expect(screen.queryByTestId("captcha")).toBeNull();
  });

  it("groups the captcha inside the resend panel, not loose in the card", () => {
    const { container } = render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={makeContext()}
      />,
    );
    fireEvent.click(resendLink());
    const panel = container.querySelector(".resend-otp-panel");
    expect(panel).not.toBeNull();
    expect(panel?.contains(screen.getByTestId("captcha"))).toBe(true);
  });
});

describe("ResendOtpComponent — attempt limit and in-flight state", () => {
  it("replaces the resend action with the limit-reached message when no attempts remain", () => {
    render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({
          additionalData: { timeLeft: "0", attemptsLeft: "0" },
        })}
      />,
    );
    expect(screen.getByText(TEXT.limitReached)).toBeDefined();
    expect(queryResendLink()).toBeNull();
  });

  it("does not submit when no attempts remain", () => {
    // No resend action is rendered in this state, so nothing can trigger a submit.
    const context = makeContext({
      additionalData: { timeLeft: "0", attemptsLeft: "0" },
    });
    render(
      <ResendOtpComponent component={makeComponent()} context={context} />,
    );
    expect(queryResendLink()).toBeNull();
    expect(context.onSubmit).not.toHaveBeenCalled();
  });

  it("disables the resend action while the request is in flight", () => {
    const context = makeContext({ additionalData: { attemptsLeft: "2" } });
    render(
      <ResendOtpComponent component={makeComponent()} context={context} />,
    );
    fireEvent.click(resendLink());
    expect(resendLink()).toBeDisabled();
  });
});

describe("ResendOtpComponent — resend without a captcha configured", () => {
  it("clears only the OTP field and submits an empty payload", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent component={makeComponent()} context={context} />,
    );
    fireEvent.click(resendLink());
    expect(context.onInputChange).toHaveBeenCalledWith("otp", "");
    expect(context.resetForm).not.toHaveBeenCalled();
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "resend-otp" }),
      {},
      true,
    );
  });

  it("does not throw when onSubmit is absent", () => {
    const context = makeContext({ onSubmit: undefined });
    render(
      <ResendOtpComponent component={makeComponent()} context={context} />,
    );
    expect(() => fireEvent.click(resendLink())).not.toThrow();
  });
});

describe("ResendOtpComponent — captcha-gated resend", () => {
  it("reveals the captcha instead of submitting on the first click", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={context}
      />,
    );
    fireEvent.click(resendLink());
    expect(screen.getByTestId("captcha")).toBeDefined();
    expect(screen.getByText(TEXT.captchaPrompt)).toBeDefined();
    expect(context.onSubmit).not.toHaveBeenCalled();
  });

  it("submits with the captcha token once the captcha is solved", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={context}
      />,
    );
    fireEvent.click(resendLink());
    act(() => captchaChangedCapture.fn?.("my-token"));

    expect(context.onInputChange).toHaveBeenCalledWith("otp", "");
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "resend-otp" }),
      { captcha_token: "my-token" },
      true,
    );
  });

  it("collapses the panel back to the prompt after a successful resend", () => {
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={makeContext()}
      />,
    );
    fireEvent.click(resendLink());
    act(() => captchaChangedCapture.fn?.("my-token"));

    expect(screen.queryByTestId("captcha")).toBeNull();
    expect(screen.getByText(TEXT.prompt)).toBeDefined();
  });

  it("submits only once when the captcha token is refreshed", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={context}
      />,
    );
    fireEvent.click(resendLink());
    act(() => captchaChangedCapture.fn?.("token-a"));
    act(() => captchaChangedCapture.fn?.("token-b"));

    expect(context.onSubmit).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["null", null],
    ["an empty string", ""],
    ["whitespace", "   "],
  ])("shows an error and does not submit for %s", (_label, token) => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={context}
      />,
    );
    fireEvent.click(resendLink());
    act(() => captchaChangedCapture.fn?.(token));

    expect(screen.getByText(TEXT.requiredError)).toBeDefined();
    expect(context.onSubmit).not.toHaveBeenCalled();
  });

  it("cancel hides the captcha, clears the stored token and restores the prompt", () => {
    const context = makeContext();
    render(
      <ResendOtpComponent
        component={makeComponent({ captcha: CAPTCHA })}
        context={context}
      />,
    );
    fireEvent.click(resendLink());
    fireEvent.click(cancelLink());

    expect(screen.queryByTestId("captcha")).toBeNull();
    expect(screen.getByText(TEXT.prompt)).toBeDefined();
    expect(context.onInputChange).toHaveBeenCalledWith(
      "resend-otp_captcha",
      "",
    );
    expect(context.onSubmit).not.toHaveBeenCalled();
  });
});

describe("ResendOtpComponent — countdown timer", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("decrements each second and reveals the prompt at zero", () => {
    render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({ additionalData: { timeLeft: "2" } })}
      />,
    );
    act(() => void vi.advanceTimersByTime(1000));
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();

    act(() => void vi.advanceTimersByTime(1000));
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();
    expect(resendLink()).toBeDefined();
  });

  it("does not start a countdown until the backend confirms the resend", () => {
    const context = makeContext({
      additionalData: { timeLeft: "0", attemptsLeft: "2" },
    });
    render(
      <ResendOtpComponent component={makeComponent()} context={context} />,
    );
    fireEvent.click(resendLink());

    expect(context.onSubmit).toHaveBeenCalled();
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();
  });

  it("starts the countdown once the backend confirms new data after a resend", () => {
    const { rerender } = render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({
          additionalData: { timeLeft: "0", attemptsLeft: "2" },
        })}
      />,
    );
    fireEvent.click(resendLink());

    // Backend confirms the resend: new timeLeft, one fewer attempt.
    rerender(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({
          additionalData: { timeLeft: "2", attemptsLeft: "1" },
        })}
      />,
    );
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
  });

  it("restarts the countdown when the backend reports a new attempt count", () => {
    const { rerender } = render(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({
          additionalData: { timeLeft: "2", attemptsLeft: "3" },
        })}
      />,
    );
    act(() => void vi.advanceTimersByTime(2000));
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();

    // Same timeLeft, one fewer attempt — still a fresh countdown.
    rerender(
      <ResendOtpComponent
        component={makeComponent()}
        context={makeContext({
          additionalData: { timeLeft: "2", attemptsLeft: "2" },
        })}
      />,
    );
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
  });
});

describe("ResendOtpRenderer", () => {
  it("wraps ResendOtpComponent in a resend-otp-renderer div", () => {
    const { container } = render(
      <>{ResendOtpRenderer(makeComponent(), makeContext())}</>,
    );
    expect(container.querySelector(".resend-otp-renderer")).not.toBeNull();
  });
});
