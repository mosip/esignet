import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import SignInOptions from "../../components/SignInOptions";
import { useTranslation } from "react-i18next";

jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));

jest.mock("../../services/walletService", () => ({
  getAllAuthFactors: jest.fn(),
}));

import { getAllAuthFactors } from "../../services/walletService";

// Simulate global fetch (used in fetchSvg)
global.fetch = jest.fn();

describe("SignInOptions Component", () => {
  const walletLogoUrl = "wallet-logo.svg";

  const mockOAuthDetails = {
    authFactors: [
      { id: "otp", value: "otp", label: "otp_label", icon: "otp.svg" },
      { id: "pin", value: "pin", label: "pin_label", icon: "pin.svg" },
    ],
  };

  const mockConfig = [{ walletLogoUrl }];

  const getMockOpenIDConnectService = () => ({
    getEsignetConfiguration: jest.fn(() => mockConfig),
    getOAuthDetails: jest.fn(() => mockOAuthDetails),
  });

  const handleSignInOptionClick = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    useTranslation.mockReturnValue({
      t: (key, options) => (options?.option ? options.option : key),
    });
  });

  test("renders with provided icons and handles clicks", async () => {
    getAllAuthFactors.mockReturnValue(mockOAuthDetails.authFactors);

    render(
      <SignInOptions
        openIDConnectService={getMockOpenIDConnectService()}
        handleSignInOptionClick={handleSignInOptionClick}
        icons={{
          otp: "<svg>OTP</svg>",
          pin: "<svg>PIN</svg>",
        }}
        authLabel="auth_method"
      />
    );

    expect(
      await screen.findByText("preferred_mode_to_continue")
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("OTP")).toBeInTheDocument();
      expect(screen.getByText("PIN")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("OTP"));
    expect(handleSignInOptionClick).toHaveBeenCalledWith(
      "otp",
      expect.anything(),
      "auth_method"
    );
  });

  test("handles Enter key press as click", async () => {
    getAllAuthFactors.mockReturnValue(mockOAuthDetails.authFactors);

    render(
      <SignInOptions
        openIDConnectService={getMockOpenIDConnectService()}
        handleSignInOptionClick={handleSignInOptionClick}
        icons={{
          otp: "<svg>OTP</svg>",
          pin: "<svg>PIN</svg>",
        }}
        authLabel="auth_method"
      />
    );

    const pinOption = await screen.findByText("PIN");
    fireEvent.keyDown(pinOption, { key: "Enter", code: "Enter" });

    expect(handleSignInOptionClick).toHaveBeenCalledWith(
      "pin",
      expect.anything(),
      "auth_method"
    );
  });

  test("auto-clicks sign-in if only one option", async () => {
    getAllAuthFactors.mockReturnValue([
      { id: "otp", value: "otp", label: "otp_label", icon: "otp.svg" },
    ]);

    render(
      <SignInOptions
        openIDConnectService={getMockOpenIDConnectService()}
        handleSignInOptionClick={handleSignInOptionClick}
        authLabel="auth_method"
      />
    );

    await waitFor(() => {
      expect(handleSignInOptionClick).toHaveBeenCalledWith(
        "otp",
        null,
        "auth_method"
      );
    });
  });

  test("displays more options and toggles hide on click", async () => {
    const extendedFactors = Array.from({ length: 5 }, (_, i) => ({
      id: `id${i}`,
      value: `value${i}`,
      label: `label${i}`,
      icon: `icon${i}.svg`,
    }));

    getAllAuthFactors.mockReturnValue(extendedFactors);

    render(
      <SignInOptions
        openIDConnectService={getMockOpenIDConnectService()}
        handleSignInOptionClick={handleSignInOptionClick}
        icons={extendedFactors.reduce((acc, val) => {
          acc[val.id] = `<svg>${val.id}</svg>`;
          return acc;
        }, {})}
        authLabel="auth_method"
      />
    );

    const moreBtn = await screen.findByText("more_ways_to_sign_in");
    expect(moreBtn).toBeInTheDocument();
    fireEvent.click(moreBtn); // click to hide remaining options
  });

  test("gracefully handles fetchSvg failure", async () => {
    getAllAuthFactors.mockReturnValue(mockOAuthDetails.authFactors);

    fetch.mockResolvedValueOnce({ ok: false });

    render(
      <SignInOptions
        openIDConnectService={getMockOpenIDConnectService()}
        handleSignInOptionClick={handleSignInOptionClick}
        authLabel="auth_method"
      />
    );

    await screen.findByText("preferred_mode_to_continue");
    await waitFor(() => {
      expect(screen.queryByText("OTP")).not.toBeInTheDocument(); // fallback empty
    });
  });

  test("handles empty auth factors", async () => {
    getAllAuthFactors.mockReturnValue([]);

    render(
      <SignInOptions
        openIDConnectService={getMockOpenIDConnectService()}
        handleSignInOptionClick={handleSignInOptionClick}
        authLabel="auth_method"
      />
    );

    expect(
      await screen.findByText("preferred_mode_to_continue")
    ).toBeInTheDocument();
    expect(handleSignInOptionClick).not.toHaveBeenCalled();
  });
});
