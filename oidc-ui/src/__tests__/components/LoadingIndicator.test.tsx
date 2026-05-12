import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import LoadingIndicator from '../../components/LoadingIndicator';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

describe('LoadingIndicator', () => {
  it('renders without crashing', () => {
    render(<LoadingIndicator />);
    expect(screen.getByRole('status')).toBeDefined();
  });

  it('renders with sr-only loading text', () => {
    render(<LoadingIndicator />);
    expect(screen.getByText('Loading...')).toBeDefined();
  });

  it('displays a translated message when provided', () => {
    render(<LoadingIndicator message="loading" />);
    expect(screen.getByText('loading')).toBeDefined();
  });

  it('applies custom className', () => {
    render(<LoadingIndicator className="test-class" />);
    const el = screen.getByRole('status');
    expect(el.className).toContain('test-class');
  });

  it('applies small size styles', () => {
    render(<LoadingIndicator size="small" />);
    const svg = screen.getByRole('status').querySelector('svg');
    expect(svg?.style.width).toBe('1.5rem');
    expect(svg?.style.height).toBe('1.5rem');
  });

  it('applies large size styles', () => {
    render(<LoadingIndicator size="large" />);
    const svg = screen.getByRole('status').querySelector('svg');
    expect(svg?.style.width).toBe('5rem');
    expect(svg?.style.height).toBe('5rem');
  });

  it('defaults to medium size', () => {
    render(<LoadingIndicator />);
    const svg = screen.getByRole('status').querySelector('svg');
    expect(svg?.style.width).toBe('2.5rem');
    expect(svg?.style.height).toBe('2.5rem');
  });
});
