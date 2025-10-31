import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRouter } from '../../../src/app/AppRouter';
import * as configServiceModule from '../../../src/services/configService';

jest.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key) => key }),
}));

jest.mock('../../../src/services/api.service', () => ({
  setupResponseInterceptor: jest.fn(),
}));

jest.mock('../../../src/common/LoadingIndicator', () => {
  const LoadingIndicator = () => <div data-testid="loading">Loading...</div>;
  LoadingIndicator.displayName = 'LoadingIndicator';
  return LoadingIndicator;
});

jest.mock('../../../src/pages', () => ({
  LoginPage: () => <div>LoginPage</div>,
  AuthorizePage: () => <div>AuthorizePage</div>,
  ConsentPage: () => <div>ConsentPage</div>,
  EsignetDetailsPage: () => <div>EsignetDetailsPage</div>,
  SomethingWrongPage: () => <div>SomethingWrongPage</div>,
  PageNotFoundPage: () => <div>PageNotFoundPage</div>,
}));

jest.mock('../../../src/components/ClaimDetails', () => {
  const ClaimDetails = () => <div>ClaimDetails</div>;
  ClaimDetails.displayName = 'ClaimDetails';
  return ClaimDetails;
});

jest.mock('../../../src/pages/NetworkError', () => {
  const NetworkError = () => <div>NetworkError</div>;
  NetworkError.displayName = 'NetworkError';
  return NetworkError;
});

jest.mock('react-detect-offline', () => ({
  Detector: ({ render }) => render({ online: true }),
}));

jest.mock('../../../src/helpers/utils', () => ({
  getPollingConfig: () => ({
    url: '/health',
    interval: 10000,
    timeout: 5000,
    enabled: true,
  }),
}));

jest.spyOn(console, 'error').mockImplementation(() => {}); // silence error log

describe('AppRouter', () => {
  const mockConfig = {
    background_logo: true,
  };

  beforeEach(() => {
    jest.spyOn(configServiceModule, 'default').mockResolvedValue(mockConfig);
  });

  afterEach(() => {
    jest.clearAllMocks();
    document.body.style.overflow = ''; // reset after each test
  });

  it('renders loading indicator initially', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRouter />
      </MemoryRouter>
    );
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByTestId('loading')).not.toBeInTheDocument()
    );
  });

  it('renders LoginPage for /login route', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRouter />
      </MemoryRouter>
    );
    expect(await screen.findByText('LoginPage')).toBeInTheDocument();
  });

  it('renders AuthorizePage for /authorize route', async () => {
    render(
      <MemoryRouter initialEntries={['/authorize']}>
        <AppRouter />
      </MemoryRouter>
    );
    expect(await screen.findByText('AuthorizePage')).toBeInTheDocument();
  });

  it('renders ConsentPage for /consent route', async () => {
    render(
      <MemoryRouter initialEntries={['/consent']}>
        <AppRouter />
      </MemoryRouter>
    );
    expect(await screen.findByText('ConsentPage')).toBeInTheDocument();
  });

  it('renders NetworkError for /network-error route', async () => {
    render(
      <MemoryRouter initialEntries={['/network-error']}>
        <AppRouter />
      </MemoryRouter>
    );
    expect(await screen.findByText('NetworkError')).toBeInTheDocument();
  });

  it('calls console.error when configService fails', async () => {
    jest
      .spyOn(configServiceModule, 'default')
      .mockRejectedValueOnce(new Error('network fail'));

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRouter />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(console.error).toHaveBeenCalledWith(
        'Failed to fetch config:',
        expect.any(Error)
      );
    });
  });
});
