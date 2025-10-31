/**
 * @jest-environment jsdom
 */
import React from 'react';
import {
  render,
  waitFor,
  screen,
  fireEvent,
  act,
} from '@testing-library/react';
import LoginQRCode from '../../components/LoginQRCode';
import {
  configurationKeys,
  walletConfigKeys,
} from '../../constants/clientConstants';
import langConfigService from '../../services/langConfigService';

// --- Mocks ---
jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, options) =>
      typeof options === 'object' ? `${key} ${JSON.stringify(options)}` : key,
  }),
}));

jest.mock('../../services/langConfigService', () => ({
  getEnLocaleConfiguration: jest.fn().mockResolvedValue({
    errors: { wallet: {}, password: {}, otp: {} },
  }),
}));

jest.mock('../../helpers/redirectOnError', () => jest.fn());

jest.mock('qrcode', () => ({
  toCanvas: (canvas, text, options, cb) => {
    canvas.toDataURL = jest.fn(() => 'data:image/png;base64,qrcode');
    cb(null);
  },
}));

// --- Polyfills ---
const mockOpen = jest.fn();

beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = () => ({
    fillRect: jest.fn(),
    drawImage: jest.fn(),
  });
  delete window.open;
  window.open = mockOpen;
});

afterEach(() => {
  jest.clearAllMocks();
  document.body.innerHTML = '';
});

// --- Env vars ---
process.env.REACT_APP_WALLET_LOGO_URL = 'https://example.com/logo.png';
process.env.REACT_APP_QRCODE_DEEP_LINK_URI =
  'https://example.com?qrcode={linkCode}&exp={linkExpiryDate}';
process.env.REACT_APP_LINKED_TRANSACTION_EXPIRE_IN_SEC = '30';
process.env.REACT_APP_QR_CODE_BUFFER_IN_SEC = '5';
process.env.REACT_APP_WALLET_QR_CODE_AUTO_REFRESH_LIMIT = '2';

// -------------------- TESTS --------------------

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

  await waitFor(() =>
    expect(mockLinkAuthService.post_GenerateLinkCode).toHaveBeenCalled()
  );

  expect(screen.getByText(/wallet_header/)).toBeInTheDocument();
});

test('displays fallback error banner when post_GenerateLinkCode throws', async () => {
  const mockWalletDetail = {
    [walletConfigKeys.walletLogoUrl]: 'https://example.com/logo.png',
    [walletConfigKeys.walletName]: 'TestWallet',
  };

  const mockLinkAuthService = {
    post_GenerateLinkCode: jest.fn().mockRejectedValue(new Error('API failed')),
  };

  const mockOpenIDConnectService = {
    getTransactionId: jest.fn().mockReturnValue('txn-123'),
    getEsignetConfiguration: jest.fn(),
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

  await waitFor(() =>
    expect(mockLinkAuthService.post_GenerateLinkCode).toHaveBeenCalled()
  );

  expect(
    screen.getByText('wallet.link_code_refresh_failed')
  ).toBeInTheDocument();
});

test('renders QR code with logo successfully', async () => {
  const mockWalletDetail = {
    [walletConfigKeys.walletLogoUrl]: 'https://example.com/logo.png',
    [walletConfigKeys.walletName]: 'TestWallet',
  };

  const mockCtx = {
    fillRect: jest.fn(),
    drawImage: jest.fn(),
    fillStyle: '',
  };

  const mockCanvas = {
    width: 600,
    height: 600,
    getContext: jest.fn(() => mockCtx),
    toDataURL: jest.fn(() => 'data:image/png;base64,mockqr'),
  };

  const originalCreateElement = document.createElement.bind(document);
  jest.spyOn(document, 'createElement').mockImplementation((tag) => {
    if (tag === 'canvas') return mockCanvas;
    return originalCreateElement(tag);
  });

  Object.defineProperty(global, 'Image', {
    writable: true,
    value: class {
      constructor() {
        setTimeout(() => this.onload && this.onload(), 0);
      }
      set src(v) {
        this._src = v;
      }
    },
  });

  render(
    <LoginQRCode
      walletDetail={mockWalletDetail}
      linkAuthService={{
        post_GenerateLinkCode: jest.fn().mockResolvedValue({
          response: {
            transactionId: 'txn-123',
            linkCode: 'abc123',
            expireDateTime: new Date(Date.now() + 30000).toISOString(),
          },
          errors: null,
        }),
      }}
      openIDConnectService={{
        getTransactionId: jest.fn().mockReturnValue('txn-123'),
        getEsignetConfiguration: jest.fn(),
      }}
      backButtonDiv={<div data-testid="back-button">Back</div>}
      secondaryHeading="wallet_header"
    />
  );

  await waitFor(() => expect(mockCanvas.toDataURL).toHaveBeenCalled());
  expect(mockCtx.fillRect).toHaveBeenCalled();
  expect(mockCtx.drawImage).toHaveBeenCalled();

  document.createElement.mockRestore();
});

test('logs error when lang config fails to load', async () => {
  const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
  langConfigService.getEnLocaleConfiguration.mockRejectedValueOnce(
    new Error('Failed to load lang config')
  );

  const mockOpenIDConnectService = {
    getTransactionId: jest.fn().mockReturnValue('txn-123'),
    getEsignetConfiguration: jest.fn(),
  };

  const mockLinkAuthService = {
    post_GenerateLinkCode: jest.fn().mockResolvedValue({
      response: { transactionId: 'txn-123', linkCode: 'abc123' },
      errors: null,
    }),
  };

  render(
    <LoginQRCode
      walletDetail={{ [walletConfigKeys.walletName]: 'TestWallet' }}
      linkAuthService={mockLinkAuthService}
      openIDConnectService={mockOpenIDConnectService}
      backButtonDiv={<div data-testid="back-button">Back</div>}
      secondaryHeading="wallet_header"
    />
  );

  await waitFor(() =>
    expect(consoleSpy).toHaveBeenCalledWith(
      'Failed to load lang config',
      expect.any(Error)
    )
  );

  consoleSpy.mockRestore();
});

test('fetches QR code when linkAuthTriggered is false', async () => {
  const mockLinkAuthService = {
    post_GenerateLinkCode: jest.fn().mockResolvedValue({
      response: {
        transactionId: 'txn-123',
        linkCode: 'abc123',
        expireDateTime: new Date(Date.now() + 30000).toISOString(),
      },
      errors: null,
    }),
  };
  const mockOpenIDConnectService = {
    getTransactionId: jest.fn().mockReturnValue('txn-123'),
    getEsignetConfiguration: jest.fn(),
  };

  render(
    <LoginQRCode
      walletDetail={{ [walletConfigKeys.walletName]: 'TestWallet' }}
      linkAuthService={mockLinkAuthService}
      openIDConnectService={mockOpenIDConnectService}
      linkAuthTriggered={false}
      backButtonDiv={<div data-testid="back-button">Back</div>}
      secondaryHeading="wallet_header"
    />
  );

  await waitFor(() =>
    expect(mockLinkAuthService.post_GenerateLinkCode).toHaveBeenCalled()
  );
});
