import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import Footer from '../../components/Footer';

const mockFetchThemeConfig = vi.fn();

vi.mock('../../services/config.service', () => ({
  fetchThemeConfig: (...args: unknown[]) => mockFetchThemeConfig(...args),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

describe('Footer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when config has footer: false', async () => {
    mockFetchThemeConfig.mockResolvedValue({ footer: false });
    const { container } = render(<Footer />);
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    expect(container.querySelector('#footer')).toBeNull();
  });

  it('renders footer when config has footer: true', async () => {
    mockFetchThemeConfig.mockResolvedValue({ footer: true });
    render(<Footer />);
    await waitFor(() => {
      expect(screen.getByText('powered_by')).toBeDefined();
    });
  });

  it('renders nothing initially while loading', () => {
    mockFetchThemeConfig.mockReturnValue(new Promise(() => {}));
    const { container } = render(<Footer />);
    expect(container.querySelector('#footer')).toBeNull();
  });

  it('handles config fetch error gracefully', async () => {
    mockFetchThemeConfig.mockRejectedValue(new Error('fail'));
    const { container } = render(<Footer />);
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    expect(container.querySelector('#footer')).toBeNull();
  });
});
