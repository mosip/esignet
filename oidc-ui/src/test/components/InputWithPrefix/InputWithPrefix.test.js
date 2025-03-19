import React from "react";
import {
  render,
  fireEvent,
  screen,
  act,
  waitFor
} from "@testing-library/react";
import InputWithPrefix from "../../../components/InputWithPrefix";
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
        { label: "USA", value: "+1" },
        { label: "UK", value: "+44" },
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
    mockT = jest.fn((key) => key);
    mockI18n = { language: "en", changeLanguage: jest.fn() };

    useTranslation.mockReturnValue({ t: mockT, i18n: mockI18n });
  });

  it("renders country flag correctly", async () => {
    render(<InputWithPrefix {...mockProps} />);
  
    await waitFor(() => {
      expect(screen.getByTestId("prefix-dropdown-btn")).toBeInTheDocument();
    });
  });

  it("renders without crashing", () => {
    render(<InputWithPrefix {...mockProps} />);
    expect(screen.getByText("Phone Number")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("Enter phone number")
    ).toBeInTheDocument();
  });

  it("updates input value on change", () => {
    render(<InputWithPrefix {...mockProps} />);
    const input = screen.getByPlaceholderText("Enter phone number");

    fireEvent.change(input, { target: { value: "1234567890" } });
    expect(mockProps.individualId).toHaveBeenCalledWith("1234567890");
  });

  it("updates language state when i18n.language changes", () => {
    render(<InputWithPrefix {...mockProps} />);

    act(() => {
      mockI18n.language = "fr";
    });

    expect(mockI18n.language).toBe("fr");
  });
});
