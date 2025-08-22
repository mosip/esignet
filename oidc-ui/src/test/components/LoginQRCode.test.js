import React from 'react';
import { render, waitFor, screen } from '@testing-library/react';
import LoginQRCode from '../../components/LoginQRCode';
import {
  configurationKeys,
  walletConfigKeys,
} from '../../constants/clientConstants';

// Mock i18n
jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, options) =>
      typeof options === 'object' ? `${key} ${JSON.stringify(options)}` : key,
  }),
}));

// Mock services and helpers
jest.mock('../../services/langConfigService', () => ({
  getEnLocaleConfiguration: jest.fn().mockResolvedValue({
    errors: {
      wallet: {},
      password: {},
      otp: {},
    },
  }),
}));
jest.mock('../../helpers/redirectOnError', () => jest.fn());

// Mock QRCode canvas generation
jest.mock('qrcode', () => ({
  toCanvas: (canvas, text, options, cb) => {
    // simulate success and generate a dummy QR code
    cb(null);
    canvas.toDataURL = () => 'data:image/png;base64,qrcode';
  },
}));

// Polyfill for `canvas`
beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = () => ({
    fillRect: jest.fn(),
    drawImage: jest.fn(),
  });
});

// Environment variables fallback
process.env.REACT_APP_WALLET_LOGO_URL = 'https://example.com/logo.png';
process.env.REACT_APP_QRCODE_DEEP_LINK_URI =
  'https://example.com?qrcode={linkCode}&exp={linkExpiryDate}';
process.env.REACT_APP_LINKED_TRANSACTION_EXPIRE_IN_SEC = '30';
process.env.REACT_APP_QR_CODE_BUFFER_IN_SEC = '5';
process.env.REACT_APP_WALLET_QR_CODE_AUTO_REFRESH_LIMIT = '2';

// Test case
test('renders LoginQRCode component without crashing', async () => {
  const mockWalletDetail = {
    [walletConfigKeys.walletLogoUrl]: 'https://example.com/logo.png',
    [walletConfigKeys.qrCodeDeepLinkURI]:
      'https://example.com?qrcode={linkCode}&exp={linkExpiryDate}',
    [walletConfigKeys.walletName]: 'TestWallet',
    [walletConfigKeys.walletFooter]: true,
    [walletConfigKeys.appDownloadURI]: 'https://example.com/download',
  };

  const mockLinkAuthService = {
    post_GenerateLinkCode: jest.fn().mockResolvedValue({
      response: {
        transactionId: 'txn-123',
        linkCode: 'abc123',
        expireDateTime: new Date(Date.now() + 30000).toISOString(),
      },
      errors: null,
    }),
    post_LinkStatus: jest
      .fn()
      .mockResolvedValue({ response: { linkStatus: 'LINKED' }, errors: [] }),
    post_AuthorizationCode: jest.fn().mockResolvedValue({
      response: { code: 'auth-code', redirectUri: 'https://redirect.com' },
      errors: [],
    }),
  };

  const mockOpenIDConnectService = {
    getTransactionId: jest.fn().mockReturnValue('txn-123'),
    getEsignetConfiguration: jest.fn((key) => {
      switch (key) {
        case configurationKeys.linkedTransactionExpireInSecs:
          return 30;
        case configurationKeys.qrCodeBufferInSecs:
          return 5;
        case configurationKeys.walletQrCodeAutoRefreshLimit:
          return 2;
        default:
          return null;
      }
    }),
  };

  render(
    <LoginQRCode
      walletDetail={mockWalletDetail}
      linkAuthService={mockLinkAuthService}
      openIDConnectService={mockOpenIDConnectService}
      backButtonDiv={<div data-testid="back-button">Back</div>}
      secondaryHeading="wallet_header"
    />
  );

  // Wait for loading state to appear and disappear
  await waitFor(() => {
    expect(mockLinkAuthService.post_GenerateLinkCode).toHaveBeenCalled();
  });

  // Check heading or expected QR related label
  expect(screen.getByText(/wallet_header/)).toBeInTheDocument();
});
test('displays fallback error banner when post_GenerateLinkCode throws', async () => {
  const mockWalletDetail = {
    [walletConfigKeys.walletLogoUrl]: 'https://example.com/logo.png',
    [walletConfigKeys.qrCodeDeepLinkURI]:
      'https://example.com?qrcode={linkCode}&exp={linkExpiryDate}',
    [walletConfigKeys.walletName]: 'TestWallet',
    [walletConfigKeys.walletFooter]: true,
    [walletConfigKeys.appDownloadURI]: 'https://example.com/download',
  };

  const mockLinkAuthService = {
    post_GenerateLinkCode: jest.fn().mockRejectedValue(new Error('API failed')),
    post_LinkStatus: jest.fn(),
    post_AuthorizationCode: jest.fn(),
  };

  const mockOpenIDConnectService = {
    getTransactionId: jest.fn().mockReturnValue('txn-123'),
    getEsignetConfiguration: jest.fn((key) => {
      switch (key) {
        case configurationKeys.linkedTransactionExpireInSecs:
          return 30;
        case configurationKeys.qrCodeBufferInSecs:
          return 5;
        case configurationKeys.walletQrCodeAutoRefreshLimit:
          return 2;
        default:
          return null;
      }
    }),
  };

  render(
    <LoginQRCode
      walletDetail={mockWalletDetail}
      linkAuthService={mockLinkAuthService}
      openIDConnectService={mockOpenIDConnectService}
      backButtonDiv={<div data-testid="back-button">Back</div>}
      secondaryHeading="wallet_header"
    />
  );

  // Wait for the rejected API call to be handled
  await waitFor(() => {
    expect(mockLinkAuthService.post_GenerateLinkCode).toHaveBeenCalled();
  });

  // This is the fallback hardcoded error string shown in the UI when catch block hits
  expect(
    screen.getByText('wallet.link_code_refresh_failed')
  ).toBeInTheDocument();

  // Optional: Ensure loading spinner was visible before error appeared
  expect(screen.getByText('loading_msg')).toBeInTheDocument();
});
