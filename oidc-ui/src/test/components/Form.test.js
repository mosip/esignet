import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import Form from "../../components/Form";

// 🧪 Mock dependencies
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: { language: "eng", on: jest.fn() },
  }),
}));

jest.mock("react-router-dom", () => ({
  useNavigate: () => jest.fn(),
}));

jest.mock("../../common/LoadingIndicator", () => () => <div>Loading...</div>);

jest.mock("../../common/ErrorBanner", () => ({ showBanner, errorCode }) => (
  <div>{showBanner && errorCode}</div>
));

jest.mock("../../helpers/redirectOnError", () => jest.fn());

jest.mock("../../services/langConfigService", () => ({
  getEnLocaleConfiguration: jest.fn().mockResolvedValue({
    errors: { kbi: {} },
  }),
}));

// 🧩 JsonFormBuilder mock
const mockRender = jest.fn();
const mockUpdateLanguage = jest.fn();
const mockGetFormData = jest.fn(() => ({
  individualId: "12345",
  fullName: "John Doe",
}));

jest.mock("@anushase/json-form-builder", () => ({
  __esModule: true,
  default: jest.fn().mockImplementation(() => ({
    render: mockRender,
    updateLanguage: mockUpdateLanguage,
    getFormData: mockGetFormData,
  })),
}));

const mockAuthService = {
  post_AuthenticateUser: jest
    .fn()
    .mockResolvedValue({ response: {}, errors: null }),
  buildRedirectParams: jest.fn(() => "?mockParams"),
};

const mockOpenIDConnectService = {
  getEsignetConfiguration: jest.fn((key) => {
    if (key === "authFactorKnowledgeFieldDetails") {
      return {
        schema: [
          {
            id: "individualId",
            controlType: "textbox",
            label: { eng: "Policy Number" },
            placeholder: { eng: "Enter Policy Number" },
            required: true,
            alignmentGroup: "groupA",
          },
          {
            id: "fullName",
            controlType: "textbox",
            label: { eng: "Full Name" },
            placeholder: { eng: "Enter Full Name" },
            required: true,
            alignmentGroup: "groupB",
          },
        ],
        errors: {
          required: { eng: "This field is required" },
        },
        language: {
          mandatory: ["eng"],
          optional: ["khm"],
          langCodeMap: { eng: "en", khm: "km" },
        },
      };
    }
    if (key === "captchaSiteKey") return "test-site-key";
    if (key === "captchaEnableComponents") return "kbi";
    if (key === "authFactorKnowledgeIndividualIdField") return "individualId";
    return undefined;
  }),
  getTransactionId: jest.fn(() => "txn123"),
  getNonce: jest.fn(() => "nonce123"),
  getState: jest.fn(() => "state123"),
  getOAuthDetails: jest.fn(() => ({})),
};

// 🌍 Set environment before tests
beforeAll(() => {
  global.window._env_ = { DEFAULT_LANG: "en" };
});

describe("Form Component", () => {
  it("renders form without errors", async () => {
    render(
      <Form
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        backButtonDiv={<div>Back</div>}
        secondaryHeading="secondaryHeading"
      />
    );

    expect(await screen.findByText("secondaryHeading")).toBeInTheDocument();
    expect(document.getElementById("form-container")).toBeInTheDocument();
  });

  it("renders back button and heading", () => {
    render(
      <Form
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        backButtonDiv={<div>Back</div>}
        secondaryHeading="Test Heading"
      />
    );

    expect(screen.getByText("Back")).toBeInTheDocument();
    expect(screen.getByText("Test Heading")).toBeInTheDocument();
  });
});
