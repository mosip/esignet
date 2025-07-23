import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import ConsentPage from "../../pages/Consent";
import authService from "../../services/authService";
import openIDConnectService from "../../services/openIDConnectService";
import * as utils from "../../helpers/utils";

// Mock react-router-dom
jest.mock("react-router-dom", () => ({
  useLocation: jest.fn(),
  useSearchParams: jest.fn(),
}));

// Mock authService and openIDConnectService
jest.mock("../../services/authService");
jest.mock("../../services/openIDConnectService");

// Mock Consent and DefaultError components
jest.mock("../../components/Consent", () => () => (
  <div data-testid="ConsentComponent" />
));
jest.mock("../../components/DefaultError", () => ({ errorCode }) => (
  <div data-testid="DefaultErrorComponent">{errorCode}</div>
));

describe("ConsentPage", () => {
  let originalLocation;

  beforeEach(() => {
    originalLocation = window.location;

    delete window.location; // Necessary to override
    window.location = {
      ...originalLocation,
      replace: jest.fn(),
    };

    window.onbeforeunload = jest.fn();
    jest.clearAllMocks();
  });

  afterAll(() => {
    window.location = originalLocation; // Restore after all tests
  });

  const setupCommonMocks = ({
    urlInfo = null,
    hash = "",
    searchParams = {},
    errorCode = undefined,
  } = {}) => {
    // Mock useLocation
    const hashEncoded = Buffer.from(hash).toString("base64");
    require("react-router-dom").useLocation.mockReturnValue({
      hash: hashEncoded,
    });

    // Mock useSearchParams
    require("react-router-dom").useSearchParams.mockReturnValue([
      {
        get: (key) => {
          if (key === "nonce") return searchParams.nonce || "mockNonce";
          if (key === "state") return searchParams.state || "mockState";
          if (key === "consentAction")
            return searchParams.consentAction || "allow";
          if (key === "authenticationTime")
            return searchParams.authenticationTime || "1620000000";
          if (key === "key") return searchParams.key || "mockKey";
          if (key === "error") return errorCode;
          return null;
        },
      },
      jest.fn(),
    ]);

    // Mock localStorage.getItem
    Object.defineProperty(window, "localStorage", {
      value: {
        getItem: jest.fn().mockImplementation((k) => {
          if (k === "mockKey") return urlInfo;
          return null;
        }),
      },
      writable: true,
    });
  };

  it("renders DefaultError when urlInfo is null and decodeOAuth fails", () => {
    setupCommonMocks({ urlInfo: null, hash: "not-json" });

    render(<ConsentPage />);
    expect(screen.getByTestId("DefaultErrorComponent")).toBeInTheDocument();
    expect(screen.getByText("unauthorized_access")).toBeInTheDocument();
  });

  it("renders Consent component and calls resume when no errors", async () => {
    const testOAuthHash = { transactionId: "abc123" };
    const encodedHash = Buffer.from(JSON.stringify(testOAuthHash)).toString(
      "base64"
    );
    const mockUrlInfo = `some=query#${encodedHash}`;

    setupCommonMocks({
      urlInfo: mockUrlInfo,
      hash: JSON.stringify({ transactionId: "abc123" }),
      searchParams: {
        nonce: "mockNonce",
        state: "mockState",
        key: "mockKey",
        consentAction: "allow",
        authenticationTime: "1620000000",
      },
    });

    utils.decodeHash = jest.fn().mockReturnValue(JSON.stringify(testOAuthHash));
    utils.getOauthDetailsHash = jest.fn().mockResolvedValue("hashed-value");

    const redirectUri = "https://mock-redirect.com/callback";

    openIDConnectService.mockImplementation(() => ({
      getRedirectUri: () => redirectUri,
    }));

    authService.mockImplementation(() => ({
      resume: jest.fn().mockResolvedValue({ errors: [] }),
    }));

    render(<ConsentPage />);

    await waitFor(() => {
      expect(screen.getByTestId("ConsentComponent")).toBeInTheDocument();
    });

    expect(window.location.replace).toHaveBeenCalledWith(
      expect.stringContaining("authenticationTime=")
    );
  });
});
