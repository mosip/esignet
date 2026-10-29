import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

import LoginIdDropdown, {
  type DropdownOption,
} from "../../components/SelectComponent/LoginIdDropdown";

const OPTIONS: DropdownOption[] = [
  { label: "VID", value: "login_id_uin", icon: "/images/vid_icon.svg" },
  { label: "Mobile", value: "login_id_mobile", icon: "/images/mobile_icon.svg" },
  { label: "Email", value: "login_id_email" },
  { label: "NRC", value: "login_id_nrc" },
];

const trigger = () => screen.getByRole("combobox");
const listbox = () => screen.queryByRole("listbox");
const iconOf = (el: Element) =>
  el.querySelector(".login-id-dropdown__icon") as HTMLElement | null;

describe("LoginIdDropdown", () => {
  beforeEach(() => vi.clearAllMocks());

  describe("rendering", () => {
    it("shows the placeholder when nothing is selected and the list is closed", () => {
      render(<LoginIdDropdown options={OPTIONS} placeholder="Pick one" />);
      expect(trigger()).toHaveTextContent("Pick one");
      expect(trigger()).not.toHaveClass("login-id-dropdown__trigger--selected");
      expect(trigger()).toHaveAttribute("aria-expanded", "false");
      expect(listbox()).not.toBeInTheDocument();
    });

    it("shows the placeholder when the value matches no option", () => {
      render(
        <LoginIdDropdown options={OPTIONS} value="unknown" placeholder="Pick" />,
      );
      expect(trigger()).toHaveTextContent("Pick");
    });

    it("shows the selected option's label and icon in the trigger", () => {
      render(<LoginIdDropdown options={OPTIONS} value="login_id_mobile" />);
      expect(trigger()).toHaveTextContent("Mobile");
      expect(trigger()).toHaveClass("login-id-dropdown__trigger--selected");
      const icon = iconOf(trigger())!;
      expect(icon.style.maskImage).toContain("mobile_icon.svg");
      expect(icon).toHaveAttribute("aria-hidden", "true");
    });

    it("renders no icon when the option has none", () => {
      render(<LoginIdDropdown options={OPTIONS} value="login_id_email" />);
      expect(iconOf(trigger())).toBeNull();
    });

    it("uses the given id and a generated one otherwise", () => {
      const { unmount } = render(
        <LoginIdDropdown id="my-select" options={OPTIONS} />,
      );
      expect(trigger()).toHaveAttribute("id", "my-select");
      unmount();
      render(<LoginIdDropdown options={OPTIONS} />);
      expect(trigger().id).not.toBe("");
    });

    it("adds a custom class to the root", () => {
      const { container } = render(
        <LoginIdDropdown options={OPTIONS} className="extra" />,
      );
      expect(container.firstChild).toHaveClass("login-id-dropdown", "extra");
    });
  });

  describe("label and accessibility", () => {
    it("names the trigger and list by the label, without a label element", () => {
      render(<LoginIdDropdown id="sel" options={OPTIONS} label="Login Id" />);
      expect(document.querySelector("label")).toBeNull();
      expect(trigger()).toHaveAttribute("aria-labelledby", "sel-label");
      expect(screen.getByRole("combobox", { name: "Login Id" })).toBeVisible();
      fireEvent.click(trigger());
      expect(screen.getByRole("listbox")).toHaveAttribute(
        "aria-labelledby",
        "sel-label",
      );
    });

    it("does not toggle the list when the label is clicked", () => {
      render(<LoginIdDropdown options={OPTIONS} label="Login Id" />);
      fireEvent.click(screen.getByText("Login Id"));
      expect(listbox()).not.toBeInTheDocument();
    });

    it("marks a required field with a hidden asterisk and aria-required", () => {
      const { container } = render(
        <LoginIdDropdown options={OPTIONS} label="Login Id" required />,
      );
      const star = container.querySelector(".login-id-dropdown__required");
      expect(star).toHaveTextContent("*");
      expect(star).toHaveAttribute("aria-hidden", "true");
      expect(trigger()).toHaveAttribute("aria-required", "true");
    });

    it("omits aria-required when not required", () => {
      render(<LoginIdDropdown options={OPTIONS} label="Login Id" />);
      expect(trigger()).not.toHaveAttribute("aria-required");
    });

    it("uses ariaLabel only when there is no visible label", () => {
      const { rerender } = render(
        <LoginIdDropdown options={OPTIONS} ariaLabel="Login id type" />,
      );
      expect(trigger()).toHaveAttribute("aria-label", "Login id type");
      expect(trigger()).not.toHaveAttribute("aria-labelledby");

      rerender(
        <LoginIdDropdown
          options={OPTIONS}
          label="Login Id"
          ariaLabel="Login id type"
        />,
      );
      expect(trigger()).not.toHaveAttribute("aria-label");
    });

    it("links the trigger to the list and the active option", () => {
      render(<LoginIdDropdown id="sel" options={OPTIONS} />);
      expect(trigger()).toHaveAttribute("aria-controls", "sel-listbox");
      expect(trigger()).toHaveAttribute("aria-haspopup", "listbox");
      expect(trigger()).not.toHaveAttribute("aria-activedescendant");

      fireEvent.click(trigger());
      expect(trigger()).toHaveAttribute("aria-expanded", "true");
      expect(trigger()).toHaveAttribute("data-open", "true");
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        "sel-listbox-option-0",
      );
      expect(screen.getByRole("listbox")).toHaveAttribute("id", "sel-listbox");
    });
  });

  describe("list", () => {
    it("opens on click and marks the selected option with a check", () => {
      render(<LoginIdDropdown options={OPTIONS} value="login_id_mobile" />);
      fireEvent.click(trigger());
      const options = screen.getAllByRole("option");
      expect(options).toHaveLength(4);
      expect(options[1]).toHaveAttribute("aria-selected", "true");
      expect(options[1]).toHaveClass("login-id-dropdown__option--selected");
      expect(options[1].querySelector(".login-id-dropdown__check")).not.toBeNull();
      expect(options[0]).toHaveAttribute("aria-selected", "false");
      expect(options[0].querySelector(".login-id-dropdown__check")).toBeNull();
    });

    it("starts with the selected option active", () => {
      render(<LoginIdDropdown options={OPTIONS} value="login_id_email" />);
      fireEvent.click(trigger());
      expect(screen.getAllByRole("option")[2]).toHaveClass(
        "login-id-dropdown__option--active",
      );
    });

    it("shows each option's icon", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.click(trigger());
      const [vid, , email] = screen.getAllByRole("option");
      expect(iconOf(vid)!.style.maskImage).toContain("vid_icon.svg");
      expect(iconOf(email)).toBeNull();
    });

    it("calls onChange with the clicked option's value, closes and refocuses", () => {
      const onChange = vi.fn();
      render(<LoginIdDropdown options={OPTIONS} onChange={onChange} />);
      fireEvent.click(trigger());
      fireEvent.click(screen.getByRole("option", { name: "NRC" }));
      expect(onChange).toHaveBeenCalledWith("login_id_nrc");
      expect(listbox()).not.toBeInTheDocument();
      expect(trigger()).toHaveFocus();
    });

    it("does not call onChange when re-selecting the current value", () => {
      const onChange = vi.fn();
      render(
        <LoginIdDropdown
          options={OPTIONS}
          value="login_id_uin"
          onChange={onChange}
        />,
      );
      fireEvent.click(trigger());
      fireEvent.click(screen.getByRole("option", { name: "VID" }));
      expect(onChange).not.toHaveBeenCalled();
      expect(listbox()).not.toBeInTheDocument();
    });

    it("moves the active row on hover", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.click(trigger());
      const email = screen.getByRole("option", { name: "Email" });
      fireEvent.mouseEnter(email);
      expect(email).toHaveClass("login-id-dropdown__option--active");
    });

    it("keeps focus on the trigger when pressing an option", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.click(trigger());
      const pressed = fireEvent.mouseDown(
        screen.getByRole("option", { name: "Email" }),
      );
      // mousedown default is prevented so the trigger does not blur.
      expect(pressed).toBe(false);
    });

    it("scrolls the active row into view", () => {
      // jsdom has no scrollIntoView; the component calls it optionally.
      const original = Element.prototype.scrollIntoView;
      const scrollIntoView = vi.fn();
      Element.prototype.scrollIntoView = scrollIntoView;
      try {
        render(<LoginIdDropdown options={OPTIONS} />);
        fireEvent.keyDown(trigger(), { key: "ArrowDown" });
        fireEvent.keyDown(trigger(), { key: "ArrowDown" });
        expect(scrollIntoView).toHaveBeenCalledWith({ block: "nearest" });
      } finally {
        Element.prototype.scrollIntoView = original;
      }
    });
  });

  describe("closing", () => {
    it("closes without selecting on a mouse click while open", () => {
      const onChange = vi.fn();
      render(<LoginIdDropdown options={OPTIONS} onChange={onChange} />);
      fireEvent.click(trigger(), { detail: 1 });
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.click(trigger(), { detail: 1 });
      expect(listbox()).not.toBeInTheDocument();
      expect(onChange).not.toHaveBeenCalled();
    });

    it("closes on Escape", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.click(trigger());
      fireEvent.keyDown(trigger(), { key: "Escape" });
      expect(listbox()).not.toBeInTheDocument();
    });

    it("closes on Tab", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.click(trigger());
      fireEvent.keyDown(trigger(), { key: "Tab" });
      expect(listbox()).not.toBeInTheDocument();
    });

    it("closes on an outside mousedown or touch, not an inside one", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.click(trigger());
      fireEvent.mouseDown(trigger());
      expect(listbox()).toBeInTheDocument();

      fireEvent.mouseDown(document.body);
      expect(listbox()).not.toBeInTheDocument();

      fireEvent.click(trigger());
      fireEvent.touchStart(document.body);
      expect(listbox()).not.toBeInTheDocument();
    });
  });

  describe("keyboard", () => {
    it("opens with ArrowDown and ArrowUp", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      expect(listbox()).toBeInTheDocument();
      fireEvent.keyDown(trigger(), { key: "Escape" });
      fireEvent.keyDown(trigger(), { key: "ArrowUp" });
      expect(listbox()).toBeInTheDocument();
    });

    it("navigates with arrows and selects with Enter", () => {
      const onChange = vi.fn();
      render(<LoginIdDropdown options={OPTIONS} onChange={onChange} />);
      fireEvent.keyDown(trigger(), { key: "Enter" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-0"),
      );
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.keyDown(trigger(), { key: "Enter" });
      expect(onChange).toHaveBeenCalledWith("login_id_email");
      expect(listbox()).not.toBeInTheDocument();
    });

    it("wraps around at both ends", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.keyDown(trigger(), { key: "ArrowUp" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-3"),
      );
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-0"),
      );
    });

    it("jumps with Home and End while open", () => {
      render(<LoginIdDropdown options={OPTIONS} value="login_id_mobile" />);
      fireEvent.keyDown(trigger(), { key: "End" });
      expect(listbox()).not.toBeInTheDocument();

      fireEvent.click(trigger());
      fireEvent.keyDown(trigger(), { key: "End" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-3"),
      );
      fireEvent.keyDown(trigger(), { key: "Home" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-0"),
      );
    });

    it("leaves Space to the native click: one toggle, then picks the active row", () => {
      const onChange = vi.fn();
      render(<LoginIdDropdown options={OPTIONS} onChange={onChange} />);
      fireEvent.keyDown(trigger(), { key: " " });
      expect(listbox()).not.toBeInTheDocument();

      // A keyboard/assistive activation has detail 0.
      fireEvent.click(trigger(), { detail: 0 });
      expect(listbox()).toBeInTheDocument();
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.click(trigger(), { detail: 0 });
      expect(onChange).toHaveBeenCalledWith("login_id_mobile");
      expect(listbox()).not.toBeInTheDocument();
    });

    it("ignores other keys", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      fireEvent.keyDown(trigger(), { key: "a" });
      expect(listbox()).not.toBeInTheDocument();
    });
  });

  describe("disabled options", () => {
    const WITH_DISABLED: DropdownOption[] = [
      { label: "A", value: "a", disabled: true },
      { label: "B", value: "b" },
      { label: "C", value: "c", disabled: true },
      { label: "D", value: "d" },
    ];

    it("cannot be clicked and is marked disabled", () => {
      const onChange = vi.fn();
      render(<LoginIdDropdown options={WITH_DISABLED} onChange={onChange} />);
      fireEvent.click(trigger());
      const a = screen.getByRole("option", { name: "A" });
      expect(a).toHaveAttribute("aria-disabled", "true");
      expect(a).toHaveClass("login-id-dropdown__option--disabled");
      fireEvent.click(a);
      expect(onChange).not.toHaveBeenCalled();
      expect(listbox()).toBeInTheDocument();
    });

    it("are skipped when opening, navigating and hovering", () => {
      const onChange = vi.fn();
      render(<LoginIdDropdown options={WITH_DISABLED} onChange={onChange} />);
      fireEvent.click(trigger());
      // Opens on the first enabled option.
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-1"),
      );
      fireEvent.mouseEnter(screen.getByRole("option", { name: "C" }));
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-1"),
      );
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-3"),
      );
      fireEvent.keyDown(trigger(), { key: "Home" });
      fireEvent.keyDown(trigger(), { key: "Enter" });
      expect(onChange).toHaveBeenCalledWith("b");
    });

    it("End lands on the last enabled option", () => {
      render(
        <LoginIdDropdown
          options={[...WITH_DISABLED, { label: "E", value: "e", disabled: true }]}
        />,
      );
      fireEvent.click(trigger());
      fireEvent.keyDown(trigger(), { key: "End" });
      expect(trigger()).toHaveAttribute(
        "aria-activedescendant",
        expect.stringContaining("-option-3"),
      );
    });

    it("keeps the active row when every option is disabled", () => {
      const onChange = vi.fn();
      render(
        <LoginIdDropdown
          options={[
            { label: "A", value: "a", disabled: true },
            { label: "B", value: "b", disabled: true },
          ]}
          onChange={onChange}
        />,
      );
      fireEvent.click(trigger());
      expect(trigger()).not.toHaveAttribute("aria-activedescendant");
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.keyDown(trigger(), { key: "Enter" });
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe("disabled control", () => {
    it("does not open by click or keyboard", () => {
      render(<LoginIdDropdown options={OPTIONS} disabled />);
      expect(trigger()).toBeDisabled();
      fireEvent.click(trigger());
      fireEvent.keyDown(trigger(), { key: "ArrowDown" });
      fireEvent.keyDown(trigger(), { key: "Enter" });
      expect(listbox()).not.toBeInTheDocument();
    });
  });

  describe("error and helper text", () => {
    it("shows the error as an alert and marks the control invalid", () => {
      const { container } = render(
        <LoginIdDropdown
          id="sel"
          options={OPTIONS}
          label="Login Id"
          error="Required"
          helperText="ignored while there is an error"
        />,
      );
      const alert = screen.getByRole("alert");
      expect(alert).toHaveTextContent("Required");
      expect(alert).toHaveAttribute("id", "sel-helper-text");
      expect(trigger()).toHaveAttribute("aria-invalid", "true");
      expect(trigger()).toHaveAttribute("aria-describedby", "sel-helper-text");
      expect(trigger()).toHaveClass("login-id-dropdown__trigger--error");
      expect(trigger()).toHaveAttribute("data-error", "true");
      expect(
        container.querySelector(".login-id-dropdown__label--error"),
      ).not.toBeNull();
    });

    it("shows helper text without alert or invalid state", () => {
      render(
        <LoginIdDropdown id="sel" options={OPTIONS} helperText="Pick a type" />,
      );
      expect(screen.getByText("Pick a type")).not.toHaveAttribute("role");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(trigger()).not.toHaveAttribute("aria-invalid");
      expect(trigger()).toHaveAttribute("aria-describedby", "sel-helper-text");
    });

    it("has no description when there is neither", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      expect(trigger()).not.toHaveAttribute("aria-describedby");
    });
  });

  describe("blur", () => {
    it("reports blur and closes once focus leaves the control", () => {
      const onBlur = vi.fn();
      render(
        <>
          <LoginIdDropdown options={OPTIONS} onBlur={onBlur} />
          <button>other</button>
        </>,
      );
      fireEvent.click(trigger());
      fireEvent.blur(trigger(), { relatedTarget: screen.getByText("other") });
      expect(onBlur).toHaveBeenCalledTimes(1);
      expect(listbox()).not.toBeInTheDocument();
    });

    it("ignores focus moving inside the control", () => {
      const onBlur = vi.fn();
      render(<LoginIdDropdown options={OPTIONS} onBlur={onBlur} />);
      fireEvent.click(trigger());
      fireEvent.blur(trigger(), {
        relatedTarget: screen.getByRole("listbox"),
      });
      expect(onBlur).not.toHaveBeenCalled();
      expect(listbox()).toBeInTheDocument();
    });

    it("works without an onBlur handler", () => {
      render(<LoginIdDropdown options={OPTIONS} />);
      expect(() => fireEvent.blur(trigger())).not.toThrow();
    });
  });
});
