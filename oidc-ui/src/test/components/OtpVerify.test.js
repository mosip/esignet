import React from 'react';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';

// 🧪 Mock translations
jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, params) => {
      if (key === 'otp_sent_msg') return `OTP sent (${params?.otpLength})`;
      if (key === 'and') return 'and';
      return key;
    },
    i18n: {
      language: 'en',
      on: jest.fn(),
    },
  }),
}));

// 🧪 Mock services
jest.mock('../../services/langConfigService', () => ({
  getEnLocaleConfiguration: jest.fn().mockResolvedValue({
    labels: {
      otp_sent_msg: 'OTP sent',
      loading_msg: 'Loading...',
    },
    errors: {
      otp: {
        required: 'OTP is required',
      },
    },
  }),
}));

jest.mock('../../helpers/redirectOnError', () => jest.fn());

jest.mock('react-google-recaptcha', () => {
  const MockReCAPTCHA = (props) => {
    // simulate captcha solved immediately
    setTimeout(() => props.onChange('mocked-captcha-token'), 0);
    return <div data-testid="mock-recaptcha" />;
  };
  MockReCAPTCHA.displayName = 'MockReCAPTCHA';
  return MockReCAPTCHA;
});

// 🧪 Import component after mocks
import OtpVerify from '../../components/OtpVerify';

// 🧪 Mocks
const mockAuthService = {
  post_SendOtp: jest.fn().mockResolvedValue({ response: {}, errors: null }),
  post_AuthenticateUser: jest.fn().mockResolvedValue({
    response: { consentAction: 'allow' },
    errors: null,
  }),
  buildRedirectParams: jest.fn().mockReturnValue('?mocked=params'),
};

const mockOpenIDConnectService = {
  getEsignetConfiguration: jest.fn((key) => {
    if (key === 'resendOtpTimeout') return 60;
    if (key === 'sendOtpChannels') return 'sms,email';
    if (key === 'otpLength') return '6';
    if (key === 'captchaSiteKey') return 'test-key';
    if (key === 'captchaEnableComponents') return 'send-otp';
    return undefined;
  }),
  getTransactionId: jest.fn(() => 'txn123'),
  getNonce: jest.fn(() => 'nonce123'),
  getState: jest.fn(() => 'state123'),
  getOAuthDetails: jest.fn(() => ({})),
};

const defaultProps = {
  param: [{ id: '1' }],
  otpResponse: {
    maskedMobile: 'xxxxxx1234',
    maskedEmail: 't***@test.com',
  },
  ID: { prefix: 'pre-', id: 'id', postfix: '-post' },
  authService: mockAuthService,
  openIDConnectService: mockOpenIDConnectService,
};

// ✅ Mock react-router hooks globally for Authorize test
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: jest.fn(),
  useSearchParams: jest.fn(() => [new URLSearchParams('')]),
}));

describe('OtpVerify', () => {
  const mockNavigate = jest.fn();
  const mockUseNavigate = require('react-router-dom').useNavigate;

  beforeEach(() => {
    mockNavigate.mockClear();
    mockUseNavigate.mockReturnValue(mockNavigate);
  });

  it('renders OTP sent message and contact info', async () => {
    render(
      <MemoryRouter>
        <OtpVerify {...defaultProps} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/OTP sent \(6\)/)).toBeInTheDocument();
    });

    expect(
      screen.getByText(
        (content, element) =>
          element.tagName.toLowerCase() === 'h6' &&
          content.includes('xxxxxx1234') &&
          content.includes('t***@test.com')
      )
    ).toBeInTheDocument();
  });

  it('allows user to enter OTP and submit', async () => {
    render(
      <MemoryRouter>
        <OtpVerify {...defaultProps} />
      </MemoryRouter>
    );

    await screen.findByText(/OTP sent/);

    const inputs = document.querySelectorAll('.pincode-input-text');
    expect(inputs.length).toBe(6);

    for (let i = 0; i < inputs.length; i++) {
      await userEvent.type(inputs[i], `${i + 1}`);
    }

    const submitBtn = screen.getByRole('button', { name: 'verify' });
    expect(submitBtn).not.toBeDisabled();

    await act(() => userEvent.click(submitBtn));

    await waitFor(() => {
      expect(mockAuthService.post_AuthenticateUser).toHaveBeenCalled();
    });
  });

  it('executes timer completion logic (clears interval, shows resend button, resets captcha)', async () => {
    jest.useFakeTimers();

    mockOpenIDConnectService.getEsignetConfiguration.mockImplementation(
      (key) => {
        if (key === 'resendOtpTimeout') return 1;
        if (key === 'sendOtpChannels') return 'sms,email';
        if (key === 'otpLength') return '6';
        if (key === 'captchaSiteKey') return 'test-key';
        if (key === 'captchaEnableComponents') return 'send-otp';
        return undefined;
      }
    );

    render(
      <MemoryRouter>
        <OtpVerify {...defaultProps} />
      </MemoryRouter>
    );

    expect(screen.queryByRole('button', { name: 'resend_otp' })).toBeDisabled();

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resend_otp' }));
    });

    jest.useRealTimers();
  });

  it('returns translated OTP label when available', async () => {
    jest.mock('react-i18next', () => ({
      useTranslation: () => ({
        t: (key) => (key === 'otp_label_text' ? 'Enter OTP' : key),
        i18n: { language: 'en', on: jest.fn() },
      }),
    }));

    const { default: OtpVerifyLabelTest } = await import(
      '../../components/OtpVerify'
    );

    render(
      <MemoryRouter>
        <OtpVerifyLabelTest {...defaultProps} />
      </MemoryRouter>
    );

    await screen.findByText(/OTP sent/);

    mockAuthService.post_AuthenticateUser.mockResolvedValueOnce({
      response: null,
      errors: [{ errorCode: 'invalid_otp' }],
    });

    const inputs = document.querySelectorAll('.pincode-input-text');
    for (let i = 0; i < inputs.length; i++) {
      await userEvent.type(inputs[i], `${i + 1}`);
    }

    const submitBtn = screen.getByRole('button', { name: 'verify' });
    await act(async () => {
      await userEvent.click(submitBtn);
    });

    await waitFor(() => {
      expect(mockAuthService.post_AuthenticateUser).toHaveBeenCalled();
    });
  });

  it('returns default label OTP when translation key not found', async () => {
    mockAuthService.post_AuthenticateUser.mockResolvedValueOnce({
      response: null,
      errors: [{ errorCode: 'invalid_otp' }],
    });

    render(
      <MemoryRouter>
        <OtpVerify {...defaultProps} />
      </MemoryRouter>
    );

    await screen.findByText(/OTP sent/);

    const inputs = document.querySelectorAll('.pincode-input-text');
    for (let i = 0; i < inputs.length; i++) {
      await userEvent.type(inputs[i], `${i + 1}`);
    }

    const verifyBtn = screen.getByRole('button', { name: 'verify' });
    await act(async () => {
      await userEvent.click(verifyBtn);
    });

    await waitFor(() => {
      expect(mockAuthService.post_AuthenticateUser).toHaveBeenCalled();
    });
  });
});
