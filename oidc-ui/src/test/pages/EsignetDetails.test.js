import React from "react";
import { render, screen } from "@testing-library/react";
import EsignetDetailsPage from "../../pages/EsignetDetails";

// Mock the EsignetDetails component
jest.mock("../../components/EsignetDetails", () => () => (
  <div data-testid="EsignetDetailsComponent">Mocked EsignetDetails</div>
));

describe("EsignetDetailsPage", () => {
  it("renders the EsignetDetails component", () => {
    render(<EsignetDetailsPage />);
    const component = screen.getByTestId("EsignetDetailsComponent");

    expect(component).toBeInTheDocument();
    expect(component).toHaveTextContent("Mocked EsignetDetails");
  });
});
