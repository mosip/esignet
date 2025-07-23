import { render, screen, fireEvent } from "@testing-library/react";
import Tabs from "../../components/Tabs";
import { useTranslation } from "react-i18next";

jest.mock("react-i18next", () => ({
  useTranslation: jest.fn(),
}));

describe("Tabs Component", () => {
  const tabs = [
    { name: "tabOne", icon: "user", comp: "UserComponent" },
    { name: "tabTwo", icon: "settings", comp: "SettingsComponent" },
  ];

  const block = new Map([
    ["user", <div key="user">User Tab Content</div>],
    ["settings", <div key="settings">Settings Tab Content</div>],
  ]);

  beforeEach(() => {
    useTranslation.mockReturnValue({
      t: (key) => key,
    });
  });

  test("renders tab names and icons", () => {
    render(<Tabs color="purple" tabs={tabs} block={block} />);
    expect(screen.getByText("tabOne")).toBeInTheDocument();
    expect(screen.getByText("tabTwo")).toBeInTheDocument();
    expect(screen.getByText("User Tab Content")).toBeInTheDocument();
  });

  test("renders icon class names correctly", () => {
    render(<Tabs color="purple" tabs={tabs} block={block} />);

    expect(screen.getByText("tabOne").querySelector("i")).toHaveClass(
      "fas",
      "fa-user"
    );
    expect(screen.getByText("tabTwo").querySelector("i")).toHaveClass(
      "fas",
      "fa-settings"
    );
  });

  test("renders tab headers and first tab content by default", () => {
    render(<Tabs color="purple" tabs={tabs} block={block} />);

    expect(screen.getByText("tabOne")).toBeInTheDocument();
    expect(screen.getByText("tabTwo")).toBeInTheDocument();
    expect(screen.getByText("User Tab Content")).toBeInTheDocument();
  });

  test("switches to second tab on click and renders its content", () => {
    render(<Tabs color="purple" tabs={tabs} block={block} />);

    const secondTab = screen.getByText("tabTwo");
    fireEvent.click(secondTab);

    expect(screen.getByText("Settings Tab Content")).toBeInTheDocument();
  });
});
