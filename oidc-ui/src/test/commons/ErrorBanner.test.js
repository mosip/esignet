import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import ErrorBanner from "../../common/ErrorBanner";
import { useTranslation } from "react-i18next";

// Mock useTranslation
jest.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key) => `translated_${key}`,
  }),
}));

describe("ErrorBanner", () => {
  const onCloseHandle = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it("should not render when showBanner is false", () => {
    render(
      <ErrorBanner
        showBanner={false}
        errorCode="error.code"
        onCloseHandle={onCloseHandle}
      />
    );
    expect(screen.queryByTestId("error-banner")).toBeNull();
    expect(screen.queryByTestId("error-banner-message")).toBeNull();
  });

  it("should render with correct message and custom class", () => {
    render(
      <ErrorBanner
        showBanner={true}
        errorCode="error.code"
        onCloseHandle={onCloseHandle}
        customClass="custom-class"
      />
    );
    const banner = screen.getByText("translated_error.code");
    expect(banner).toBeInTheDocument();
    expect(screen.getByRole("button")).toBeInTheDocument();
    expect(screen.getByRole("button").parentElement).toHaveClass(
      "custom-class"
    );
  });

  it("should call onCloseHandle when close button is clicked", () => {
    render(
      <ErrorBanner
        showBanner={true}
        errorCode="error.code"
        onCloseHandle={onCloseHandle}
      />
    );
    fireEvent.click(screen.getByRole("button"));
    expect(onCloseHandle).toHaveBeenCalled();
  });

  it("should call onCloseHandle when close button is pressed (keydown)", () => {
    render(
      <ErrorBanner
        showBanner={true}
        errorCode="error.code"
        onCloseHandle={onCloseHandle}
      />
    );
    fireEvent.keyDown(screen.getByRole("button"));
    expect(onCloseHandle).toHaveBeenCalled();
  });

  it("should call onCloseHandle after bannerCloseTimer seconds", () => {
    render(
      <ErrorBanner
        showBanner={true}
        errorCode="error.code"
        onCloseHandle={onCloseHandle}
        bannerCloseTimer={2}
      />
    );
    act(() => {
      jest.advanceTimersByTime(2000);
    });
    expect(onCloseHandle).toHaveBeenCalled();
  });
});
