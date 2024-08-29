import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ClaimDetails from "../../../components/ClaimDetails";
import openIDConnectService from "../../../services/openIDConnectService";
import authService from "../../../services/authService";
import { useTranslation } from "react-i18next";
import { configurationKeys } from "../../../constants/clientConstants";
import { mockOAuthDetails, mockAuthService, mockResponse } from "./mocks";

// Mock services
jest.mock("../../../services/openIDConnectService");
jest.mock("../../../services/authService");
jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));

// Mock window.location.replace
const originalLocation = window.location;

beforeAll(() => {
  delete window.location;
  window.location = {
    href: "http://localhost",
    replace: jest.fn(),
  };
});

afterAll(() => {
  window.location = originalLocation;
});

// Reset mocks and data before each test
beforeEach(() => {
  openIDConnectService.mockImplementation(() => ({
    getOAuthDetails: jest.fn(() => mockOAuthDetails),
    getTransactionId: jest.fn(() => "transactionId"),
    getEsignetConfiguration: jest.fn((key) => {
      if (key === configurationKeys.eKYCStepsConfig) {
        return "eKYCStepsURL";
      }
      return null;
    }),
    getRedirectUri: jest.fn(() => "mockRedirectUri"),
  }));
  authService.mockImplementation(() => mockAuthService);
  useTranslation.mockImplementation((namespace, { keyPrefix }) => ({
    t: (key) =>
      ({
        "consentDetails.proceed": "Proceed",
        "consentDetails.cancel": "Cancel",
        "consentDetails.header": "Header",
        "consentDetails.essential_claims": "Essential Claims",
        "consentDetails.popup_header": "Attention!",
      }[`${keyPrefix}.${key}`] || key),
    i18n: { language: "en" },
  }));
  jest.spyOn(window, "atob").mockImplementation(() => JSON.stringify({}));
});

afterEach(() => {
  jest.clearAllMocks();
});

// Tests
test("displays loading state initially", () => {
  render(<ClaimDetails />);
  expect(screen.getByText("Loading...")).toBeInTheDocument();
});

test("renders ClaimDetails component and display Header", async () => {
  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);

  render(<ClaimDetails />);

  await waitFor(() => {
    expect(screen.getByText("Header")).toBeInTheDocument();
  });
});

test("displays Essential Claims", async () => {
  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);

  render(<ClaimDetails />);

  await waitFor(() => {
    expect(screen.getByText("Essential Claims")).toBeInTheDocument();
  });
});

test("clicking Proceed button triggers window.location.replace with mockRedirectUri", async () => {
  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);

  render(<ClaimDetails />);

  await waitFor(() => {
    expect(screen.getByText("Header")).toBeInTheDocument();
  });

  const proceedButton = screen.getByRole("button", { name: /Proceed/i });
  fireEvent.click(proceedButton);

  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalledWith(
      expect.stringContaining("mockRedirectUri")
    );
  });
});

test("clicking cancel button triggers 'Attention!' popup", async () => {
  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);

  render(<ClaimDetails />);

  await waitFor(() => {
    expect(screen.getByText("Header")).toBeInTheDocument();
  });

  const cancelButton = screen.getByText("Cancel");
  fireEvent.click(cancelButton);

  await waitFor(() => {
    expect(screen.getByText("Attention!")).toBeInTheDocument();
  });
});
