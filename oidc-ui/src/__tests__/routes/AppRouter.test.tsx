import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AppRouter from '../../routes/AppRouter';

vi.mock('../../services/config.service', () => ({
  fetchThemeConfig: () => Promise.resolve({ footer: false, background_logo: false }),
}));

vi.mock('react-detect-offline', () => ({
  Detector: ({ render }: { render: (props: { online: boolean }) => null }) =>
    render({ online: true }),
}));

describe('AppRouter', () => {
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
      expect(screen.getByText(/Something went wrong/)).toBeDefined();
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
});
