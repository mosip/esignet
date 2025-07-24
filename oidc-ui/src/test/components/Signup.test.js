import { render, screen, fireEvent } from "@testing-library/react";
import Signup from "../../components/Signup";
import { signupFields } from "../../constants/formFields";

// Mock Input and FormAction components
jest.mock(
  "../../components/Input",
  () =>
    ({
      handleChange,
      value,
      labelText,
      id,
      name,
      type,
      isRequired,
      placeholder,
    }) =>
      (
        <div>
          <label htmlFor={id}>{labelText}</label>
          <input
            id={id}
            name={name}
            type={type}
            required={isRequired}
            placeholder={placeholder}
            value={value}
            onChange={handleChange}
          />
        </div>
      )
);

jest.mock("../../components/FormAction", () => ({ handleSubmit, text, id }) => (
  <button id={id} type="submit" onClick={handleSubmit}>
    {text}
  </button>
));

describe("Signup Component", () => {
  test("renders all signup fields", () => {
    render(<Signup />);

    signupFields.forEach((field) => {
      expect(screen.getByLabelText(field.labelText)).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: /signup/i })).toBeInTheDocument();
  });

  test("updates input values and submits form", () => {
    render(<Signup />);

    // Fill out each input field
    signupFields.forEach((field) => {
      const input = screen.getByLabelText(field.labelText);
      fireEvent.change(input, { target: { value: `test-${field.id}` } });
      expect(input.value).toBe(`test-${field.id}`);
    });

    // Spy on console.log for submission
    const logSpy = jest.spyOn(console, "log").mockImplementation(() => {});

    const submitBtn = screen.getByRole("button", { name: /signup/i });
    fireEvent.click(submitBtn);

    const expectedState = {};
    signupFields.forEach((field) => {
      expectedState[field.id] = `test-${field.id}`;
    });

    expect(logSpy).toHaveBeenCalledWith(expectedState);

    logSpy.mockRestore();
  });
});
