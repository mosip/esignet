import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import NavHeader from '../../components/NavHeader';

const mockFetchThemeConfig = vi.fn();
const mockChangeLanguage = vi.fn();
const mockOn = vi.fn();
const mockOff = vi.fn();

vi.mock('../../services/config.service', () => ({
  fetchThemeConfig: (...args: unknown[]) => mockFetchThemeConfig(...args),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: {
      language: 'en',
      changeLanguage: mockChangeLanguage,
      on: mockOn,
      off: mockOff,
    },
  }),
}));

const langOptions = [
  { value: 'en', label: 'English' },
  { value: 'hi', label: 'हिंदी' },
  { value: 'ar', label: 'عربى' },
];

describe('NavHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchThemeConfig.mockResolvedValue({
      outline_dropdown: false,
      remove_language_indicator_pipe: true,
    });
  });

  it('renders the navbar', async () => {
    render(<NavHeader langOptions={langOptions} />);
    await waitFor(() => {
      expect(screen.getByRole('navigation')).toBeDefined();
    });
  });

  it('renders the brand logo', async () => {
    render(<NavHeader langOptions={langOptions} />);
    expect(screen.getByAltText('brand_logo')).toBeDefined();
  });

  it('renders the language selector when langOptions provided', async () => {
    render(<NavHeader langOptions={langOptions} />);
    await waitFor(() => {
      expect(screen.getByLabelText('Language selector')).toBeDefined();
    });
  });

  it('displays the selected language label', async () => {
    render(<NavHeader langOptions={langOptions} />);
    await waitFor(() => {
      expect(screen.getByText('English')).toBeDefined();
    });
  });

  it('registers and cleans up language change listener', () => {
    const { unmount } = render(<NavHeader langOptions={langOptions} />);
    expect(mockOn).toHaveBeenCalledWith('languageChanged', expect.any(Function));
    unmount();
    expect(mockOff).toHaveBeenCalledWith('languageChanged', expect.any(Function));
  });

  it('hides language dropdown when no langOptions', async () => {
    render(<NavHeader langOptions={[]} />);
    await waitFor(() => expect(mockFetchThemeConfig).toHaveBeenCalled());
    expect(screen.queryByLabelText('Language selector')).toBeNull();
  });
});
