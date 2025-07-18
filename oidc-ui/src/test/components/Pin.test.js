import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Pin from "../../components/Pin";
import openIDConnectService from "../../services/openIDConnectService";
import authService from "../../services/authService";
import { useTranslation } from "react-i18next";
import { configurationKeys } from "../../constants/clientConstants";

// ---------- Mocks ----------
jest.mock("../../services/openIDConnectService");
jest.mock("../../services/authService");
jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));
jest.mock("../../helpers/redirectOnError", () => jest.fn());
jest.mock("../../services/langConfigService", () => ({
  getEnLocaleConfiguration: jest.fn(() =>
    Promise.resolve({ errors: { pin: { invalid_pin: "Invalid PIN" } } })
  ),
}));

// ---------- GLOBAL CONFIG ----------
const mockOAuthDetails = {
  clientName: { "@none": "Test Client" },
  logoUrl: "/logo.png",
};

const mockOpenIDConnectService = {
  getOAuthDetails: jest.fn(() => mockOAuthDetails),
  getTransactionId: jest.fn(() => "txn-123"),
  getNonce: jest.fn(() => "mockNonce"),
  getState: jest.fn(() => "mockState"),
  getEsignetConfiguration: jest.fn((key) => {
    const config = {
      [configurationKeys.loginIdOptions]: [
        {
          id: "email",
          input_label: "Email",
          input_placeholder: "Enter your email",
          prefixes: [],
        },
      ],
      [configurationKeys.captchaEnableComponents]: "",
      [configurationKeys.captchaSiteKey]: "test-key",
    };
    return config[key];
  }),
};

const mockAuthService = {
  post_AuthenticateUser: jest.fn(() =>
    Promise.resolve({
      response: { consentAction: "consent_given" },
      errors: null,
    })
  ),
  buildRedirectParams: jest.fn(() => "?next=claim-details"),
};

// ---------- Component Props ----------
const mockFields = [
  {
    id: "pin",
    labelText: "PIN",
    type: "password",
    isRequired: true,
    placeholder: "Enter PIN",
    errorCode: "invalid_pin",
    maxLength: "6",
    regex: "^[0-9]{6}$",
  },
];

const mockBackButtonDiv = <div>Back</div>;

// ---------- window.location & atob ----------
const originalLocation = window.location;

beforeAll(() => {
  delete window.location;
  window.location = {
    href: "http://localhost?state=mockState&nonce=mockNonce&ui_locales=en#eyJjbGllbnROYW1lIjp7IkBub25lIjoiVGVzdCBDbGllbnQifSwibG9nb1VybCI6Ii9sb2dvLnBuZyJ9",
    search: "?state=mockState&nonce=mockNonce&ui_locales=en",
    hash: "#eyJjbGllbnROYW1lIjp7IkBub25lIjoiVGVzdCBDbGllbnQifSwibG9nb1VybCI6Ii9sb2dvLnBuZyJ9",
    replace: jest.fn(),
  };

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

beforeEach(() => {
  openIDConnectService.mockImplementation(() => mockOpenIDConnectService);
  authService.mockImplementation(() => mockAuthService);

  useTranslation.mockImplementation(() => ({
    t: (key) => {
      const map = {
        "input.placeholder.pin": "Enter PIN",
        "input.label.pin": "PIN",
        login: "login",
        invalid_pin: "Invalid PIN",
        remember_me: "Remember me",
        secondary_heading: "secondary_heading",
      };
      return map[key] || key;
    },
    i18n: { language: "en", on: jest.fn() },
  }));
});

afterEach(() => {
  jest.clearAllMocks();
});

// ---------- SHARED RENDER ----------
const renderComponent = () =>
  render(
    <MemoryRouter>
      <Pin
        param={mockFields}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        backButtonDiv={mockBackButtonDiv}
        secondaryHeading="secondary_heading"
      />
    </MemoryRouter>
  );

// ---------- TEST CASES ----------
test("Renders Pin component with required fields", async () => {
  renderComponent();
  expect(await screen.findByText("Back")).toBeInTheDocument();
  expect(await screen.findByText("secondary_heading")).toBeInTheDocument();
  expect(await screen.findByText("login")).toBeInTheDocument();
});