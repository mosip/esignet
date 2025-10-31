import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Pin from '../../components/Pin';
import openIDConnectService from '../../services/openIDConnectService';
import authService from '../../services/authService';
import { useTranslation } from 'react-i18next';
import langConfigService from '../../services/langConfigService';
import { configurationKeys } from '../../constants/clientConstants';
import redirectOnError from '../../helpers/redirectOnError';

// ---------- Mocks ----------
jest.mock('../../services/openIDConnectService');
jest.mock('../../services/authService');
jest.mock('../../services/langConfigService');
jest.mock('../../helpers/redirectOnError');
jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
}));
jest.mock('react-google-recaptcha', () => {
  return function MockReCAPTCHA({ onChange }) {
    return (
      <div
        data-testid="recaptcha"
        role="button"
        tabIndex={0}
        aria-label="Mock ReCAPTCHA"
        onClick={() => onChange && onChange('mock-captcha-token')}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            onChange && onChange('mock-captcha-token');
          }
        }}
      />
    );
  };
});

// ---------- Mock Data ----------
const mockOAuthDetails = {
  clientName: { '@none': 'Test Client' },
  logoUrl: '/logo.png',
};

const mockOpenIDConnectService = {
  getOAuthDetails: jest.fn(() => mockOAuthDetails),
  getTransactionId: jest.fn(() => 'txn-123'),
  getNonce: jest.fn(() => 'mockNonce'),
  getState: jest.fn(() => 'mockState'),
  getEsignetConfiguration: jest.fn((key) => {
    const config = {
      [configurationKeys.loginIdOptions]: [
        {
          id: 'vid',
          input_label: 'input.label.vid',
          input_placeholder: 'input.placeholder.vid',
          prefixes: [],
        },
      ],
      [configurationKeys.captchaEnableComponents]: 'pin',
      [configurationKeys.captchaSiteKey]: 'test-key',
    };
    return config[key];
  }),
};

const mockAuthService = {
  post_AuthenticateUser: jest.fn(() =>
    Promise.resolve({
      response: { consentAction: 'consent_given' },
      errors: null,
    })
  ),
  buildRedirectParams: jest.fn(() => '?next=claim-details'),
};

const mockFields = [
  {
    id: 'vid',
    labelText: 'input.label.vid',
    type: 'text',
    isRequired: true,
    placeholder: 'input.placeholder.vid',
    errorCode: 'invalid_vid',
    maxLength: '50',
    regex: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$', // Adjusted to match VID format
  },
  {
    id: 'pin',
    labelText: 'PIN',
    type: 'password',
    isRequired: true,
    placeholder: 'Enter PIN',
    errorCode: 'invalid_pin',
    maxLength: '6',
    regex: '^[0-9]{6}$',
  },
];

const mockBackButtonDiv = <div>Back</div>;

// ---------- Translation Mock ----------
const mockTranslation = {
  t: (key, options) => {
    const map = {
      'pin.input.label.vid': 'VID',
      'pin.input.placeholder.vid': 'Enter VID',
      'pin.PIN': 'PIN',
      'pin.invalid_vid': 'Invalid VID',
      'pin.invalid_pin': 'Invalid PIN',
      'pin.login': 'login',
      'pin.remember_me': 'remember_me',
      'pin.secondary_heading': options?.currentID
        ? `Secondary Heading (${options.currentID})`
        : 'secondary_heading',
      'buttons.vid': 'VID Button',
      'errors.invalid_transaction': 'Invalid Transaction',
      'errors.authentication_failed_msg': 'Authentication Failed',
      loading_msg: 'Loading...',
      authenticating_msg: 'Authenticating...',
    };
    return map[key] || key;
  },
  i18n: {
    language: 'en',
    on: jest.fn((event, callback) => callback),
    changeLanguage: jest.fn(),
  },
};

