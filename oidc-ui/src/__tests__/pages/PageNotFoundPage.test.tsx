import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import PageNotFoundPage from '../../pages/PageNotFoundPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback: string) => fallback || key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

describe('PageNotFoundPage', () => {
  it('renders the under construction image', () => {
    render(<PageNotFoundPage />);
    expect(screen.getByAltText('page_not_found')).toBeDefined();
  });

  it('renders the page not found heading', () => {
    render(<PageNotFoundPage />);
    expect(screen.getByText('Page Not Found')).toBeDefined();
  });

  it('has the correct CSS classes', () => {
    const { container } = render(<PageNotFoundPage />);
    const card = container.firstElementChild;
    expect(card?.className).toContain('multipurpose-login-card');
    expect(card?.className).toContain('section-background');
  });
});
