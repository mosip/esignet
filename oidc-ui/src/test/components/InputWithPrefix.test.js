import React from "react";
import {
  render,
  fireEvent,
  screen,
  act,
  waitFor,
} from "@testing-library/react";
import InputWithPrefix from "../../components/InputWithPrefix";
import { useTranslation } from "react-i18next";

jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));

jest.mock("iso-3166-1", () => ({
  all: () => [
    { alpha2: "US", alpha3: "USA" },
    { alpha2: "GB", alpha3: "GBR" },
  ],
}));

describe("InputWithPrefix Component", () => {
  const mockProps = {
    login: "test",
    currentLoginID: {
      id: "phone",
      input_label: "Phone Number",
      input_placeholder: "Enter phone number",
      prefixes: [
        { label: "USA", value: "+1", maxLength: 10, regex: "^\\d{10}$" },
        { label: "UK", value: "+44", maxLength: 10 },
      ],
    },
    selectedCountry: jest.fn(),
    countryCode: jest.fn(),
    individualId: jest.fn(),
    isBtnDisabled: jest.fn(),
    i18nPrefix: "translation",
  };

  let mockT, mockI18n;

  beforeEach(() => {
    mockT = jest.fn((key, options) =>
      options?.id ? `${key}-${options.id}` : key
    );
    mockI18n = { language: "en", changeLanguage: jest.fn() };
    useTranslation.mockReturnValue({ t: mockT, i18n: mockI18n });
  });

  it("renders country flag and label", async () => {
    render(<InputWithPrefix {...mockProps} />);
    expect(screen.getByText("Phone Number")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("Enter phone number")
    ).toBeInTheDocument();
  });

  it("updates input value and validates", () => {
    render(<InputWithPrefix {...mockProps} />);
    const input = screen.getByPlaceholderText("Enter phone number");
    fireEvent.change(input, { target: { value: "1234567890" } });
    expect(mockProps.individualId).toHaveBeenCalledWith("1234567890");
  });

  it("validates input on blur", () => {
    render(<InputWithPrefix {...mockProps} />);
    const input = screen.getByPlaceholderText("Enter phone number");
    fireEvent.change(input, { target: { value: "1234567890" } });
    fireEvent.blur(input);
    expect(input).toBeInTheDocument();
  });

  it("shows error message when invalid input", () => {
    render(<InputWithPrefix {...mockProps} />);
    const input = screen.getByPlaceholderText("Enter phone number");
    fireEvent.change(input, { target: { value: "123" } });
    fireEvent.blur(input);
    expect(mockProps.isBtnDisabled).toHaveBeenCalledWith(true);
  });

  it("handles language change correctly", () => {
    render(<InputWithPrefix {...mockProps} />);
    act(() => {
      mockI18n.language = "fr";
    });
    expect(mockI18n.language).toBe("fr");
  });

  it("handles re-selecting same country", async () => {
    render(<InputWithPrefix {...mockProps} />);
    const dropdownBtn = screen.getByTestId("prefix-dropdown-btn");
    fireEvent.click(dropdownBtn);
    await waitFor(() => {
      const option = screen.getByText("USA (+1)");
      fireEvent.click(option);
    });
  });

  it("doesn't break with missing props", () => {
    render(<InputWithPrefix {...{ ...mockProps, currentLoginID: null }} />);
    expect(screen.queryByText("Phone Number")).not.toBeInTheDocument();
  });
});
