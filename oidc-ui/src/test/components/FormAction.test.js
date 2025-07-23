import { render, screen, fireEvent } from "@testing-library/react";
import FormAction from "../../components/FormAction";
import { buttonTypes } from "../../constants/clientConstants";

describe("FormAction Component", () => {
  const mockClick = jest.fn();

  afterEach(() => {
    jest.clearAllMocks();
  });

  const baseProps = {
    handleClick: mockClick,
    text: "Click Me",
    customClassName: "custom-class",
    id: "test-button",
  };
//     render(<FormAction {...baseProps} type={buttonTypes.button} />);
//     const btn = screen.getByRole("button", { name: "Click Me" });
//     expect(btn).toBeInTheDocument();
//     expect(btn).toHaveAttribute("type", "button");
//     expect(btn).toHaveClass("primary-button custom-class");
//     fireEvent.click(btn);
//     expect(mockClick).toHaveBeenCalled();
//   });

//   test("renders submit type button", () => {
//     render(<FormAction {...baseProps} type={buttonTypes.submit} />);
//     const btn = screen.getByRole("button", { name: "Click Me" });
//     expect(btn).toHaveAttribute("type", "submit");
//     expect(btn).toHaveClass("primary-button custom-class");
//     fireEvent.submit(btn); // Note: button onSubmit won't be triggered, but button still renders properly
//   });

//   test("renders reset type and triggers click", () => {
//     render(<FormAction {...baseProps} type={buttonTypes.reset} />);
//     const btn = screen.getByRole("button", { name: "Click Me" });
//     expect(btn).toHaveAttribute("type", "reset");
//     fireEvent.click(btn);
//     expect(mockClick).toHaveBeenCalled();
//   });

  test("renders cancel type with secondary-button style", () => {
    render(<FormAction {...baseProps} type={buttonTypes.cancel} />);
    const btn = screen.getByRole("button", { name: "Click Me" });
    expect(btn).toHaveClass("secondary-button");
    fireEvent.click(btn);
    expect(mockClick).toHaveBeenCalled();
  });

  test("renders discontinue type with discontinue-button style", () => {
    render(<FormAction {...baseProps} type={buttonTypes.discontinue} />);
    const btn = screen.getByRole("button", { name: "Click Me" });
    expect(btn).toHaveClass("discontinue-button");
    fireEvent.click(btn);
    expect(mockClick).toHaveBeenCalled();
  });

  test("renders disabled button", () => {
    render(
      <FormAction {...baseProps} type={buttonTypes.button} disabled={true} />
    );
    const btn = screen.getByRole("button", { name: "Click Me" });
    expect(btn).toBeDisabled();
  });

  test("defaults to Button when type is not provided", () => {
    render(<FormAction {...baseProps} />);
    const btn = screen.getByRole("button", { name: "Click Me" });
    expect(btn).toHaveAttribute("type", "Button"); // Not "button" but "Button" as per default prop
  });
});
