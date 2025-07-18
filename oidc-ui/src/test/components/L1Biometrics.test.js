// L1Biometrics.test.js

// 🧩 Polyfills for Node.js environment
global.TextEncoder = require("util").TextEncoder;
global.TextDecoder = require("util").TextDecoder;

import React from "react";
import { render, screen, act, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import L1Biometrics from "../../components/L1Biometrics";

// ✅ Mock i18n
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: { language: "en", on: jest.fn(), off: jest.fn() },
  }),
}));

// ✅ Mock secure biometric
jest.mock("secure-biometric-interface-integrator", () => ({
  init: jest.fn(),
  propChange: jest.fn(),
}));

// ✅ Mock subcomponents
jest.mock("../../components/InputWithImage", () => () => (
  <div>InputWithImage</div>
));
jest.mock("../../components/InputWithPrefix", () => () => (
  <div>InputWithPrefix</div>
));
jest.mock("../../common/ErrorBanner", () => () => <div>ErrorBanner</div>);
jest.mock("../../common/LoadingIndicator", () => () => (
  <div>LoadingIndicator</div>
));
jest.mock("react-google-recaptcha", () => {
  const React = require("react");
  return React.forwardRef(() => <div data-testid="recaptcha">ReCAPTCHA</div>);
});

// ✅ Mock LoginIDOptions with useEffect
jest.mock("../../components/LoginIDOptions", () => {
  const React = require("react");
  return function MockLoginIDOptions(props) {
    React.useEffect(() => {
      if (props.currentLoginID) {
        props.currentLoginID({
          id: "mock-id",
          input_label: "Label",
          input_placeholder: "Placeholder",
          prefixes: [],
        });
      }
    }, []);
    return <div>LoginIDOptions</div>;
  };
});

// ✅ Mock langConfigService
jest.mock("../../services/langConfigService", () => ({
  getEnLocaleConfiguration: jest.fn().mockResolvedValue({
    errors: {
      biometrics: {},
      otp: {},
    },
  }),
}));

// ✅ Mock redirectOnError
jest.mock("../../helpers/redirectOnError", () => jest.fn());

const mockAuthService = {
  post_AuthenticateUser: jest.fn(),
  buildRedirectParams: jest.fn(() => "?mock=params"),
};

const mockOpenIDConnectService = {
  getTransactionId: jest.fn(() => "mocktxn123"),
  encodeBase64: jest.fn(() => "encodedBio"),
  getOAuthDetails: jest.fn(() => ({})),
  getNonce: jest.fn(() => "nonce123"),
  getState: jest.fn(() => "state123"),
  getEsignetConfiguration: jest.fn((key) => {
    const config = {
      loginIdOptions: [
        {
          id: "mock-id",
          input_label: "Label",
          input_placeholder: "Placeholder",
        },
      ],
      captchaEnableComponents: "bio",
      captchaSiteKey: "test-site-key",
    };
    return config[key] || "";
  }),
};

const mockParam = {
  inputFields: [
    {
      id: "mock-id",
      type: "text",
      isRequired: true,
    },
  ],
};

beforeEach(() => {
  document.getElementById = jest
    .fn()
    .mockReturnValue(document.createElement("div"));
});

afterEach(() => {
  jest.restoreAllMocks();
});

test("renders L1Biometrics component successfully without crashing", async () => {
  await act(async () => {
    render(
      <MemoryRouter>
        <L1Biometrics
          param={mockParam}
          authService={mockAuthService}
          openIDConnectService={mockOpenIDConnectService}
          backButtonDiv={<div>Back</div>}
          secondaryHeading="Secondary Heading"
        />
      </MemoryRouter>
    );
  });

  await screen.findByText("Back");
  await screen.findByText("LoginIDOptions");

  expect(screen.getByText("Back")).toBeInTheDocument();
  expect(screen.getByText("LoginIDOptions")).toBeInTheDocument();
});
