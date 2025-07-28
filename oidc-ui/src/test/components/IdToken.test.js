// --- Mocks must be defined before imports ---
const mockNavigate = jest.fn();

const mockAuthService = {
  post_AuthenticateUser: jest.fn(() =>
    Promise.resolve({
      response: { consentAction: "mockedConsent" },
      errors: null,
    })
  ),
  buildRedirectParams: jest.fn(() => "?mocked=params"),
  getAuthorizeQueryParam: jest.fn(() =>
    btoa("id_token_hint=mockedIdTokenHint")
  ),
};

const mockOpenIDConnectService = {
  getTransactionId: jest.fn(() => "mockedTransactionId"),
  getOAuthDetails: jest.fn(() => ({})),
  getNonce: jest.fn(() => "mockedNonce"),
  getState: jest.fn(() => "mockedState"),
};

jest.mock("../../common/LoadingIndicator", () => () => <div>Loading...</div>);

jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key,
  }),
}));

jest.mock("../../helpers/redirectOnError", () => jest.fn());

jest.mock("../../helpers/utils", () => ({
  getOauthDetailsHash: jest.fn(() => Promise.resolve("mockedHash")),
  base64UrlDecode: jest.fn(() => JSON.stringify({ sub: "mocked-uuid" })),
}));

jest.mock("buffer", () => ({
  Buffer: {
    from: jest.fn(() => ({
      toString: jest.fn(() => "id_token_hint=mockedIdTokenHint"),
    })),
  },
}));

jest.mock("react-router-dom", () => {
  const actual = jest.requireActual("react-router-dom");
  const mockValidJwt = [
    "header",
    btoa(JSON.stringify({ sub: "mocked-uuid" })),
    "signature",
  ].join(".");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useSearchParams: () => [
      {
        get: jest.fn((param) => {
          if (param === "id_token_hint") return mockValidJwt;
          return null;
        }),
      },
      jest.fn(),
    ],
  };
});

// --- Imports ---
import React from "react";
import { render, waitFor } from "@testing-library/react";
import IdToken from "../../components/IdToken";
import { MemoryRouter } from "react-router-dom";

describe("IdToken Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders without crashing", async () => {
    render(
      <MemoryRouter>
        <IdToken
          authService={mockAuthService}
          openIDConnectService={mockOpenIDConnectService}
        />
      </MemoryRouter>
    );
  });

  it("shows loading indicator", () => {
    const { getByText } = render(
      <MemoryRouter>
        <IdToken
          authService={mockAuthService}
          openIDConnectService={mockOpenIDConnectService}
        />
      </MemoryRouter>
    );
    expect(getByText("Loading...")).toBeInTheDocument();
  });
});
