import { render, screen, fireEvent } from "@testing-library/react";
import FormExtra from "../../components/FormExtra";

describe("FormExtra Component", () => {
  test("renders checkbox with label", () => {
    render(<FormExtra />);
    const checkbox = screen.getByRole("checkbox", { name: "Remember me" });
    const label = screen.getByText("Remember me");

    expect(checkbox).toBeInTheDocument();
    expect(label).toHaveAttribute("for", "remember-me");

    // Simulate checking the box
    fireEvent.click(checkbox);
    expect(checkbox.checked).toBe(true);
  });

  test("renders forgot password button", () => {
    render(<FormExtra />);
    const forgotPasswordBtn = screen.getByRole("button", {
      name: "Forgot your password?",
    });

    expect(forgotPasswordBtn).toBeInTheDocument();
    expect(forgotPasswordBtn).toHaveAttribute("id", "forgot_password");
  });
});
