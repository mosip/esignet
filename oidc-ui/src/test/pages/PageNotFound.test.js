import React from "react";
import { render, screen } from "@testing-library/react";
import PageNotFoundPage from "../../pages/PageNotFound";

// ✅ Mock i18next
jest.mock("react-i18next", () => ({
  useTranslation: (_, { keyPrefix }) => ({
    t: (key) => `${keyPrefix}.${key}`,
  }),
}));

describe("PageNotFoundPage", () => {
  it("renders image and default translation text", () => {
    render(<PageNotFoundPage />);

    const image = screen.getByAltText("page_not_found");
    expect(image).toBeInTheDocument();
    expect(image).toHaveAttribute("src", "images/under_construction.svg");

    expect(screen.getByText("errors.page_not_exist")).toBeInTheDocument();
  });

  it("respects custom i18nKeyPrefix prop", () => {
    render(<PageNotFoundPage i18nKeyPrefix="custom" />);
    expect(screen.getByText("custom.page_not_exist")).toBeInTheDocument();
  });
});
