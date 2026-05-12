import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import NetworkErrorPage from '../../pages/NetworkErrorPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback: string) => fallback || key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

describe('NetworkErrorPage', () => {
  it('renders the no internet SVG icon', () => {
    render(
      <MemoryRouter>
        <NetworkErrorPage />
      </MemoryRouter>,
    );
    const svg = document.querySelector('svg[viewBox="0 0 102 102"]');
    expect(svg).not.toBeNull();
  });

  it('renders the header text', () => {
    render(
      <MemoryRouter>
        <NetworkErrorPage />
      </MemoryRouter>,
    );
    expect(screen.getByText('No Internet Connection')).toBeDefined();
  });

  it('renders the subheader text', () => {
    render(
      <MemoryRouter>
        <NetworkErrorPage />
      </MemoryRouter>,
    );
    expect(
      screen.getByText(
        'Please check your network connection and try again.',
      ),
    ).toBeDefined();
  });

  it('renders the try again button', () => {
    render(
      <MemoryRouter>
        <NetworkErrorPage />
      </MemoryRouter>,
    );
    expect(screen.getByText('Try Again')).toBeDefined();
  });

  it('try again button has correct id', () => {
    render(
      <MemoryRouter>
        <NetworkErrorPage />
      </MemoryRouter>,
    );
    const button = screen.getByText('Try Again');
    expect(button.id).toBe('try_again');
  });
});
