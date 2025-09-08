import React from 'react';
import { render, screen } from '@testing-library/react';
import SignupPage from '../../pages/Signup';

// ✅ Mock Header component
jest.mock('../../components/Header', () => ({
  __esModule: true,
  default: ({ heading, paragraph, linkName, linkUrl }) => (
    <div data-testid="header">
      <div>{heading}</div>
      <div>{paragraph}</div>
      <div>{linkName}</div>
      <div>{linkUrl}</div>
    </div>
  ),
}));

// ✅ Mock Signup component
jest.mock('../../components/Signup', () => ({
  __esModule: true,
  default: () => <div data-testid="signup">Mocked Signup</div>,
}));

describe('SignupPage', () => {
  it('renders Header with correct props and Signup component', () => {
    render(<SignupPage />);

    // Check Header content
    expect(screen.getByTestId('header')).toBeInTheDocument();
    expect(
      screen.getByText('Preregister to create an account')
    ).toBeInTheDocument();
    expect(screen.getByText('Login')).toBeInTheDocument();
    expect(screen.getByText('/')).toBeInTheDocument();

    // Check Signup component
    expect(screen.getByTestId('signup')).toBeInTheDocument();
  });
});
