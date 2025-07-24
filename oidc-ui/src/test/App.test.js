import React from "react";
import { render, screen, waitFor, act } from "@testing-library/react";
import App from "../App";
import langConfigService from "../services/langConfigService";
import { useTranslation } from "react-i18next";

// Mock components
jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));
jest.mock("../components/NavHeader", () => () => <div>NavHeader</div>);
jest.mock("../components/Footer", () => () => <div>Footer</div>);
jest.mock("../common/LoadingIndicator", () => ({ message }) => (
  <div>{message}</div>
));
jest.mock("../app/AppRouter", () => ({
  AppRouter: () => <div>AppRouter</div>,
}));
jest.mock("@tanstack/react-query", () => ({
  QueryClient: function () {},
  QueryClientProvider: ({ children }) => <div>{children}</div>,
}));
jest.mock("../services/api.service", () => ({
  HttpError: class HttpError extends Error {
    constructor(code) {
      super();
      this.code = code;
    }
  },
}));

const mockLangConfig = {
  languages_2Letters: { en: "English", ar: "Arabic" },
  langCodeMapping: { eng: "en", ara: "ar" },
  rtlLanguages: ["ar"],
};

describe("App", () => {
  let changeLanguage;
  let on;

  beforeEach(() => {
    jest.clearAllMocks();

    changeLanguage = jest.fn();
    on = jest.fn();

    useTranslation.mockReturnValue({
      i18n: {
        language: "en",
        changeLanguage,
        on,
      },
    });

    jest
      .spyOn(langConfigService, "getLocaleConfiguration")
      .mockResolvedValue(mockLangConfig);
    window._env_ = { DEFAULT_LANG: "en" };
  });

  it("renders loading indicator while loading", async () => {
    let resolveLang;
    langConfigService.getLocaleConfiguration.mockImplementation(
      () => new Promise((resolve) => (resolveLang = resolve))
    );

    render(<App />);
    expect(screen.getByText("loading_msg")).toBeInTheDocument();

    resolveLang(mockLangConfig);
    await waitFor(() => {
      expect(screen.getByText("NavHeader")).toBeInTheDocument();
    });
  });

  it("renders main app after loading", async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getByText("NavHeader")).toBeInTheDocument();
      expect(screen.getByText("AppRouter")).toBeInTheDocument();
      expect(screen.getByText("Footer")).toBeInTheDocument();
    });
  });

  it("sets direction to rtl for rtl language", async () => {
    useTranslation.mockReturnValue({
      i18n: {
        language: "ar",
        changeLanguage,
        on,
      },
    });
    render(<App />);
    await waitFor(() => {
      expect(
        screen.getByText("NavHeader").closest("div[dir='rtl']")
      ).toBeInTheDocument();
    });
  });

  it("sets direction to ltr for non-rtl language", async () => {
    useTranslation.mockReturnValue({
      i18n: {
        language: "fr",
        changeLanguage,
        on,
      },
    });
    render(<App />);
    await waitFor(() => {
      expect(
        screen.getByText("NavHeader").closest("div[dir='ltr']")
      ).toBeInTheDocument();
    });
  });

  it("calls changeLanguage for 2-letter match", async () => {
    delete window.location;
    window.location = { search: "?ui_locales=ar" };

    render(<App />);
    await waitFor(() => {
      expect(changeLanguage).toHaveBeenCalledWith("ar");
    });
  });

  it("calls changeLanguage for 3-letter match", async () => {
    delete window.location;
    window.location = { search: "?ui_locales=ara" };

    render(<App />);
    await waitFor(() => {
      expect(changeLanguage).toHaveBeenCalledWith("ar");
    });
  });

  it("falls back to 2-letter defaultLang", async () => {
    delete window.location;
    window.location = { search: "?ui_locales=xyz" };
    window._env_ = { DEFAULT_LANG: "en" };

    render(<App />);
    await waitFor(() => {
      expect(changeLanguage).toHaveBeenCalledWith("en");
    });
  });

  it("falls back to 3-letter defaultLang mapped", async () => {
    delete window.location;
    window.location = { search: "?ui_locales=xyz" };
    window._env_ = { DEFAULT_LANG: "ara" };

    render(<App />);
    await waitFor(() => {
      expect(changeLanguage).toHaveBeenCalledWith("ar");
    });
  });

  it("does not call changeLanguage if no match in ui_locales or defaultLang", async () => {
    delete window.location;
    window.location = { search: "?ui_locales=zzz" };
    window._env_ = { DEFAULT_LANG: "yyy" };

    render(<App />);
    await waitFor(() => {
      expect(changeLanguage).not.toHaveBeenCalled();
    });
  });

  it("logs error when getLocaleConfiguration fails", async () => {
    const spy = jest.spyOn(console, "error").mockImplementation(() => {});
    langConfigService.getLocaleConfiguration.mockRejectedValue(
      new Error("fail")
    );

    render(<App />);
    await waitFor(() => {
      expect(spy).toHaveBeenCalledWith(
        "Failed to load rtl languages!",
        expect.any(Error)
      );
    });

    spy.mockRestore();
  });

  it("updates direction to rtl on languageChanged event", async () => {
    let languageChangedCallback;
    const onMock = jest.fn((event, cb) => {
      if (event === "languageChanged") languageChangedCallback = cb;
    });

    useTranslation.mockReturnValue({
      i18n: {
        language: "en",
        changeLanguage,
        on: onMock,
      },
    });

    render(<App />);
    await waitFor(() => {
      expect(screen.getByText("NavHeader")).toBeInTheDocument();
    });

    if (languageChangedCallback) {
      act(() => {
        languageChangedCallback("ar");
      });

      await waitFor(() => {
        expect(
          screen.getByText("NavHeader").closest("div[dir='rtl']")
        ).toBeInTheDocument();
      });
    }
  });

  it("updates direction to ltr for unknown language", async () => {
    let languageChangedCallback;
    const onMock = jest.fn((event, cb) => {
      if (event === "languageChanged") languageChangedCallback = cb;
    });

    useTranslation.mockReturnValue({
      i18n: {
        language: "en",
        changeLanguage,
        on: onMock,
      },
    });

    render(<App />);
    await waitFor(() => {
      expect(screen.getByText("NavHeader")).toBeInTheDocument();
    });

    if (languageChangedCallback) {
      act(() => {
        languageChangedCallback("xyz");
      });

      await waitFor(() => {
        expect(
          screen.getByText("NavHeader").closest("div[dir='ltr']")
        ).toBeInTheDocument();
      });
    }
  });
});
