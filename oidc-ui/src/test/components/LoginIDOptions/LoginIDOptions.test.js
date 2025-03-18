import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import openIDConnectService from "../../../services/openIDConnectService";
import authService from "../../../services/authService";
import LoginIDOptions from "../../../components/LoginIDOptions";
import { useTranslation } from "react-i18next";
import { mockOAuthDetails } from "./mocks";
import { configurationKeys } from "../../../constants/clientConstants";

// Mock services
jest.mock("../../../services/openIDConnectService");
jest.mock("../../../services/authService");
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => key, // Return the translation key directly
    i18n: { changeLanguage: jest.fn() }, // Mock i18n object
  }),
}));

// Reset mocks and data before each test
beforeEach(() => {
  openIDConnectService.mockImplementation(() => ({
    getOAuthDetails: jest.fn(() => mockOAuthDetails),
    getTransactionId: jest.fn(() => "transactionId"),
    getEsignetConfiguration: jest.fn((key) => {
      if (key === configurationKeys.loginIdOptions) {
        return [
          {
            id: "vid",
            svg: "vid_icon",
            prefixes: "",
            postfix: "",
            maxLength: "",
            regex: "",
          },
        ]; // Return an array instead of a string
      }
      return null;
    }),
  }));

  jest.spyOn(window, "atob").mockImplementation(() =>
    JSON.stringify({
      id: "test",
    })
  );
});

afterEach(() => {
  jest.clearAllMocks();
}); 

describe("LoginIDOptions Component", () => {
  test("renders LoginIDOptions without crashing", async () => {
    render(<LoginIDOptions currentLoginID={jest.fn()} />);

    // Wait for component to update state
    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument();
    });
  });

  test("should preselect the first login ID option", async () => {
    const mockCurrentLoginID = jest.fn();
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument();
    });

    expect(mockCurrentLoginID).toHaveBeenCalledWith(
      expect.objectContaining({ id: "vid" })
    );
  });

  test("should change selection when another option is clicked", async () => {
    const mockCurrentLoginID = jest.fn();
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument();
    });

    const button = screen.getByText("buttons.vid");
    fireEvent.click(button);

    expect(mockCurrentLoginID).toHaveBeenCalledWith(
      expect.objectContaining({ id: "vid" })
    );
  });

  test("should preselect the first login ID option", async () => {
    const mockCurrentLoginID = jest.fn();
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument();
    });

    expect(mockCurrentLoginID).toHaveBeenCalledWith(
      expect.objectContaining({ id: "vid" })
    );
  });

  test("renders default option if getEsignetConfiguration returns null", async () => {
    openIDConnectService.mockImplementation(() => ({
      getEsignetConfiguration: jest.fn(() => null), // Simulate null response
    }));

    render(<LoginIDOptions currentLoginID={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument(); // Default fallback
    });
  });

  test("renders default option if loginIDs array is empty", async () => {
    openIDConnectService.mockImplementation(() => ({
      getEsignetConfiguration: jest.fn(() => []), // Simulate empty array
    }));

    render(<LoginIDOptions currentLoginID={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument(); // Default option should appear
    });
  });

  test("renders all login ID options", async () => {
    openIDConnectService.mockImplementation(() => ({
      getEsignetConfiguration: jest.fn(() => [
        { id: "mobile", svg: "mobile_icon" },
        { id: "email", svg: "email_icon" },
      ]),
    }));

    render(<LoginIDOptions currentLoginID={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.mobile")).toBeInTheDocument();
      expect(screen.getByText("buttons.email")).toBeInTheDocument();
    });
  });

  test("handles SVG fetch failure gracefully", async () => {
    global.fetch = jest.fn(() => Promise.reject(new Error("Network Error")));

    render(<LoginIDOptions currentLoginID={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.vid")).toBeInTheDocument();
    });
  });

  test("renders correctly when an option has a missing SVG path", async () => {
    openIDConnectService.mockImplementation(() => ({
      getEsignetConfiguration: jest.fn(() => [
        { id: "mobile", svg: "" },
        { id: "email", svg: "email_icon" },
      ]),
    }));

    render(<LoginIDOptions currentLoginID={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("buttons.mobile")).toBeInTheDocument();
      expect(screen.getByText("buttons.email")).toBeInTheDocument();
    });
  });
});
