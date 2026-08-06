import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BackButtonComponent, BackButtonRenderer } from "../../components";
import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";

const mockT = vi.fn((key: string) => key);
const mockResolveFlowTemplateLiterals = vi.fn(
  (label: string) => `resolved:${label}`,
);

vi.mock("@thunderid/react", () => ({
  useTranslation: () => ({ t: mockT }),
  resolveFlowTemplateLiterals: (label: string, _opts: unknown) =>
    mockResolveFlowTemplateLiterals(label),
}));

function makeComponent(
  overrides: Partial<EmbeddedFlowComponent> = {},
): EmbeddedFlowComponent {
  return {
    id: "back-btn",
    type: "BACK_BUTTON",
    label: undefined,
    ...overrides,
  } as EmbeddedFlowComponent;
}

function makeContext(
  overrides: Partial<ComponentRenderContext> = {},
): ComponentRenderContext {
  return {
    onSubmit: vi.fn(),
    resetForm: vi.fn(),
    onInputChange: vi.fn(),
    formValues: {},
    formErrors: {},
    touchedFields: {},
    ...overrides,
  } as unknown as ComponentRenderContext;
}

describe("BackButtonComponent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the back button element", () => {
    render(
      <BackButtonComponent
        component={makeComponent()}
        context={makeContext()}
      />,
    );
    expect(screen.getByRole("button", { name: /app\.back/i })).toBeDefined();
  });

  it('falls back to t("app.back") when component has no label', () => {
    render(
      <BackButtonComponent
        component={makeComponent()}
        context={makeContext()}
      />,
    );
    expect(mockT).toHaveBeenCalledWith("app.back");
  });

  it("uses resolveFlowTemplateLiterals when component has a label", () => {
    const component = makeComponent({ label: "Go Back" });
    render(
      <BackButtonComponent component={component} context={makeContext()} />,
    );
    expect(mockResolveFlowTemplateLiterals).toHaveBeenCalledWith("Go Back");
    expect(screen.getByText("resolved:Go Back")).toBeDefined();
  });

  it("calls context.onSubmit with the component when clicked", () => {
    const context = makeContext();
    render(
      <BackButtonComponent component={makeComponent()} context={context} />,
    );
    fireEvent.click(screen.getByRole("button"));
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "back-btn" }),
      {},
      true,
    );
  });

  it("calls context.resetForm before onSubmit", () => {
    const context = makeContext();
    render(
      <BackButtonComponent component={makeComponent()} context={context} />,
    );
    fireEvent.click(screen.getByRole("button"));
    const resetOrder = (context.resetForm as ReturnType<typeof vi.fn>).mock
      .invocationCallOrder[0];
    const submitOrder = (context.onSubmit as ReturnType<typeof vi.fn>).mock
      .invocationCallOrder[0];
    expect(resetOrder).toBeLessThan(submitOrder);
  });

  it("renders the left-arrow SVG icon", () => {
    render(
      <BackButtonComponent
        component={makeComponent()}
        context={makeContext()}
      />,
    );
    expect(screen.getByLabelText("left_arrow_icon")).toBeDefined();
  });

  it("does not throw when clicked and context.onSubmit is undefined (false branch)", () => {
    // Covers the `if (context.onSubmit)` false path in BackButtonComponent.
    const context = makeContext({
      onSubmit: undefined as unknown as ComponentRenderContext["onSubmit"],
    });
    render(
      <BackButtonComponent component={makeComponent()} context={context} />,
    );
    expect(() => fireEvent.click(screen.getByRole("button"))).not.toThrow();
  });
});

describe("BackButtonRenderer", () => {
  it("wraps BackButtonComponent in a back-button-renderer div", () => {
    const { container } = render(
      <>{BackButtonRenderer(makeComponent(), makeContext())}</>,
    );
    expect(container.querySelector(".back-button-renderer")).not.toBeNull();
  });

  it("passes component and context through to BackButtonComponent", () => {
    const context = makeContext();
    const { container } = render(
      <>{BackButtonRenderer(makeComponent(), context)}</>,
    );
    expect(container.querySelector("#back-button")).not.toBeNull();
  });
});
