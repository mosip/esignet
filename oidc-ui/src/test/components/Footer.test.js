import { render, screen, waitFor } from "@testing-library/react";
import Footer from "../../components/Footer";
import { useTranslation } from "react-i18next";
import configService from "../../services/configService";

// Mock i18n
jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));

// Mock configService
jest.mock("../../services/configService", () => jest.fn());

describe("Footer Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useTranslation.mockReturnValue({
      t: (key) => key,
    });
  });

  test("does not render when config is not yet loaded", () => {
    configService.mockResolvedValue({ footer: true });
    const { container } = render(<Footer />);
    expect(container.firstChild).toBeNull(); // nothing yet rendered
  });

  test("does not render when config.footer is false", async () => {
    configService.mockResolvedValue({ footer: false });
    render(<Footer />);
    await waitFor(() => {
      expect(screen.queryByText("powered_by")).not.toBeInTheDocument();
    });
  });

  test("renders footer when config.footer is true", async () => {
    configService.mockResolvedValue({ footer: true });

    render(<Footer />);

    await waitFor(() => {
      expect(screen.getByText("powered_by")).toBeInTheDocument();
      expect(screen.getByAltText("logo_alt")).toBeInTheDocument();
      expect(
        screen.getByRole("contentinfo", { hidden: true }) // <footer> tag
      ).toBeInTheDocument();
    });
  });

  test("handles configService throwing an error", async () => {
    const errorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
    configService.mockRejectedValue(new Error("Fetch error"));

    render(<Footer />);
    await waitFor(() =>
      expect(errorSpy).toHaveBeenCalledWith(
        "Error fetching footer config:",
        expect.any(Error)
      )
    );

    errorSpy.mockRestore();
  });
});
