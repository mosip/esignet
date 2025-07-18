import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import Password from "../../components/Password";
import { MemoryRouter } from "react-router-dom";
import { configurationKeys } from "../../constants/clientConstants";

// ---------- Mocks ----------

// 🌐 i18n
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: {
      language: "en",
      on: jest.fn(),
    },
  }),
}));

// 🔒 reCAPTCHA
jest.mock("react-google-recaptcha", () => () => (
  <div data-testid="recaptcha" />
));

// 🚫 redirectOnError
jest.mock("../../helpers/redirectOnError", () => jest.fn());

// 🌐 langConfigService
jest.mock("../../services/langConfigService", () => ({
  getEnLocaleConfiguration: jest.fn(() =>
    Promise.resolve({ errors: { password: {} } })
  ),
}));

// 📍 useNavigate
jest.mock("react-router-dom", () => ({
  ...jest.requireActual("react-router-dom"),
  useNavigate: () => jest.fn(),
}));

// ---------- Globals ----------

// Mock openIDConnectService instance
const mockOpenIDConnectService = {
  getEsignetConfiguration: jest.fn((key) => {
    const configs = {
      [configurationKeys.loginIdOptions]: [
        {
          id: "email",
          input_label: "Email",
          input_placeholder: "Enter your email",
          prefixes: [],
        },
      ],
      [configurationKeys.forgotPswdConfig]: {
        forgotPswd: true,
        forgotPswdURL: "https://example.com/forgot-password",
      },
      [configurationKeys.additionalConfig]: {
        forgotPswdLinkRequired: true,
      },
      [configurationKeys.captchaEnableComponents]: "pwd",
      [configurationKeys.captchaSiteKey]: "test-site-key",
      [configurationKeys.bannerCloseTimer]: 3000,
    };
    return configs[key];
  }),
  getTransactionId: () => "txn-abc",
  getNonce: () => "nonce-xyz",
  getState: () => "state-123",
  getOAuthDetails: () => ({}),
  getAuthorizeQueryParam: () => "someQuery",
};

// Mock authService instance
const mockAuthService = {
  post_AuthenticateUser: jest.fn(() =>
    Promise.resolve({
      response: { consentAction: "consent_given" },
      errors: null,
    })
  ),
  buildRedirectParams: jest.fn(() => "?next=claim-details"),
  getAuthorizeQueryParam: jest.fn(() => "authParam"),
};

// Mock field input
const mockFields = [
  {
    id: "email",
    labelText: "Email",
    placeholder: "Email",
    type: "text",
    isRequired: true,
  },
  {
    id: "password",
    labelText: "Password",
    placeholder: "Password",
    type: "password",
    isRequired: true,
  },
];

// Mock back button
const mockBackButtonDiv = <div>Back</div>;

// ---------- window.location & atob ----------

const originalLocation = window.location;

beforeAll(() => {
  delete window.location;
  window.location = {
    ...originalLocation,
    href: "http://localhost?state=mockState&nonce=mockNonce&ui_locales=en#eyJjbGllbnROYW1lIjoiVGVzdCBDbGllbnQiLCJsb2dvVXJsIjoiL2xvZ28ucG5nIiwiY29uZmlncyI6eyJsb2dpbi1pZC5vcHRpb25zIjpbeyJpZCI6ImVtYWlsIiwiaW5wdXRfbGFiZWwiOiJFbWFpbCIsImlucHV0X3BsYWNlaG9sZGVyIjoiRW50ZXIgeW91ciBlbWFpbCIsInByZWZpeGVzIjpbXX1dfX0=",
    hash: "#eyJjbGllbnROYW1lIjoiVGVzdCBDbGllbnQiLCJsb2dvVXJsIjoiL2xvZ28ucG5nIiwiY29uZmlncyI6eyJsb2dpbi1pZC5vcHRpb25zIjpbeyJpZCI6ImVtYWlsIiwiaW5wdXRfbGFiZWwiOiJFbWFpbCIsImlucHV0X3BsYWNlaG9sZGVyIjoiRW50ZXIgeW91ciBlbWFpbCIsInByZWZpeGVzIjpbXX1dfX0=",
  };

  // Provide base64-decoded JSON config with .configs.login-id.options
  jest.spyOn(window, "atob").mockImplementation(() =>
    JSON.stringify({
      clientName: { "@none": "Test Client" },
      logoUrl: "/logo.png",
      configs: {
        "login-id.options": [
          {
            id: "email",
            input_label: "Email",
            input_placeholder: "Enter your email",
            prefixes: [],
          },
        ],
      },
    })
  );
});

afterAll(() => {
  window.location = originalLocation;
  jest.restoreAllMocks();
});

afterEach(() => {
  jest.clearAllMocks();
});

// ---------- TEST ----------

test("should render the Password component successfully", async () => {
  render(
    <MemoryRouter>
      <Password
        param={mockFields}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        backButtonDiv={mockBackButtonDiv}
        secondaryHeading="secondary_heading"
      />
    </MemoryRouter>
  );

  // Wait for initial render
  expect(await screen.findByText("Back")).toBeInTheDocument();
  expect(await screen.findByText("secondary_heading")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "login" })).toBeInTheDocument();
});
