import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import type { ComponentRenderContext } from "@thunderid/react";

import SelectComponent from "../../components/SelectComponent/SelectComponent";
import SelectComponentRenderer from "../../components/SelectComponent/SelectComponentRenderer";
import type { SelectFlowComponent } from "../../components/SelectComponent/SelectModel";

const mockT = vi.fn((key: string) => key);

vi.mock("@thunderid/react", () => ({
  useTranslation: () => ({ t: mockT }),
  // Mirrors the SDK: "{{t(ns:key)}}" -> t("ns.key"), other text unchanged.
  resolveFlowTemplateLiterals: (
    text: string,
    { t }: { t: (key: string) => string },
  ) =>
    text.replace(/\{\{\s*t\(([^)]+)\)\s*\}\}/g, (_m, key: string) =>
      t(key.replace(/:/g, ".")),
    ),
}));

const OPTIONS = [
  { label: "VID", value: "login_id_uin" },
  { label: "Mobile", value: "login_id_mobile" },
  { label: "Email", value: "login_id_email" },
  { label: "NRC", value: "login_id_nrc" },
];

function makeComponent(
  overrides: Partial<SelectFlowComponent> = {},
): SelectFlowComponent {
  return {
    id: "SELECT_LOGIN_ID",
    ref: "login_id_select",
    type: "CUSTOM",
    label: "Select Login Id",
    placeholder: "Select a login Id",
    required: true,
    options: OPTIONS,
    ...overrides,
  } as unknown as SelectFlowComponent;
}

function makeContext(
  overrides: Partial<ComponentRenderContext> = {},
): ComponentRenderContext {
  return {
    onSubmit: vi.fn(),
    resetForm: vi.fn(),
    onInputChange: vi.fn(),
    onInputBlur: vi.fn(),
    formValues: {},
    formErrors: {},
    touchedFields: {},
    ...overrides,
  } as unknown as ComponentRenderContext;
}

