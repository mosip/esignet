import { render, screen } from "@testing-library/react";
import Header from "../../components/Header";

describe("Header Component", () => {
  const props = {
    heading: "Welcome to Esignet",
    paragraph: "New here?",
    linkName: "Sign up",
    linkUrl: "/register",
  };

  test("renders heading text", () => {
    render(<Header {...props} />);
    const heading = screen.getByRole("heading", {
      name: /welcome to esignet/i,
    });
    expect(heading).toBeInTheDocument();
  });

  test("renders paragraph and link with correct text", () => {
    render(<Header {...props} />);
    expect(screen.getByText("New here?")).toBeInTheDocument();

    const link = screen.getByRole("link", { name: "Sign up" });
    expect(link).toHaveAttribute("href", "/register");
    expect(link).toHaveClass("font-medium text-cyan-600 hover:text-purple-500");
    expect(link).toHaveAttribute("id", "header-link-url");
  });

  test("uses default linkUrl if not provided", () => {
    render(
      <Header
        heading="Hello"
        paragraph="Already have an account?"
        linkName="Login"
      />
    );
    const link = screen.getByRole("link", { name: "Login" });
    expect(link).toHaveAttribute("href", "#");
  });
});
