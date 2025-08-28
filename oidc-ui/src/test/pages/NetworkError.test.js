import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import NetworkError from '../../pages/NetworkError';
import { MemoryRouter, useLocation } from 'react-router-dom';

// ✅ Mock i18n
jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key) => key,
  }),
}));

// ✅ Mock useLocation
const mockReplace = jest.fn();
const mockPath = '/some-retry-path';

jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router-dom');
  return {
    ...actual,
    useLocation: jest.fn(),
  };
});

describe('NetworkError', () => {
  beforeEach(() => {
    jest.clearAllMocks();

    // Mock window.location.replace
    delete window.location;
    window.location = { replace: mockReplace };

    // Mock onbeforeunload
    window.onbeforeunload = jest.fn();

    // Mock useLocation to return a state with a path
    useLocation.mockReturnValue({ state: { path: mockPath } });
  });

  it('renders all translated texts', () => {
    render(
      <MemoryRouter>
        <NetworkError />
      </MemoryRouter>
    );

    expect(screen.getByText('errors.network_error.header')).toBeInTheDocument();
    expect(
      screen.getByText('errors.network_error.subHeader')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'errors.network_error.button' })
    ).toBeInTheDocument();
  });

  it('calls tryAgain correctly on button click', () => {
    render(
      <MemoryRouter>
        <NetworkError />
      </MemoryRouter>
    );

    const button = screen.getByRole('button', {
      name: 'errors.network_error.button',
    });

    fireEvent.click(button);

    expect(window.onbeforeunload).toBeNull();
    expect(window.location.replace).toHaveBeenCalledWith(mockPath);
  });
});
