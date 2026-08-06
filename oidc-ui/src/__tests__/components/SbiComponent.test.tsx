import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, act } from "@testing-library/react";
import { SbiComponent, SbiCustomRenderer } from "../../components";
import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";

const mockInit = vi.fn();
const mockPropChange = vi.fn();

vi.mock("@mosip/secure-biometric-interface-integrator", () => ({
  init: (...args: unknown[]) => mockInit(...args),
  propChange: (...args: unknown[]) => mockPropChange(...args),
}));

vi.mock("@thunderid/react", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@thunderid/react")>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      currentLanguage: "en",
    }),
  };
});

function makeComponent(
  overrides: Partial<EmbeddedFlowComponent> = {},
): EmbeddedFlowComponent {
  return {
    id: "sbi-field",
    type: "SBI",
    ref: "biometric",
    ...overrides,
  } as EmbeddedFlowComponent;
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

describe("SbiComponent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the SBI container div", () => {
    render(
      <SbiComponent component={makeComponent()} context={makeContext()} />,
    );
    expect(
      document.getElementById("secure-biometric-interface-integration"),
    ).not.toBeNull();
  });

  it("calls init from @mosip/secure-biometric-interface-integrator on mount", () => {
    render(
      <SbiComponent component={makeComponent()} context={makeContext()} />,
    );
    expect(mockInit).toHaveBeenCalledTimes(1);
    expect(mockInit).toHaveBeenCalledWith(
      expect.objectContaining({
        buttonLabel: "scan_and_verify",
        transactionId: "transactionId",
        langCode: "en",
      }),
    );
  });

  it("calls propChange with an onCapture callback on mount", () => {
    render(
      <SbiComponent component={makeComponent()} context={makeContext()} />,
    );
    expect(mockPropChange).toHaveBeenCalledTimes(2);
    expect(mockPropChange).toHaveBeenCalledWith(
      expect.objectContaining({ onCapture: expect.any(Function) }),
    );
  });

  it("calls onInputChange with empty string on mount (fieldRef initialisation)", () => {
    const context = makeContext();
    render(<SbiComponent component={makeComponent()} context={context} />);
    expect(context.onInputChange).toHaveBeenCalledWith("biometric", "");
  });

  it("uses component.id as fieldRef when component.ref is not set", () => {
    const context = makeContext();
    render(
      <SbiComponent
        component={makeComponent({ ref: undefined })}
        context={context}
      />,
    );
    expect(context.onInputChange).toHaveBeenCalledWith("sbi-field", "");
  });

  it("invokes onInputChange and onSubmit with encoded biometrics on valid capture", async () => {
    const context = makeContext();
    render(<SbiComponent component={makeComponent()} context={context} />);

    // Extract the onCapture callback registered by propChange
    const onCapture: (resp: unknown) => Promise<void> =
      mockPropChange.mock.calls[0][0].onCapture;

    const biometricResponse = {
      biometrics: [{ error: { errorCode: "0" }, data: "some-bio" }],
    };
    const expectedEncoded = "W3siZGF0YSI6InNvbWUtYmlvIn1d";

    await act(async () => {
      await onCapture(biometricResponse);
    });

    // After valid capture, onInputChange is called with the encoded value and
    // onSubmit is called to submit the form.
    expect(context.onInputChange).toHaveBeenCalledWith(
      "biometric",
      expectedEncoded,
    );
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "sbi-field" }),
      expect.objectContaining({ biometric: expectedEncoded }),
      true,
    );
  });

  it("clears the field on null/empty biometric response", async () => {
    const context = makeContext();
    render(<SbiComponent component={makeComponent()} context={context} />);

    const onCapture: (resp: unknown) => Promise<void> =
      mockPropChange.mock.calls[0][0].onCapture;

    await act(async () => {
      await onCapture(null);
    });

    // null response → no onSubmit; onInputChange called with empty string
    expect(context.onSubmit).not.toHaveBeenCalled();
    expect(context.onInputChange).toHaveBeenLastCalledWith("biometric", "");
  });

  it("clears the field when a biometric entry has a non-zero error code", async () => {
    const context = makeContext();
    render(<SbiComponent component={makeComponent()} context={context} />);

    const onCapture: (resp: unknown) => Promise<void> =
      mockPropChange.mock.calls[0][0].onCapture;

    await act(async () => {
      await onCapture({
        biometrics: [
          { error: { errorCode: "ERR_001", errorInfo: "device error" } },
        ],
      });
    });

    expect(context.onSubmit).not.toHaveBeenCalled();
    expect(context.onInputChange).toHaveBeenLastCalledWith("biometric", "");
  });
});

describe("SbiCustomRenderer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("wraps the SbiComponent component in a sbi-custom-renderer div", () => {
    const { container } = render(
      <>{SbiCustomRenderer(makeComponent(), makeContext())}</>,
    );
    expect(container.querySelector(".sbi-custom-renderer")).not.toBeNull();
  });

  it("renders the SBI container inside the wrapper", () => {
    const { container } = render(
      <>{SbiCustomRenderer(makeComponent(), makeContext())}</>,
    );
    expect(
      container.querySelector("#secure-biometric-interface-integration"),
    ).not.toBeNull();
  });
});
