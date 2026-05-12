import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import LoginPage from '../../pages/LoginPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback: string) => fallback || key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

describe('LoginPage', () => {
  it('renders the login heading', () => {
    render(<LoginPage />);
    expect(screen.getByRole('heading', { name: 'Login' })).toBeDefined();
  });

  it('renders the subheader', () => {
    render(<LoginPage />);
    expect(screen.getByText('Please sign in to continue')).toBeDefined();
  });

  it('renders username and password fields', () => {
    render(<LoginPage />);
    expect(screen.getByPlaceholderText('Enter your username')).toBeDefined();
    expect(screen.getByPlaceholderText('Enter your password')).toBeDefined();
  });

  it('renders the login button', () => {
    render(<LoginPage />);
    const buttons = screen.getAllByText('Login');
    // One heading + one button
    expect(buttons.length).toBeGreaterThanOrEqual(2);
  });

  it('renders the illustration image', () => {
    render(<LoginPage />);
    expect(screen.getByAltText('login_illustration')).toBeDefined();
  });

  it('has readonly input fields', () => {
    render(<LoginPage />);
    const usernameInput = screen.getByPlaceholderText('Enter your username') as HTMLInputElement;
    const passwordInput = screen.getByPlaceholderText('Enter your password') as HTMLInputElement;
    expect(usernameInput.readOnly).toBe(true);
    expect(passwordInput.readOnly).toBe(true);
  });
});
