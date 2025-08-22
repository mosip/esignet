import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../../pages/Login';

// Mock TextEncoder and TextDecoder
global.TextEncoder = require('util').TextEncoder;
global.TextDecoder = require('util').TextDecoder;

// Mock secure-biometric-interface-integrator
jest.mock('secure-biometric-interface-integrator', () => ({
  init: jest.fn(),
  propChange: jest.fn(),
}));

// Mock jose
jest.mock('jose', () => ({
  decodeJwt: jest.fn(),
  jwtVerify: jest.fn(),
}));

// Mocks for langConfigService
jest.mock('../../services/langConfigService', () => ({
  getLangCodeMapping: jest.fn(() =>
    Promise.resolve({
      eng: 'en',
    })
  ),
}));

// Mock data and service for valid parsing
const mockDecodedOAuth = {
  logoUrl: 'https://example.com/logo.png',
  clientName: { eng: 'Test Client', '@none': 'Fallback Client' },
  purpose: {
    type: 'login',
    title: { eng: 'Welcome', '@none': 'Welcome Default' },
    subTitle: { eng: 'Login to continue', '@none': 'Default Subtitle' },
  },
  authFactorList: [{ type: 'OTP' }],
};

jest.mock('../../services/openIDConnectService', () => {
  return jest.fn().mockImplementation(() => ({
    getOAuthDetails: () => mockDecodedOAuth,
    getPurpose: () => mockDecodedOAuth.purpose,
    getAuthFactorList: () => mockDecodedOAuth.authFactorList,
    getEsignetConfiguration: () => null,
  }));
});

describe('LoginPage', () => {
  const encodedOAuth = Buffer.from(JSON.stringify(mockDecodedOAuth)).toString(
    'base64'
  );

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hash: encodedOAuth,
        search: '?nonce=testnonce&state=teststate',
      },
    });
  });

  it('renders without crashing', () => {
    const { container } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
    expect(container).toBeInTheDocument();
  });

  it('renders parsing error alert when parsing fails', () => {
    // override to simulate failure
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hash: '!!!not_base64!!!',
        search: '?nonce=x&state=y',
      },
    });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    const alert = screen.getByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent(/parsing_error_msg/i);
  });

  it('renders background image when parsing fails', () => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hash: '!!!not_base64!!!',
        search: '?nonce=x&state=y',
      },
    });

    const { getByAltText } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    const img = getByAltText('backgroud_image_alt');
    expect(img).toBeInTheDocument();
  });
});
