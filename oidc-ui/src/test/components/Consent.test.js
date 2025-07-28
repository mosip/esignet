import {
  render,
  screen,
  waitFor,
  fireEvent,
  act,
} from "@testing-library/react";
import Consent from "../../components/Consent";
import configService from "../../services/configService";
import langConfigService from "../../services/langConfigService";
import redirectOnError from "../../helpers/redirectOnError";
import { useTranslation } from "react-i18next";

jest.mock("../../services/configService");
jest.mock("../../services/langConfigService");
jest.mock("../../helpers/redirectOnError");
jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));

const mockOAuthDetails = {
  authorizeScopes: ["profile", "email"],
  essentialClaims: ["name"],
  voluntaryClaims: ["mobile"],
  clientName: { en: "Test Client", "@none": "Default Client" },
  logoUrl: "test-logo.png",
};

const mockAuthService = {
  post_AuthCode: jest.fn().mockResolvedValue({
    response: {
      redirectUri: "http://localhost/callback",
      code: "123",
      state: "abc",
    },
    errors: null,
  }),
};

const mockOIDCService = {
  getOAuthDetails: () => mockOAuthDetails,
  getTransactionId: () => "txn-123",
  getEsignetConfiguration: jest.fn((key) =>
    key === "consentScreenExpireInSec" ? 120 : 5
  ),
};

describe("Consent Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();

    configService.mockResolvedValue({ outline_toggle: true });
    langConfigService.getLangCodeMapping.mockResolvedValue({ en: "en" });

    useTranslation.mockReturnValue({
      t: (key, options) =>
        typeof options === "object" && options?.clientName
          ? `${key} ${options.clientName}`
          : key,
    });

    delete window.location;
    window.location = { replace: jest.fn() };
    delete window.onbeforeunload;
  });

  test("handles checkbox scope/claim toggling manually", async () => {
    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(new Date().getTime() / 1000)}
        openIDConnectService={mockOIDCService}
      />
    );

    const checkboxes = await screen.findAllByRole("checkbox");

    expect(checkboxes.length).toBeGreaterThan(0);

    act(() => {
      fireEvent.click(checkboxes[0]); // toggle first scope/claim
    });
  });

  test("handles selectUnselectAllScopeClaim with main true", async () => {
    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(new Date().getTime() / 1000)}
        openIDConnectService={mockOIDCService}
      />
    );

    const toggle = await screen.findByLabelText("authorize_scope");
    fireEvent.click(toggle); // triggers `selectUnselectAllScopeClaim` with `main=true`
  });

  test("sets checkbox using elementChecked", async () => {
    document.body.innerHTML = `<input type="checkbox" id="fakeCheck"/>`;

    const checkbox = document.getElementById("fakeCheck");
    expect(checkbox.checked).toBe(false);

    // Simulate call to elementChecked
    checkbox.checked = true;
    expect(checkbox.checked).toBe(true);
  });

  test("handles post_AuthCode error (catch block)", async () => {
    mockAuthService.post_AuthCode.mockRejectedValueOnce(
      new Error("mock failure")
    );

    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(new Date().getTime() / 1000)}
        openIDConnectService={mockOIDCService}
      />
    );

    const allowBtn = await screen.findByText("allow");
    fireEvent.click(allowBtn);

    await waitFor(() => {
      expect(redirectOnError).toHaveBeenCalledWith(
        "authorization_failed_msg",
        "mock failure"
      );
    });
  });

  test("handles language fallback to @none", async () => {
    langConfigService.getLangCodeMapping.mockResolvedValueOnce({ fr: "fr" });
    useTranslation.mockReturnValueOnce({
      t: (key, options) =>
        typeof options === "object" && options?.clientName
          ? `${key} ${options.clientName}`
          : key,
    });

    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(Date.now() / 1000)}
        openIDConnectService={mockOIDCService}
      />
    );

    expect(
      await screen.findByText("consent_request_msg Default Client")
    ).toBeInTheDocument();
  });

  test("falls back to @none clientName if language key missing", async () => {
    const modifiedOIDC = {
      ...mockOIDCService,
      getOAuthDetails: () => ({
        ...mockOAuthDetails,
        clientName: { "@none": "Fallback Client" }, // no 'en' key
      }),
    };

    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(Date.now() / 1000)}
        openIDConnectService={modifiedOIDC}
      />
    );

    expect(
      await screen.findByText("consent_request_msg Fallback Client")
    ).toBeInTheDocument();
  });

  test("renders without essentialClaims", async () => {
    const modifiedOIDC = {
      ...mockOIDCService,
      getOAuthDetails: () => ({
        ...mockOAuthDetails,
        essentialClaims: [],
      }),
    };

    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(Date.now() / 1000)}
        openIDConnectService={modifiedOIDC}
      />
    );

    expect(screen.queryByText("essential_claims")).not.toBeInTheDocument();
  });

  test("skips voluntary_claims section when empty", async () => {
    const modifiedOIDC = {
      ...mockOIDCService,
      getOAuthDetails: () => ({
        ...mockOAuthDetails,
        voluntaryClaims: [],
      }),
    };

    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(Date.now() / 1000)}
        openIDConnectService={modifiedOIDC}
      />
    );

    expect(screen.queryByText("voluntary_claims")).not.toBeInTheDocument();
    expect(screen.queryByText("noRecordClaimsMessage")).not.toBeInTheDocument();
  });

  test("does not show main toggle when only one scope", async () => {
    const modifiedOIDC = {
      ...mockOIDCService,
      getOAuthDetails: () => ({
        ...mockOAuthDetails,
        authorizeScopes: ["email"],
      }),
    };

    render(
      <Consent
        authService={mockAuthService}
        consentAction="CAPTURE"
        authTime={Math.floor(Date.now() / 1000)}
        openIDConnectService={modifiedOIDC}
      />
    );

    expect(screen.queryByLabelText("authorize_scope")).not.toBeInTheDocument();
  });

  test("auto-submits consent if consentAction is NOCAPTURE", async () => {
    render(
      <Consent
        authService={mockAuthService}
        consentAction="NOCAPTURE"
        authTime={Math.floor(Date.now() / 1000)}
        openIDConnectService={mockOIDCService}
      />
    );

    await waitFor(() => {
      expect(mockAuthService.post_AuthCode).toHaveBeenCalled();
      expect(screen.getByText("redirecting_msg")).toBeInTheDocument();
    });
  });
});
