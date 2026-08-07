import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import {
  CaptchaComponent,
  CaptchaRenderer,
  GoogleReCaptcha,
  CloudflareTurnstile,
  HCaptcha,
  type CaptchaFlowComponent,
} from "../../components";
import type { ComponentRenderContext } from "@thunderid/react";

const mockT = vi.fn((key: string) => key);
const mockValidateFieldValue = vi.fn();

vi.mock("@thunderid/react", () => ({
  useTranslation: () => ({ t: mockT }),
  validateFieldValue: (...args: unknown[]) => mockValidateFieldValue(...args),
  FormControl: ({
    children,
    error,
  }: {
    children: React.ReactNode;
    error?: string;
  }) =>
    React.createElement(
      "div",
      { "data-testid": "form-control", "data-error": error },
      children,
    ),
}));

// Store captured callbacks so tests can invoke them
let capturedRecaptchaProps: Record<string, unknown> = {};

vi.mock("react-google-recaptcha", () => ({
  default: (props: Record<string, unknown>) => {
    capturedRecaptchaProps = props;
    return React.createElement("div", {
      "data-testid": "google-recaptcha",
      "data-sitekey": props.sitekey,
    });
  },
}));

vi.mock("@marsidev/react-turnstile", () => ({
  Turnstile: (props: Record<string, unknown>) => {
    return React.createElement("div", {
      "data-testid": "cloudflare-turnstile",
      "data-sitekey": props.siteKey,
    });
  },
}));

vi.mock("@hcaptcha/react-hcaptcha", () => ({
  default: (props: Record<string, unknown>) => {
    return React.createElement("div", {
      "data-testid": "hcaptcha",
      "data-sitekey": props.sitekey,
    });
  },
}));

function makeComponent(
  overrides: Partial<CaptchaFlowComponent> = {},
): CaptchaFlowComponent {
  return {
    id: "captcha-field",
    type: "CAPTCHA",
    required: true,
    captcha: { provider: "google-recaptcha", siteKey: "test-key" },
    ...overrides,
  } as CaptchaFlowComponent;
}

function makeContext(
  overrides: Partial<ComponentRenderContext> = {},
): ComponentRenderContext {
  return {
    onInputChange: vi.fn(),
    formValues: {},
    formErrors: {},
    touchedFields: {},
    onSubmit: vi.fn(),
    ...overrides,
  } as unknown as ComponentRenderContext;
}

