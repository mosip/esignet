// 🧩 Polyfills for Node.js environment
global.TextEncoder = require("util").TextEncoder;
global.TextDecoder = require("util").TextDecoder;

import React from "react";
import {
  render,
  screen,
  act,
  waitFor,
  fireEvent,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import L1Biometrics from "../../components/L1Biometrics";
import { init, propChange } from "secure-biometric-interface-integrator";
import langConfigService from "../../services/langConfigService";
import redirectOnError from "../../helpers/redirectOnError";

// ✅ Mock i18n
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: {
      language: "en",
      on: jest.fn(),
      off: jest.fn(),
      changeLanguage: jest.fn(),
    },
  }),
}));

// ✅ Mock secure biometric
jest.mock("secure-biometric-interface-integrator", () => ({
  init: jest.fn(),
  propChange: jest.fn(),
}));

// ✅ Mock subcomponents
jest.mock(
  "../../components/InputWithImage",
  () =>
    ({ handleChange, blurChange, isInvalid, value, ...props }) =>
      (
        <input
          data-testid={`input-${props.id}`}
          onChange={handleChange}
          onBlur={blurChange}
          value={value || ""}
          className={isInvalid ? "invalid" : ""}
        />
      )
);
jest.mock(
  "../../components/InputWithPrefix",
  () =>
    ({
      currentLoginID,
      countryCode,
      selectedCountry,
      individualId,
      isBtnDisabled,
    }) =>
      (
        <div data-testid="input-with-prefix">
          <select
            data-testid="prefix-select"
            onChange={(e) => countryCode(e.target.value)}
          >
            {currentLoginID.prefixes.map((p) => (
              <option key={p.label} value={p.label}>
                {p.label}
              </option>
            ))}
          </select>
          <input
            data-testid="prefix-input"
            onChange={(e) => individualId(e.target.value)}
            onBlur={() => isBtnDisabled(false)}
          />
        </div>
      )
);
jest.mock(
  "../../common/ErrorBanner",
  () =>
    ({ showBanner, errorCode, onCloseHandle }) =>
      showBanner ? <div data-testid="error-banner">{errorCode}</div> : null
);
jest.mock("../../common/LoadingIndicator", () => ({ message }) => (
  <div data-testid="loading-indicator">{message}</div>
));
jest.mock("react-google-recaptcha", () => {
  const React = require("react");
  return React.forwardRef(({ onChange }, ref) => (
    <div data-testid="recaptcha">
      <button
        data-testid="captcha-button"
        onClick={() => onChange("captcha-token")}
      >
        Complete CAPTCHA
      </button>
    </div>
  ));
});

// ✅ Mock LoginIDOptions
jest.mock("../../components/LoginIDOptions", () => {
  const React = require("react");
  return function MockLoginIDOptions({ currentLoginID }) {
    React.useEffect(() => {
      if (currentLoginID) {
        currentLoginID({
          id: "mock-id",
          input_label: "Label",
          input_placeholder: "Placeholder",
          prefixes: [],
        });
      }
    }, []);
    return <div data-testid="login-id-options">LoginIDOptions</div>;
  };
});

// ✅ Mock services
jest.mock("../../services/langConfigService", () => ({
  getEnLocaleConfiguration: jest.fn().mockResolvedValue({
    errors: { biometrics: {}, otp: {} },
  }),
}));
jest.mock("../../helpers/redirectOnError", () => jest.fn());

const mockAuthService = {
  post_AuthenticateUser: jest
    .fn()
    .mockResolvedValue({ response: {}, errors: [] }),
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
          prefixes: [],
        },
      ],
      captchaEnableComponents: "bio",
      captchaSiteKey: "test-site-key",
      authTxnIdLength: "10",
      sbiEnv: "test",
      sbiCAPTURETimeoutInSeconds: "30",
      sbiIrisBioSubtypes: "both",
      sbiFingerBioSubtypes: "all",
      sbiFaceCaptureCount: "1",
      sbiFaceCaptureScore: "80",
      sbiFingerCaptureCount: "2",
      sbiFingerCaptureScore: "80",
      sbiIrisCaptureCount: "2",
      sbiIrisCaptureScore: "80",
      sbiPortRange: "8000-9000",
      sbiDISCTimeoutInSeconds: "10",
      sbiDINFOTimeoutInSeconds: "5",
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

