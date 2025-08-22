import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import Background from '../../components/Background';
import { configurationKeys } from '../../constants/clientConstants';
import * as utils from '../../helpers/utils';
import { useTranslation } from 'react-i18next';

// Mocks
jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
  Trans: ({ i18nKey, defaults }) => <span>{defaults || i18nKey}</span>,
}));

jest.mock('../../helpers/utils', () => ({
  checkConfigProperty: jest.fn(),
}));

const defaultProps = {
  heading: 'Test Heading',
  subheading: 'test_subheading',
  clientLogoPath: '/logo.png',
  clientName: 'Test Client',
  component: <div data-testid="custom-component">Custom</div>,
  oidcService: {
    getEsignetConfiguration: jest.fn(),
  },
  authService: {
    getAuthorizeQueryParam: jest.fn(() => 'mock-query'),
  },
};

beforeEach(() => {
  jest.clearAllMocks();

  useTranslation.mockReturnValue({
    t: (key) => key,
    i18n: {
      language: 'en',
    },
  });
});

test('renders with all props', () => {
  utils.checkConfigProperty.mockReturnValue(false);

  defaultProps.oidcService.getEsignetConfiguration.mockImplementation(
    () => ({})
  );

  render(<Background {...defaultProps} />);

  expect(screen.getByText('Test Heading')).toBeInTheDocument();
  expect(screen.getByText('test_subheading')).toBeInTheDocument();
  expect(screen.getByAltText('Test Client')).toBeInTheDocument();
  expect(screen.getByAltText('logo_alt')).toBeInTheDocument();
  expect(screen.getByTestId('custom-component')).toBeInTheDocument();
  expect(screen.queryByText('noAccount')).not.toBeInTheDocument(); // signupBanner false
});

test('renders signupBanner from clientAdditionalConfig', () => {
  utils.checkConfigProperty.mockImplementationOnce(() => true); // For clientAdditionalConfig

  defaultProps.oidcService.getEsignetConfiguration.mockImplementation((key) => {
    if (key === configurationKeys.additionalConfig) {
      return { [configurationKeys.signupBannerRequired]: true };
    }
    if (key === configurationKeys.signupConfig) {
      return { [configurationKeys.signupURL]: 'https://signup.com' };
    }
    return {};
  });

  render(<Background {...defaultProps} />);

  expect(screen.getByText('noAccount')).toBeInTheDocument();
});

test('does not show banner if config check fails', () => {
  utils.checkConfigProperty
    .mockImplementationOnce(() => false)
    .mockImplementationOnce(() => false);

  defaultProps.oidcService.getEsignetConfiguration.mockImplementation(() => {
    return {};
  });

  render(<Background {...defaultProps} />);
  expect(screen.queryByText('noAccount')).not.toBeInTheDocument();
});

test('handleSignup clears onbeforeunload', () => {
  utils.checkConfigProperty.mockReturnValue(true);

  defaultProps.oidcService.getEsignetConfiguration.mockImplementation((key) => {
    if (key === configurationKeys.additionalConfig) {
      return { [configurationKeys.signupBannerRequired]: true };
    }
    if (key === configurationKeys.signupConfig) {
      return { [configurationKeys.signupURL]: 'https://signup.com' };
    }
    return {};
  });

  window.onbeforeunload = jest.fn();

  render(<Background {...defaultProps} />);
  const signupLink = screen.getByRole('link', {
    name: 'signup_for_unified_login',
  });
  fireEvent.click(signupLink);
  expect(window.onbeforeunload).toBe(null);
});