describe("CaptchaComponent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders GoogleReCaptcha when provider is google-recaptcha", () => {
    render(
      <CaptchaComponent component={makeComponent()} context={makeContext()} />,
    );
    expect(screen.getByTestId("google-recaptcha")).toBeDefined();
  });

  it("renders CloudflareTurnstile when provider is cloudflare-turnstile", () => {
    render(
      <CaptchaComponent
        component={makeComponent({
          captcha: { provider: "cloudflare-turnstile", siteKey: "cf-key" },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.getByTestId("cloudflare-turnstile")).toBeDefined();
  });

  it("renders HCaptcha when provider is hcaptcha", () => {
    render(
      <CaptchaComponent
        component={makeComponent({
          captcha: { provider: "hcaptcha", siteKey: "hc-key" },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.getByTestId("hcaptcha")).toBeDefined();
  });

  it("shows error alert when siteKey is missing", () => {
    render(
      <CaptchaComponent
        component={makeComponent({ captcha: { provider: "google-recaptcha" } })}
        context={makeContext()}
      />,
    );
    expect(screen.getByRole("alert")).toBeDefined();
  });

  it("shows error alert when provider is not in the allowed list", () => {
    render(
      <CaptchaComponent
        component={makeComponent({
          captcha: { provider: undefined, siteKey: "key" },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.getByRole("alert")).toBeDefined();
  });

  it("shows error alert when captcha config is undefined", () => {
    render(
      <CaptchaComponent
        component={makeComponent({ captcha: undefined })}
        context={makeContext()}
      />,
    );
    // undefined captcha means isConfigValid=false → error alert is rendered
    expect(screen.getByRole("alert")).toBeDefined();
  });

  it("calls onInputChange with empty string when config is invalid", () => {
    const context = makeContext();
    render(
      <CaptchaComponent
        component={makeComponent({ captcha: { provider: "google-recaptcha" } })}
        context={context}
      />,
    );
    expect(context.onInputChange).toHaveBeenCalledWith("captcha-field", "");
  });
});

describe("GoogleReCaptcha component", () => {
  it("passes siteKey to ReCAPTCHA", () => {
    const props = {
      captcha: { siteKey: "gkey", theme: "light" as const, size: "normal" },
      handleSuccess: vi.fn(),
      handleError: vi.fn(),
      handleExpire: vi.fn(),
    };
    render(<GoogleReCaptcha {...props} />);
    expect(
      screen.getByTestId("google-recaptcha").getAttribute("data-sitekey"),
    ).toBe("gkey");
  });

  it("uses fallback values when captcha fields are undefined — covers ?? field branches", () => {
    // captcha is defined but all fields are undefined → siteKey??"", theme??"light", size??"normal"
    const props = {
      captcha: {
        siteKey: undefined,
        theme: undefined,
        size: undefined,
      } as Parameters<typeof GoogleReCaptcha>[0]["captcha"],
      handleSuccess: vi.fn(),
      handleError: vi.fn(),
      handleExpire: vi.fn(),
    };
    render(<GoogleReCaptcha {...props} />);
    expect(
      screen.getByTestId("google-recaptcha").getAttribute("data-sitekey"),
    ).toBe("");
  });

  it("uses empty object when captcha prop is undefined — covers captcha ?? {} branch", () => {
    render(
      <GoogleReCaptcha
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        captcha={undefined as any}
        handleSuccess={vi.fn()}
        handleError={vi.fn()}
        handleExpire={vi.fn()}
      />,
    );
    expect(
      screen.getByTestId("google-recaptcha").getAttribute("data-sitekey"),
    ).toBe("");
  });
});

describe("CloudflareTurnstile component", () => {
  it("passes siteKey to Turnstile", () => {
    const props = {
      captcha: { siteKey: "cfkey", theme: "light" as const },
      handleSuccess: vi.fn(),
      handleError: vi.fn(),
      handleExpire: vi.fn(),
    };
    render(<CloudflareTurnstile {...props} />);
    expect(
      screen.getByTestId("cloudflare-turnstile").getAttribute("data-sitekey"),
    ).toBe("cfkey");
  });

  it("uses fallback values when captcha fields are undefined — covers ?? field branches", () => {
    const props = {
      captcha: {
        siteKey: undefined,
        theme: undefined,
        size: undefined,
      } as Parameters<typeof CloudflareTurnstile>[0]["captcha"],
      handleSuccess: vi.fn(),
      handleError: vi.fn(),
      handleExpire: vi.fn(),
    };
    render(<CloudflareTurnstile {...props} />);
    expect(
      screen.getByTestId("cloudflare-turnstile").getAttribute("data-sitekey"),
    ).toBe("");
  });

  it("uses empty object when captcha prop is undefined — covers captcha ?? {} branch", () => {
    render(
      <CloudflareTurnstile
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        captcha={undefined as any}
        handleSuccess={vi.fn()}
        handleError={vi.fn()}
        handleExpire={vi.fn()}
      />,
    );
    expect(
      screen.getByTestId("cloudflare-turnstile").getAttribute("data-sitekey"),
    ).toBe("");
  });
});

describe("HCaptcha component", () => {
  it("passes siteKey to HCaptchaLib", () => {
    const props = {
      captcha: { siteKey: "hkey", theme: "light" as const },
      handleSuccess: vi.fn(),
      handleError: vi.fn(),
      handleExpire: vi.fn(),
    };
    render(<HCaptcha {...props} />);
    expect(screen.getByTestId("hcaptcha").getAttribute("data-sitekey")).toBe(
      "hkey",
    );
  });

  it("uses fallback values when captcha fields are undefined — covers ?? field branches", () => {
    const props = {
      captcha: {
        siteKey: undefined,
        theme: undefined,
        size: undefined,
      } as Parameters<typeof HCaptcha>[0]["captcha"],
      handleSuccess: vi.fn(),
      handleError: vi.fn(),
      handleExpire: vi.fn(),
    };
    render(<HCaptcha {...props} />);
    expect(screen.getByTestId("hcaptcha").getAttribute("data-sitekey")).toBe(
      "",
    );
  });

  it("uses empty object when captcha prop is undefined — covers captcha ?? {} branch", () => {
    render(
      <HCaptcha
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        captcha={undefined as any}
        handleSuccess={vi.fn()}
        handleError={vi.fn()}
        handleExpire={vi.fn()}
      />,
    );
    expect(screen.getByTestId("hcaptcha").getAttribute("data-sitekey")).toBe(
      "",
    );
  });
});

describe("CaptchaComponent callbacks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedRecaptchaProps = {};
  });

  it("handleSuccess calls onInputChange with the token", () => {
    const context = makeContext();
    render(<CaptchaComponent component={makeComponent()} context={context} />);
    // capturedRecaptchaProps.onChange is the handleSuccess callback
    const onChange = capturedRecaptchaProps.onChange as (t: string) => void;
    onChange("test-token");
    expect(context.onInputChange).toHaveBeenCalledWith(
      "captcha-field",
      "test-token",
    );
  });

  it("handleSuccess with null token calls onInputChange with empty string", () => {
    const context = makeContext();
    render(<CaptchaComponent component={makeComponent()} context={context} />);
    const onChange = capturedRecaptchaProps.onChange as (
      t: string | null,
    ) => void;
    onChange(null);
    expect(context.onInputChange).toHaveBeenCalledWith("captcha-field", "");
  });

  it("handleExpire calls onInputChange with empty string", () => {
    const context = makeContext();
    render(<CaptchaComponent component={makeComponent()} context={context} />);
    const onExpired = capturedRecaptchaProps.onExpired as () => void;
    onExpired();
    expect(context.onInputChange).toHaveBeenCalledWith("captcha-field", "");
  });

  it("handleError calls onInputChange with empty string", () => {
    const context = makeContext();
    render(<CaptchaComponent component={makeComponent()} context={context} />);
    const onError = capturedRecaptchaProps.onError as () => void;
    onError();
    expect(context.onInputChange).toHaveBeenCalledWith("captcha-field", "");
  });
});

describe("CaptchaRenderer", () => {
  it("wraps CaptchaComponent in a captcha-renderer div", () => {
    const { container } = render(
      <>{CaptchaRenderer(makeComponent(), makeContext())}</>,
    );
    expect(container.querySelector(".captcha-renderer")).not.toBeNull();
  });
});
