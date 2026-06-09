import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PageNotFoundPage from '../../pages/PageNotFoundPage';

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
