import React from "react";
import { render, screen } from "@testing-library/react";
import AuthorizePage from "../../pages/Authorize";
import * as AuthServiceModule from "../../services/authService";

// ✅ Correctly mock the default export Authorize
jest.mock("../../components/Authorize", () => ({
  __esModule: true, // ⬅️ VERY IMPORTANT for default export mocking
  default: jest.fn(() => <div data-testid="authorize-component" />),
}));

import Authorize from "../../components/Authorize";

describe("AuthorizePage", () => {
  it("should render Authorize component with authService prop", () => {
    // Mock the class constructor
    const AuthServiceMock = jest.fn();
    AuthServiceModule.default = AuthServiceMock;

    render(<AuthorizePage />);

    // Check if service constructor was called with null
    expect(AuthServiceMock).toHaveBeenCalledWith(null);

    // Check if mock Authorize was called with correct props
    expect(Authorize).toHaveBeenCalledWith(
      expect.objectContaining({
        authService: expect.any(AuthServiceMock),
      }),
      {}
    );
  });
});
