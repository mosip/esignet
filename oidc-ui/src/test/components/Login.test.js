import { render, screen, fireEvent } from "@testing-library/react";
import Login from "../../components/Login";

// Mock Input, FormAction, FormExtra components
jest.mock("../../components/Input", () => (props) => (
  <input
    data-testid={`input-${props.id}`}
    id={props.id}
    placeholder={props.placeholder}
    value={props.value}
    onChange={props.handleChange}
  />
));
jest.mock("../../components/FormAction", () => (props) => (
  <button onClick={props.handleSubmit} id={props.id}>
    {props.text}
  </button>
));
jest.mock("../../components/FormExtra", () => () => (
  <div data-testid="form-extra">FormExtra Content</div>
));

// Mock loginFields
jest.mock("../../constants/formFields", () => ({
  loginFields: [
    {
      id: "email",
      labelText: "Email",
      labelFor: "email",
      name: "email",
      type: "email",
      isRequired: true,
      placeholder: "Enter your email",
    },
    {
      id: "password",
      labelText: "Password",
      labelFor: "password",
      name: "password",
      type: "password",
      isRequired: true,
      placeholder: "Enter your password",
    },
  ],
}));

describe("Login Component", () => {
  test("renders input fields based on loginFields", () => {
    render(<Login />);

    expect(screen.getByPlaceholderText("Enter your email")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("Enter your password")
    ).toBeInTheDocument();
  });

  test("renders FormExtra and FormAction", () => {
    render(<Login />);
    expect(screen.getByTestId("form-extra")).toBeInTheDocument();
    expect(screen.getByText("Login")).toBeInTheDocument();
  });

  test("updates state on input change", () => {
    render(<Login />);

    const emailInput = screen.getByPlaceholderText("Enter your email");
    fireEvent.change(emailInput, { target: { value: "user@example.com" } });
    expect(emailInput.value).toBe("user@example.com");
  });

  test("handles form submit", () => {
    render(<Login />);

    const loginButton = screen.getByText("Login");
    fireEvent.click(loginButton);

    // Since authenticateUser is empty, we just make sure no crash occurs
    expect(screen.getByText("Login")).toBeInTheDocument();
  });
});