// ---------- Mock ErrorBanner for Close Button ----------
jest.mock('../../common/ErrorBanner', () => {
  return function MockErrorBanner({ showBanner, errorCode, onCloseHandle }) {
    if (!showBanner) return null;
    return (
      <div data-testid="error-banner">
        <span>{errorCode}</span>
        <button data-testid="error-banner-close" onClick={onCloseHandle}>
          Close
        </button>
      </div>
    );
  };
});

// ---------- window.location Mock ----------
const originalLocation = window.location;

beforeAll(() => {
  delete window.location;
  window.location = {
    href: 'http://localhost?state=mockState&nonce=mockNonce&ui_locales=en#eyJjbGllbnROYW1lIjp7IkBub25lIjoiVGVzdCBDbGllbnQifSwibG9nb1VybCI6Ii9sb2dvLnBuZyJ9',
    search: '?state=mockState&nonce=mockNonce&ui_locales=en',
    hash: '#eyJjbGllbnROYW1lIjp7IkBub25lIjoiVGVzdCBDbGllbnQifSwibG9nb1VybCI6Ii9sb2dvLnBuZyJ9',
    replace: jest.fn(),
  };

  jest.spyOn(window, 'atob').mockImplementation(() =>
    JSON.stringify({
      clientName: { '@none': 'Test Client' },
      logoUrl: '/logo.png',
      configs: {
        'login-id.options': [
          {
            id: 'vid',
            input_label: 'input.label.vid',
            input_placeholder: 'input.placeholder.vid',
            prefixes: [],
          },
        ],
      },
    })
  );
});

afterAll(() => {
  window.location = originalLocation;
  jest.restoreAllMocks();
});

beforeEach(() => {
  openIDConnectService.mockReturnValue(mockOpenIDConnectService);
  authService.mockReturnValue(mockAuthService);
  langConfigService.getEnLocaleConfiguration.mockResolvedValue({
    errors: { pin: { invalid_pin: 'Invalid PIN' } },
  });
  useTranslation.mockReturnValue(mockTranslation);
});

afterEach(() => {
  jest.clearAllMocks();
});

// ---------- Render Helper ----------
const renderComponent = (props = {}) =>
  render(
    <MemoryRouter>
      <Pin
        param={mockFields}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        backButtonDiv={mockBackButtonDiv}
        secondaryHeading="secondary_heading"
        i18nKeyPrefix1="pin"
        i18nKeyPrefix2="errors"
        {...props}
      />
    </MemoryRouter>
  );

// ---------- Tests ----------
describe('Pin Component', () => {
  test('Renders Pin component with required fields', async () => {
    renderComponent();
    expect(await screen.findByText('Back')).toBeInTheDocument();
    expect(await screen.findByText('secondary_heading')).toBeInTheDocument();
    expect(await screen.findByText('login')).toBeInTheDocument();
  });

  test('Handles input change and validation for PIN', async () => {
    renderComponent();
    const pinInput = await screen.findByPlaceholderText('Enter PIN');
    fireEvent.change(pinInput, { target: { value: '123456' } });
    expect(pinInput.value).toBe('123456');
    fireEvent.blur(pinInput);
    await waitFor(() => {
      expect(screen.getByText('login')).toBeDisabled(); // Disabled until VID and CAPTCHA are provided
    });
  });

  test('Handles remember me checkbox interaction', async () => {
    renderComponent();
    const checkbox = await screen.findByLabelText('remember_me');
    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
  });

  test('Handles input change for non-password field (VID)', async () => {
    renderComponent();

    // Select input using its id
    const vidInput = await screen.findByRole('textbox', { name: /vid/i });

    // Trigger a value change (non-password input path)
    fireEvent.change(vidInput, { target: { value: 'test@example.com' } });

    expect(vidInput.value).toBe('test@example.com');

    // Trigger blur to validate
    fireEvent.blur(vidInput);

    // Button should still be disabled (PIN and CAPTCHA not yet done)
    const loginButton = screen.getByRole('button', { name: /login/i });
    expect(loginButton).toBeDisabled();
  });
});
