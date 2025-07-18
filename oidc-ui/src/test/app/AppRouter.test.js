import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { BrowserRouter, MemoryRouter, Route, Routes } from "react-router-dom";
import { AppRouter } from "../../../src/app/AppRouter";
import * as configServiceModule from "../../../src/services/configService";
import * as apiService from "../../../src/services/api.service";

jest.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key) => key }),
}));

jest.mock("../../../src/services/api.service", () => ({
  setupResponseInterceptor: jest.fn(),
}));

jest.mock("../../../src/common/LoadingIndicator", () => () => (
  <div data-testid="loading">Loading...</div>
));

jest.mock("../../../src/pages", () => ({
  LoginPage: () => <div>LoginPage</div>,
  AuthorizePage: () => <div>AuthorizePage</div>,
  ConsentPage: () => <div>ConsentPage</div>,
  EsignetDetailsPage: () => <div>EsignetDetailsPage</div>,
  SomethingWrongPage: () => <div>SomethingWrongPage</div>,
  PageNotFoundPage: () => <div>PageNotFoundPage</div>,
}));

jest.mock("../../../src/components/ClaimDetails", () => () => (
  <div>ClaimDetails</div>
));
jest.mock("../../../src/pages/NetworkError", () => () => (
  <div>NetworkError</div>
));
jest.mock("react-detect-offline", () => ({
  Detector: ({ render }) => render({ online: true }),
}));

jest.mock("../../../src/helpers/utils", () => ({
  getPollingConfig: () => ({
    url: "/health",
    interval: 10000,
    timeout: 5000,
    enabled: true,
  }),
}));

describe("AppRouter", () => {
  const mockConfig = {
    background_logo: true,
  };

  beforeEach(() => {
    jest.spyOn(configServiceModule, "default").mockResolvedValue(mockConfig);
  });

  it("renders loading indicator initially", async () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <AppRouter />
      </MemoryRouter>
    );
    expect(screen.getByTestId("loading")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByTestId("loading")).not.toBeInTheDocument()
    );
  });

  it("renders LoginPage for /login route", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AppRouter />
      </MemoryRouter>
    );
    await screen.findByText("LoginPage");
  });

  it("renders AuthorizePage for /authorize route", async () => {
    render(
      <MemoryRouter initialEntries={["/authorize"]}>
        <AppRouter />
      </MemoryRouter>
    );
    await screen.findByText("AuthorizePage");
  });

  it("renders ConsentPage for /consent route", async () => {
    render(
      <MemoryRouter initialEntries={["/consent"]}>
        <AppRouter />
      </MemoryRouter>
    );
    await screen.findByText("ConsentPage");
  });

  it("renders NetworkError for /network-error route", async () => {
    render(
      <MemoryRouter initialEntries={["/network-error"]}>
        <AppRouter />
      </MemoryRouter>
    );
    await screen.findByText("NetworkError");
  });
});
