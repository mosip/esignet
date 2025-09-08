import React from 'react';
import { render } from '@testing-library/react';
import AuthorizePage from '../../pages/Authorize';
import * as AuthServiceModule from '../../services/authService';

// ✅ Correctly mock the default export Authorize
jest.mock('../../components/Authorize', () => ({
  __esModule: true, // ⬅️ VERY IMPORTANT for default export mocking
  default: jest.fn(() => <div data-testid="authorize-component" />),
}));

import Authorize from '../../components/Authorize';

describe('AuthorizePage', () => {
  it('should render Authorize component with authService prop', () => {
    // ✅ Create a specific instance for the mock constructor
    const mockInstance = { foo: 'bar' };
    const AuthServiceMock = jest.fn(() => mockInstance);

    // ✅ Spy on the default export and replace with our mock
    jest
      .spyOn(AuthServiceModule, 'default')
      .mockImplementation(AuthServiceMock);

    render(<AuthorizePage />);

    // Check if service constructor was called with null
    expect(AuthServiceMock).toHaveBeenCalledWith(null);

    // Check if mock Authorize was called with correct props
    expect(Authorize).toHaveBeenCalledWith(
      expect.objectContaining({
        authService: mockInstance, // 👈 exact instance returned from mock
      }),
      {}
    );
  });
});
