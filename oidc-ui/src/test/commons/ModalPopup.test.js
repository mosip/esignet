import React from "react";
import { render } from "@testing-library/react";
import ModalPopup from "../../common/ModalPopup";

describe("ModalPopup Component", () => {
  it("renders without crashing", () => {
    render(<ModalPopup />);
  });

  it("renders alert icon when provided", () => {
    const alertIcon = "path/to/alertIcon.png";
    const { getByTestId } = render(<ModalPopup alertIcon={alertIcon} />);
    const alertIconElement = getByTestId("alert-icon");
    expect(alertIconElement).toBeInTheDocument();
    expect(alertIconElement).toHaveAttribute("src", alertIcon);
  });

  it("renders header when provided", () => {
    const { getByText } = render(<ModalPopup header="Test Header" />);
    const headerElement = getByText("Test Header");
    expect(headerElement).toBeInTheDocument();
  });

  it("renders body when provided", () => {
    const { getByText } = render(<ModalPopup body="Test Body" />);
    const bodyElement = getByText("Test Body");
    expect(bodyElement).toBeInTheDocument();
  });

  it("renders footer when provided", () => {
    const { getByText } = render(<ModalPopup footer="Test Footer" />);
    const footerElement = getByText("Test Footer");
    expect(footerElement).toBeInTheDocument();
  });
});