describe("SelectComponent", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders a plain label and placeholder as given, without a hidden field", () => {
    const { container } = render(
      <SelectComponent component={makeComponent()} context={makeContext()} />,
    );
    expect(mockT).not.toHaveBeenCalledWith("Select Login Id");
    expect(screen.getByText("Select Login Id")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toHaveTextContent("Select a login Id");
    expect(container.querySelector('input[type="hidden"]')).toBeNull();
  });

  it("labels the trigger without a label element, so clicking the label does not toggle it", () => {
    render(
      <SelectComponent component={makeComponent()} context={makeContext()} />,
    );
    expect(document.querySelector("label")).toBeNull();
    const trigger = screen.getByRole("combobox", { name: /Select Login Id/ });
    fireEvent.click(screen.getByText("Select Login Id"));
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("translates a templated label and placeholder", () => {
    render(
      <SelectComponent
        component={makeComponent({
          label: "{{t(sample:label)}}",
          placeholder: "{{t(sample:placeholder)}}",
        })}
        context={makeContext()}
      />,
    );
    expect(mockT).toHaveBeenCalledWith("sample.label");
    expect(mockT).toHaveBeenCalledWith("sample.placeholder");
    expect(screen.getByText("sample.label")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toHaveTextContent(
      "sample.placeholder",
    );
  });

  it("shows the labelKey text above the select when the flow gives no label", () => {
    const { container } = render(
      <SelectComponent
        component={makeComponent({ label: undefined, placeholder: undefined })}
        context={makeContext()}
        labelKey="dropdown.login_id_label"
      />,
    );
    expect(mockT).toHaveBeenCalledWith("dropdown.login_id_label");
    expect(
      container.querySelector(".login-id-dropdown__label"),
    ).toHaveTextContent("dropdown.login_id_label");
    expect(
      screen.getByRole("combobox", { name: /dropdown\.login_id_label/ }),
    ).toBeInTheDocument();
  });

  it("prefers the flow's label over the labelKey", () => {
    render(
      <SelectComponent
        component={makeComponent()}
        context={makeContext()}
        labelKey="dropdown.login_id_label"
      />,
    );
    expect(mockT).not.toHaveBeenCalledWith("dropdown.login_id_label");
    expect(screen.getByText("Select Login Id")).toBeInTheDocument();
  });

  it("has no login id defaults of its own", () => {
    const { container } = render(
      <SelectComponent
        component={makeComponent({
          label: undefined,
          placeholder: undefined,
          selectedValue: "login_id_email",
        })}
        context={makeContext()}
      />,
    );
    expect(container.querySelector(".login-id-dropdown__label")).toBeNull();
    expect(container.querySelector(".login-id-dropdown__icon")).toBeNull();
  });

  it("uses the icons map unless the option has its own icon", () => {
    render(
      <SelectComponent
        component={makeComponent({
          options: [
            { label: "Mobile", value: "login_id_mobile" },
            { label: "Own", value: "own", icon: "/images/custom.svg" },
          ] as unknown as SelectFlowComponent["options"],
        })}
        context={makeContext()}
        icons={{ login_id_mobile: "/m.svg", own: "/ignored.svg" }}
      />,
    );
    fireEvent.click(screen.getByRole("combobox"));
    const [mobile, own] = screen.getAllByRole("option");
    const mask = (el: HTMLElement) =>
      (el.querySelector(".login-id-dropdown__icon") as HTMLElement).style
        .maskImage;
    expect(mask(mobile)).toContain("/m.svg");
    expect(mask(own)).toContain("custom.svg");
  });

  it("shows the step's selectedValue over a form value left from the previous step", () => {
    const context = makeContext({
      formValues: { login_id_select: "login_id_mobile" },
    });
    render(
      <SelectComponent
        component={makeComponent({ selectedValue: "login_id_uin" })}
        context={context}
      />,
    );
    expect(screen.getByRole("combobox")).toHaveTextContent("VID");
    expect(context.onInputChange).toHaveBeenCalledWith(
      "login_id_select",
      "login_id_uin",
    );
  });

  it("shows selectedValue when the form has no value", () => {
    render(
      <SelectComponent
        component={makeComponent({ selectedValue: "login_id_uin" })}
        context={makeContext()}
      />,
    );
    expect(screen.getByRole("combobox")).toHaveTextContent("VID");
  });

  it("resets the form and submits with the chosen id on selection", () => {
    const context = makeContext();
    const component = makeComponent();
    render(<SelectComponent component={component} context={context} />);

    fireEvent.click(screen.getByRole("combobox"));
    fireEvent.click(screen.getByRole("option", { name: "Mobile" }));

    expect(context.onInputChange).not.toHaveBeenCalled();
    expect(context.resetForm).toHaveBeenCalled();
    expect(context.onSubmit).toHaveBeenCalledWith(
      { ...component, id: "login_id_mobile" },
      {},
      true,
    );
  });

  it("shows the chosen option while submitting and reverts once the submit settles", async () => {
    let settle!: () => void;
    const onSubmit = vi.fn(
      () => new Promise<void>((resolve) => (settle = resolve)),
    );
    render(
      <SelectComponent
        component={makeComponent({ selectedValue: "login_id_uin" })}
        context={makeContext({
          onSubmit,
          formValues: { login_id_select: "login_id_uin" },
        } as Partial<ComponentRenderContext>)}
      />,
    );
    fireEvent.click(screen.getByRole("combobox"));
    fireEvent.click(screen.getByRole("option", { name: "Mobile" }));
    expect(screen.getByRole("combobox")).toHaveTextContent("Mobile");

    await act(async () => settle());
    expect(screen.getByRole("combobox")).toHaveTextContent("VID");
  });

  it("reverts without an unhandled rejection when the submit fails", async () => {
    let fail!: (e: Error) => void;
    const onSubmit = vi.fn(
      () => new Promise<void>((_resolve, reject) => (fail = reject)),
    );
    const unhandled = vi.fn();
    process.on("unhandledRejection", unhandled);
    try {
      render(
        <SelectComponent
          component={makeComponent({ selectedValue: "login_id_uin" })}
          context={makeContext({ onSubmit } as Partial<ComponentRenderContext>)}
        />,
      );
      fireEvent.click(screen.getByRole("combobox"));
      fireEvent.click(screen.getByRole("option", { name: "Mobile" }));
      expect(screen.getByRole("combobox")).toHaveTextContent("Mobile");
      await act(async () => fail(new Error("boom")));
      await new Promise((r) => setTimeout(r, 0));
      expect(screen.getByRole("combobox")).toHaveTextContent("VID");
      expect(unhandled).not.toHaveBeenCalled();
    } finally {
      process.off("unhandledRejection", unhandled);
    }
  });

  it("does not sync again when only the context object changes", () => {
    const onInputChange = vi.fn();
    const component = makeComponent({ selectedValue: "login_id_uin" });
    const { rerender } = render(
      <SelectComponent
        component={component}
        context={makeContext({
          onInputChange,
          formValues: { login_id_select: "login_id_uin" },
        })}
      />,
    );
    rerender(
      <SelectComponent
        component={component}
        context={makeContext({
          onInputChange,
          formValues: { login_id_select: "login_id_uin", username: "x" },
        })}
      />,
    );
    expect(onInputChange).not.toHaveBeenCalled();
  });

  it("does not sync when the form already holds the selected value", () => {
    const context = makeContext({
      formValues: { login_id_select: "login_id_uin" },
    });
    render(
      <SelectComponent
        component={makeComponent({ selectedValue: "login_id_uin" })}
        context={context}
      />,
    );
    expect(context.onInputChange).not.toHaveBeenCalled();
  });

  it("falls back to the component id when ref is missing", () => {
    const context = makeContext();
    render(
      <SelectComponent
        component={makeComponent({ ref: undefined, selectedValue: "login_id_nrc" })}
        context={context}
      />,
    );
    expect(context.onInputChange).toHaveBeenCalledWith(
      "SELECT_LOGIN_ID",
      "login_id_nrc",
    );
  });

  it("only shows errors once the field is touched", () => {
    const { rerender } = render(
      <SelectComponent
        component={makeComponent()}
        context={makeContext({
          formErrors: { login_id_select: "Pick one" },
        })}
      />,
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    rerender(
      <SelectComponent
        component={makeComponent()}
        context={makeContext({
          formErrors: { login_id_select: "Pick one" },
          touchedFields: { login_id_select: true },
        })}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Pick one");
  });

  it("translates option labels given as flow templates", () => {
    mockT.mockImplementation((key: string) =>
      key === "dropdown.mobile" ? "Mobile Number" : key,
    );
    render(
      <SelectComponent
        component={makeComponent({
          selectedValue: "login_id_mobile",
          options: [
            { label: "{{t(dropdown:mobile)}}", value: "login_id_mobile" },
          ] as unknown as SelectFlowComponent["options"],
        })}
        context={makeContext()}
      />,
    );
    expect(mockT).toHaveBeenCalledWith("dropdown.mobile");
    expect(screen.getByRole("combobox")).toHaveTextContent("Mobile Number");
    mockT.mockImplementation((key: string) => key);
  });

  it("accepts string options", () => {
    render(
      <SelectComponent
        component={makeComponent({
          selectedValue: "b",
          options: ["a", "b"] as unknown as SelectFlowComponent["options"],
        })}
        context={makeContext()}
      />,
    );
    expect(screen.getByRole("combobox")).toHaveTextContent("b");
  });
});

describe("SelectComponentRenderer", () => {
  beforeEach(() => vi.clearAllMocks());

  it("adds the login id icons and the Login Id label", () => {
    const { container } = render(
      <>
        {SelectComponentRenderer(
          makeComponent({
            label: undefined,
            placeholder: undefined,
            selectedValue: "login_id_email",
          }),
          makeContext(),
        )}
      </>,
    );
    expect(mockT).toHaveBeenCalledWith("dropdown.login_id_label");
    expect(
      container.querySelector(".login-id-dropdown__label"),
    ).toHaveTextContent("dropdown.login_id_label");
    const icon = container.querySelector(
      ".login-id-dropdown__icon",
    ) as HTMLElement;
    expect(icon.style.maskImage).toContain("email_icon.svg");
  });

  it("syncs selectedValue into the form after render and renders the component", () => {
    const context = makeContext();
    render(
      <>
        {SelectComponentRenderer(
          makeComponent({ selectedValue: "login_id_email" }),
          context,
        )}
      </>,
    );
    expect(context.onInputChange).toHaveBeenCalledWith(
      "login_id_select",
      "login_id_email",
    );
    expect(screen.getByRole("combobox")).toHaveTextContent("Email");
  });
});
