import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../../pages/LoginPage';

vi.mock('@asgardeo/react', () => ({
  SignIn: () => <div data-testid="sign-in">SignIn Component</div>,
}));

describe('LoginPage', () => {
  it('renders the loading indicator initially', () => {
    render(
      <MemoryRouter initialEntries={['/login?applicationId=test&authId=test']}>
        <LoginPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('status')).toBeDefined();
  });
});
