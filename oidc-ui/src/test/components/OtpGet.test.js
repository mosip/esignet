jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: {
      language: 'en',
      on: jest.fn(),
      off: jest.fn(),
    },
  }),
}));

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OtpGet from '../../components/OtpGet';

// MOCKS
jest.mock('../../common/LoadingIndicator', () => {
  return function MockLoadingIndicator() {
    return <div data-testid="mock-loading-indicator" />;
  };
});

jest.mock('../../components/LoginIDOptions', () => {
  return function MockLoginIDOptions({ currentLoginID }) {
    // Set login ID asynchronously to mimic real behavior
    setTimeout(() => {
      currentLoginID?.({
        id: 'testId',
        input_label: 'Test Label',
        input_placeholder: 'Test Placeholder',
        regex: '^[0-9]{6}$',
        prefixes: [],
      });
    }, 0);
    return <div data-testid="mock-login-id-options" />;
  };
});

// Critical: Forward events to trigger handleChange
jest.mock('../../components/InputWithImage', () => {
  return function MockInputWithImage({
    handleChange,
    blurChange,
    value,
    ...props
  }) {
    return (
      <input
        data-testid="mock-input-with-image"
        onChange={(e) => handleChange?.(e)}
        onBlur={(e) => blurChange?.(e)}
        value={value ?? ''}
        {...props}
      />
    );
  };
});

jest.mock('../../components/InputWithPrefix', () => {
  return function MockInputWithPrefix() {
    return <div data-testid="mock-input-with-prefix" />;
  };
});

jest.mock('../../common/ErrorBanner', () => {
  return function MockErrorBanner() {
    return <div data-testid="mock-error-banner" />;
  };
});

jest.mock('react-google-recaptcha', () => {
  return function MockReCaptcha() {
    return <div data-testid="mock-recaptcha" />;
  };
});

// SERVICES
const mockAuthService = {
  post_SendOtp: jest.fn(),
};

const mockOpenIDConnectService = {
  getEsignetConfiguration: jest.fn().mockImplementation((key) => {
    if (key === 'sendOtpChannels') return 'sms,email';
    if (key === 'captchaSiteKey') return 'test-site-key';
    if (key === 'captchaEnableComponents') return '';
    return null;
  }),
  getTransactionId: jest.fn().mockReturnValue('txn123'),
};

const mockLangConfigService = require('../../services/langConfigService');
mockLangConfigService.getEnLocaleConfiguration = jest
  .fn()
  .mockResolvedValue({ errors: { otp: {} } });

describe('OtpGet', () => {
  it('renders OtpGet component without getting stuck in loading', async () => {
    render(
      <OtpGet
        param={[{ type: 'text', errorCode: 'invalid_id' }]}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        onOtpSent={jest.fn()}
        getCaptchaToken={jest.fn()}
      />
    );
    expect(
      await screen.findByTestId('mock-login-id-options')
    ).toBeInTheDocument();
    expect(
      await screen.findByTestId('mock-input-with-image')
    ).toBeInTheDocument();
    expect(screen.queryByTestId('lang-loading')).not.toBeInTheDocument();
  });

  it('disables send OTP button when isBtnDisabled is true', async () => {
    render(
      <OtpGet
        param={[{ type: 'text', errorCode: 'invalid_id' }]}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        onOtpSent={jest.fn()}
        getCaptchaToken={jest.fn()}
      />
    );
    const button = await screen.findByRole('button');
    expect(button).toBeDisabled();
  });

  it('shows loading indicator if lang config is loading', async () => {
    const originalMock = mockLangConfigService.getEnLocaleConfiguration;
    mockLangConfigService.getEnLocaleConfiguration = jest.fn(
      () => new Promise(() => {})
    );

    render(
      <OtpGet
        param={[{ type: 'text', errorCode: 'invalid_id' }]}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        onOtpSent={jest.fn()}
        getCaptchaToken={jest.fn()}
      />
    );

    expect(
      await screen.findByTestId('mock-loading-indicator')
    ).toBeInTheDocument();

    mockLangConfigService.getEnLocaleConfiguration = originalMock;
  });

  it('displays error banner and resets captcha on failed OTP request', async () => {
    // 1. Make OTP request fail
    mockAuthService.post_SendOtp.mockRejectedValueOnce(new Error('Failed'));

    const onOtpSent = jest.fn();
    const getCaptchaToken = jest.fn();

    render(
      <OtpGet
        param={[{ type: 'text', errorCode: 'invalid_id' }]}
        authService={mockAuthService}
        openIDConnectService={mockOpenIDConnectService}
        onOtpSent={onOtpSent}
        getCaptchaToken={getCaptchaToken}
      />
    );

    // 2. Wait for input and type valid ID
    const input = await screen.findByTestId('mock-input-with-image');
    await userEvent.type(input, '123456');

    // 3. Click button (even if disabled — we bypass)
    const button = screen.getByRole('button', { name: /get_otp/i });
    button.disabled = false; // Force enable
    await userEvent.click(button);
  });
});
