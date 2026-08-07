import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import React from "react";

// State captured by reference so tests can mutate it before rendering.
const state = {
  languages: [] as { code: string; displayName: string }[],
  currentLanguage: "en",
  onLanguageChange: vi.fn(),
  isLoading: false,
};

vi.mock("@thunderid/react", () => ({
  I18nContext: React.createContext<object | null>(null),
  LanguageSwitcher: ({
    children,
  }: {
    children: (props: {
      languages: { code: string; displayName: string }[];
      currentLanguage: string;
      onLanguageChange: (code: string) => void;
      isLoading: boolean;
    }) => React.ReactNode;
  }) =>
    children({
      languages: state.languages,
      currentLanguage: state.currentLanguage,
      onLanguageChange: state.onLanguageChange,
      isLoading: state.isLoading,
    }),
}));

// Import AFTER vi.mock so we get the mocked module's context reference.
import { I18nContext } from "@thunderid/react";
import NavHeader from "../../components/NavHeader";

// Provide a non-null context value so SafeLanguageSwitcher renders.
const I18nProvider = I18nContext.Provider as React.ComponentType<{
  value: object | null;
  children: React.ReactNode;
}>;

function renderWithI18n() {
  return render(
    <I18nProvider value={{ t: (k: string) => k, currentLanguage: "en" }}>
      <NavHeader />
    </I18nProvider>,
  );
}

describe("NavHeader — structure", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state.languages = [];
    state.currentLanguage = "en";
    state.isLoading = false;
  });

  it("renders the navbar element", () => {
    render(<NavHeader />);
    expect(screen.getByRole("navigation")).toBeDefined();
  });

  it("renders the brand logo", () => {
    render(<NavHeader />);
    expect(screen.getByAltText("brand_logo")).toBeDefined();
  });

  it("has the #navbar-header id", () => {
    render(<NavHeader />);
    expect(document.getElementById("navbar-header")).not.toBeNull();
  });

  it("does not render a language listbox outside I18nContext", () => {
    // No I18nContext provider → SafeLanguageSwitcher returns null
    render(<NavHeader />);
    expect(screen.queryByRole("listbox")).toBeNull();
  });
});

describe("LanguageDropdown — single or no language", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state.isLoading = false;
  });

  it("renders nothing when there is only one language", () => {
    state.languages = [{ code: "en", displayName: "English" }];
    renderWithI18n();
    expect(screen.queryByRole("listbox")).toBeNull();
    // No toggle button either
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("renders nothing when there are zero languages", () => {
    state.languages = [];
    renderWithI18n();
    expect(screen.queryByRole("button")).toBeNull();
  });
});

describe("LanguageDropdown — multiple languages", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state.languages = [
      { code: "en", displayName: "English" },
      { code: "fr", displayName: "French" },
    ];
    state.currentLanguage = "en";
    state.isLoading = false;
  });

  it("shows the current language name in the toggle button", () => {
    renderWithI18n();
    expect(screen.getByText("English")).toBeDefined();
  });

  it("does not show the listbox before the button is clicked", () => {
    renderWithI18n();
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("opens the listbox when the toggle button is clicked", () => {
    renderWithI18n();
    fireEvent.click(screen.getByRole("button", { name: /English/i }));
    expect(screen.getByRole("listbox")).toBeDefined();
  });

  it("closes the listbox when the toggle button is clicked again", () => {
    renderWithI18n();
    const btn = screen.getByRole("button", { name: /English/i });
    fireEvent.click(btn);
    fireEvent.click(btn);
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("renders one option per language", () => {
    renderWithI18n();
    fireEvent.click(screen.getByRole("button", { name: /English/i }));
    expect(screen.getAllByRole("option")).toHaveLength(2);
  });

  it("calls onLanguageChange with the correct code when an option is clicked", () => {
    renderWithI18n();
    fireEvent.click(screen.getByRole("button", { name: /English/i }));
    fireEvent.click(screen.getByRole("option", { name: "French" }));
    expect(state.onLanguageChange).toHaveBeenCalledWith("fr");
  });

  it("closes the listbox after a language is selected", () => {
    renderWithI18n();
    fireEvent.click(screen.getByRole("button", { name: /English/i }));
    fireEvent.click(screen.getByRole("option", { name: "French" }));
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("disables the toggle button while isLoading is true", () => {
    state.isLoading = true;
    renderWithI18n();
    expect(screen.getByRole("button", { name: /English/i })).toBeDisabled();
  });

  it("marks the current language option as aria-selected", () => {
    renderWithI18n();
    fireEvent.click(screen.getByRole("button", { name: /English/i }));
    const englishOption = screen.getByRole("option", { name: "English" });
    expect(englishOption.getAttribute("aria-selected")).toBe("true");
    const frenchOption = screen.getByRole("option", { name: "French" });
    expect(frenchOption.getAttribute("aria-selected")).toBe("false");
  });

  it("closes the listbox when clicking outside the dropdown", () => {
    renderWithI18n();
    fireEvent.click(screen.getByRole("button", { name: /English/i }));
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("does not close the listbox when clicking inside the dropdown", () => {
    renderWithI18n();
    const btn = screen.getByRole("button", { name: /English/i });
    fireEvent.click(btn);
    // Click on an option label inside the dropdown — should still be open
    fireEvent.mouseDown(screen.getByRole("option", { name: "English" }));
    expect(screen.getByRole("listbox")).toBeDefined();
  });
});
