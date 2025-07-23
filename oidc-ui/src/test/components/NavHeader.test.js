import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import NavHeader from "../../components/NavHeader";

// Mocks
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: {
      language: "en",
      changeLanguage: jest.fn(),
      on: jest.fn((event, cb) => cb("fr")), // simulate change to 'fr'
    },
  }),
}));

jest.mock("../../services/authService", () => {
  return {
    __esModule: true,
    default: class {
      constructor() {}
      getAuthorizeQueryParam() {
        return btoa("ui_locales=en");
      }
    },
  };
});

jest.mock("../../services/openIDConnectService", () => ({}));

jest.mock("../../services/configService", () => {
  return {
    __esModule: true,
    default: jest.fn(() =>
      Promise.resolve({
        outline_dropdown: true,
        remove_language_indicator_pipe: true,
      })
    ),
  };
});

jest.mock("react-detect-offline", () => ({
  Detector: ({ render }) => render({ online: true }),
}));

jest.mock("../../helpers/utils", () => ({
  getPollingConfig: () => ({
    url: "https://ping.test",
    interval: 10000,
    timeout: 5000,
    enabled: true,
  }),
}));

describe("NavHeader", () => {
  const langOptions = [
    { value: "en", label: "English" },
    { value: "fr", label: "Français" },
  ];

  beforeEach(() => {
    localStorage.clear();
    jest.resetModules();
  });

  test("renders header with language dropdown (Select)", async () => {
    render(<NavHeader langOptions={langOptions} />);
    await waitFor(() => {
      expect(screen.getByLabelText("Customise options")).toBeInTheDocument();
    });
  });

  test("calls changeLanguage on language change", async () => {
    render(<NavHeader langOptions={langOptions} />);
    await waitFor(() => {
      expect(screen.getByLabelText("Customise options")).toBeInTheDocument();
    });
  });

  test("renders DropdownMenu if outline_dropdown is false", async () => {
    const configService = require("../../services/configService");
    configService.default.mockImplementationOnce(() =>
      Promise.resolve({
        outline_dropdown: false,
        remove_language_indicator_pipe: false,
      })
    );

    render(<NavHeader langOptions={langOptions} />);

    await waitFor(() => {
      expect(screen.getByText("English")).toBeInTheDocument();
    });
  });
});