const defaultProps = {
  param: mockParam,
  authService: mockAuthService,
  openIDConnectService: mockOpenIDConnectService,
  backButtonDiv: <div>Back</div>,
  secondaryHeading: "secondary_heading",
};

beforeEach(() => {
  jest.clearAllMocks();
  document.getElementById = jest
    .fn()
    .mockReturnValue(document.createElement("div"));
});

afterEach(() => {
  jest.restoreAllMocks();
});

// Test 1: Renders successfully without crashing
test("renders L1Biometrics component successfully without crashing", async () => {
  await act(async () => {
    render(
      <MemoryRouter>
        <L1Biometrics {...defaultProps} />
      </MemoryRouter>
    );
  });

  await screen.findByText("Back");
  await screen.findByText("LoginIDOptions");

  expect(screen.getByText("Back")).toBeInTheDocument();
  expect(screen.getByText("LoginIDOptions")).toBeInTheDocument();
});

// Test 2: Loads language configuration on mount
test("loads language configuration on component mount", async () => {
  await act(async () => {
    render(
      <MemoryRouter>
        <L1Biometrics {...defaultProps} />
      </MemoryRouter>
    );
  });

  await waitFor(() => {
    expect(langConfigService.getEnLocaleConfiguration).toHaveBeenCalled();
  });
});

// Test 3: Handles language configuration failure
test("handles language configuration failure with fallback", async () => {
  langConfigService.getEnLocaleConfiguration.mockRejectedValueOnce(
    new Error("Failed")
  );
  jest.spyOn(console, "error").mockImplementation(() => {});

  await act(async () => {
    render(
      <MemoryRouter>
        <L1Biometrics {...defaultProps} />
      </MemoryRouter>
    );
  });

  await waitFor(() => {
    expect(console.error).toHaveBeenCalledWith(
      "Failed to load lang config",
      expect.any(Error)
    );
    expect(langConfigService.getEnLocaleConfiguration).toHaveBeenCalled();
  });
});

// Test 4: Initializes biometric interface on mount
test("initializes secure biometric interface on first render", async () => {
  await act(async () => {
    render(
      <MemoryRouter>
        <L1Biometrics {...defaultProps} />
      </MemoryRouter>
    );
  });

  await waitFor(() => {
    expect(init).toHaveBeenCalledWith(
      expect.objectContaining({
        transactionId: expect.any(String),
        sbiEnv: expect.any(Object),
        langCode: "en",
        disable: true,
      })
    );
  });
});

// Test 5: Handles input change and validation
test("handles input change and updates validation state", async () => {
  const mockLoginID = {
    id: "mock-id",
    input_label: "Label",
    input_placeholder: "Placeholder",
    prefixes: [],
    maxLength: "10",
    regex: "^[0-9]+$",
  };
  mockOpenIDConnectService.getEsignetConfiguration.mockImplementation((key) => {
    const config = {
      loginIdOptions: [mockLoginID],
      captchaEnableComponents: "bio",
      captchaSiteKey: "test-site-key",
      authTxnIdLength: "10",
    };
    return config[key] || "";
  });

  await act(async () => {
    render(
      <MemoryRouter>
        <L1Biometrics {...defaultProps} />
      </MemoryRouter>
    );
  });

  const input = screen.getByTestId("input-sbi_mock-id");
  await act(async () => {
    fireEvent.change(input, { target: { value: "1234567890" } });
  });

  expect(input).toHaveValue("1234567890");
  expect(input).not.toHaveClass("invalid");
});
