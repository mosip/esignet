import { render, screen, fireEvent } from '@testing-library/react';
import EsignetDetails from '../../components/EsignetDetails';
import { useTranslation } from 'react-i18next';

jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
}));

describe('EsignetDetails', () => {
  const mockT = jest.fn((key) => key);
  const originalEnv = { ...window._env_ };

  beforeEach(() => {
    jest.clearAllMocks();
    useTranslation.mockReturnValue({ t: mockT });

    // Mock window.open
    global.open = jest.fn();

    // Default wellknown data
    window._env_ = {
      DEFAULT_WELLKNOWN: encodeURIComponent(
        JSON.stringify([
          {
            name: 'Service A',
            value: '/.well-known/service-a',
            icon: 'icon-a.png',
          },
          { name: 'Service B', value: '/.well-known/service-b' }, // no icon
        ])
      ),
    };
  });

  afterAll(() => {
    window._env_ = originalEnv;
  });

  test('renders heading and well-known details with icons and names', async () => {
    render(<EsignetDetails />);

    expect(mockT).toHaveBeenCalledWith('loading_msg');
    expect(screen.getByAltText('')).toHaveAttribute('src', 'icon-a.png');
    expect(screen.getByText('/.well-known/service-b')).toBeInTheDocument();
    expect(screen.getByText('Service B')).toBeInTheDocument();
  });

  test('clicking on a well-known endpoint calls window.open', async () => {
    render(<EsignetDetails />);
    const link = await screen.findByText('/.well-known/service-a');

    fireEvent.click(link);
    expect(global.open).toHaveBeenCalledWith(
      expect.stringContaining('/.well-known/service-a'),
      '_blank',
      'noopener,noreferrer'
    );
  });

  test('handles invalid DEFAULT_WELLKNOWN JSON', async () => {
    const consoleSpy = jest
      .spyOn(console, 'error')
      .mockImplementation(() => {});
    window._env_ = {
      DEFAULT_WELLKNOWN: '%7Binvalid%3Ajson%7D', // invalid JSON
    };

    render(<EsignetDetails />);

    expect(consoleSpy).toHaveBeenCalledWith(
      'Default Wellknown Endpoint is not a valid JSON'
    );

    consoleSpy.mockRestore();
  });

  test('respects custom i18nKeyPrefix prop', async () => {
    render(<EsignetDetails i18nKeyPrefix="customPrefix" />);
    expect(useTranslation).toHaveBeenCalledWith('translation', {
      keyPrefix: 'customPrefix',
    });
  });
});
