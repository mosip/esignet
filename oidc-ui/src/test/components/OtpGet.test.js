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
import { render, screen } from '@testing-library/react';
import OtpGet from '../../components/OtpGet';

// Only allowed globals inside jest.mock factories
jest.mock('../../common/LoadingIndicator', () => {
  return function MockLoadingIndicator() {
    return <div data-testid="mock-loading-indicator" />;
  };
});
jest.mock('../../components/LoginIDOptions', () => {
  return function MockLoginIDOptions(props) {
    // Set login ID asynchronously to mimic real behavior
    setTimeout(() => {
      if (props.currentLoginID) {
        props.currentLoginID({
          id: 'testId',
          input_label: 'Test Label',
          input_placeholder: 'Test Placeholder',
          regex: '^[0-9]{6}$',
          prefixes: [],
        });
      }
    }, 0);
    return <div data-testid="mock-login-id-options" />;
  };
});
jest.mock('../../components/InputWithImage', () => {
  return function MockInputWithImage() {
    return <input data-testid="mock-input-with-image" />;
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

const mockAuthService = {
  post_SendOtp: jest.fn().mockResolvedValue({ response: {}, errors: [] }),
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
      () => new Promise(() => {}) // Never resolves — simulates loading
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

    // Restore mock to avoid affecting other tests
    mockLangConfigService.getEnLocaleConfiguration = originalMock;
  });
});
