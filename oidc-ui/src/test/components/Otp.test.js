import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Otp from '../../components/Otp';

jest.mock('../../components/OtpGet', () => ({
  __esModule: true,
  default: ({ onOtpSent }) => (
    <div data-testid="otp-get">
      <button
        onClick={() => onOtpSent('123', 'responseData', 'loginID', 'IN')}
        data-testid="send-otp"
      >
        Send OTP
      </button>
    </div>
  ),
}));

jest.mock('../../components/OtpVerify', () => ({
  __esModule: true,
  default: ({ ID, otpResponse, loginID, selectedCountry }) => (
    <div data-testid="otp-verify">
      ID: {ID}, OTP: {otpResponse}, LoginID: {loginID}, Country:{' '}
      {selectedCountry}
    </div>
  ),
}));

jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, options) =>
      typeof options === 'object' && options?.currentID
        ? `${key} (${options.currentID})`
        : key,
  }),
}));

describe('Otp Component', () => {
  const defaultProps = {
    param: {},
    authService: { dummy: true },
    openIDConnectService: {
      getEsignetConfiguration: jest.fn(() => [{ id: 'phone' }]),
    },
    backButtonDiv: <div data-testid="back-div">Back</div>,
    secondaryHeading: 'secondary.heading',
    i18nKeyPrefix: 'otp',
  };

  test('renders OtpVerify after OTP is sent', async () => {
    render(<Otp {...defaultProps} />);
    fireEvent.click(screen.getByTestId('send-otp'));

    await waitFor(() => {
      expect(screen.getByTestId('otp-verify')).toBeInTheDocument();
    });

    expect(screen.getByText(/ID: 123/)).toBeInTheDocument();
    expect(screen.getByText(/OTP: responseData/)).toBeInTheDocument();
    expect(screen.getByText(/LoginID: loginID/)).toBeInTheDocument();
    expect(screen.getByText(/Country: IN/)).toBeInTheDocument();
  });

  test('clicking back button switches to OtpGet', async () => {
    render(<Otp {...defaultProps} />);
    fireEvent.click(screen.getByTestId('send-otp'));

    await waitFor(() => {
      expect(screen.getByTestId('otp-verify')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /left_arrow_icon/i }));
    expect(screen.getByTestId('otp-get')).toBeInTheDocument();
  });

  test('multiple login ID options - secondaryHeading shown without currentID', () => {
    const multiProps = {
      ...defaultProps,
      openIDConnectService: {
        getEsignetConfiguration: jest.fn(() => [
          { id: 'phone' },
          { id: 'email' },
        ]),
      },
    };

    render(<Otp {...multiProps} />);
    expect(screen.getByText('secondary.heading')).toBeInTheDocument();
  });
});
