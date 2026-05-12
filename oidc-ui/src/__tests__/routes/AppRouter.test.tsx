import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AppRouter from '../../routes/AppRouter';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback: string) => fallback || key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

vi.mock('../../services/config.service', () => ({
  fetchThemeConfig: () => Promise.resolve({ footer: false }),
}));

describe('AppRouter', () => {
  it('renders login page on /login route', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRouter />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Login' })).toBeDefined();
    });
  });

  it('renders something wrong page on /something-went-wrong route', async () => {
    render(
      <MemoryRouter
        initialEntries={[
          { pathname: '/something-went-wrong', state: { code: 500 } },
        ]}
      >
        <AppRouter />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Something went wrong')).toBeDefined();
    });
  });

  it('renders network error page on /network-error route', async () => {
    render(
      <MemoryRouter initialEntries={['/network-error']}>
        <AppRouter />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('No Internet Connection')).toBeDefined();
    });
  });

  it('renders page not found for unknown routes', async () => {
    render(
      <MemoryRouter initialEntries={['/unknown-route']}>
        <AppRouter />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Page Not Found')).toBeDefined();
    });
  });

  it('renders page not found for root path', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRouter />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Page Not Found')).toBeDefined();
    });
  });
});
