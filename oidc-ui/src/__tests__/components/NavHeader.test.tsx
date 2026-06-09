import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import NavHeader from '../../components/NavHeader';

describe('NavHeader', () => {
  it('renders the navbar', () => {
    render(<NavHeader />);
    expect(screen.getByRole('navigation')).toBeDefined();
  });

  it('renders the brand logo', () => {
    render(<NavHeader />);
    expect(screen.getByAltText('brand_logo')).toBeDefined();
  });

  it('has the correct id', () => {
    render(<NavHeader />);
    expect(document.getElementById('navbar-header')).not.toBeNull();
  });
});
