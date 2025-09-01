import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ClaimDetails from '../../components/ClaimDetails';
import openIDConnectService from '../../services/openIDConnectService';
import authService from '../../services/authService';
import { useTranslation } from 'react-i18next';
import { configurationKeys } from '../../constants/clientConstants';

// ---------- MOCK DATA ----------
const mockOAuthDetails = {
  essentialClaims: ['Name', 'Birthdate'],
  voluntaryClaims: ['Phone Number', 'Gender'],
  clientName: { '@none': 'Healthservice' },
  logoUrl: 'logoUrl',
};

const mockAuthService = {
  getClaimDetails: jest.fn(),
  prepareSignupRedirect: jest.fn(),
};

const mockResponse = {
  response: {
    claimStatus: [
      { claim: 'Name', available: true, verified: true },
      { claim: 'Gender', available: false, verified: false },
    ],
    profileUpdateRequired: true,
    consentAction: 'CAPTURE',
  },
  errors: [],
};

// ---------- MOCKS ----------
jest.mock('../../services/openIDConnectService');
jest.mock('../../services/authService');
jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
}));

// ---------- LOCATION MOCK ----------
const originalLocation = window.location;

beforeAll(() => {
  delete window.location;
  window.location = {
    href: 'http://localhost?state=mockState&nonce=mockNonce&ui_locales=en#eyJjbGllbnROYW1lIjp7IkBub25lIjoiVGVzdCBDbGllbnQifSwibG9nb1VybCI6Ii9sb2dvLnBuZyJ9',
    search: '?state=mockState&nonce=mockNonce&ui_locales=en',
    hash: '#eyJjbGllbnROYW1lIjp7IkBub25lIjoiVGVzdCBDbGllbnQifSwibG9nb1VybCI6Ii9sb2dvLnBuZyJ9',
    replace: jest.fn(),
  };
});

afterAll(() => {
  window.location = originalLocation;
});

beforeEach(() => {
  openIDConnectService.mockImplementation(() => ({
    getOAuthDetails: jest.fn(() => mockOAuthDetails),
    getTransactionId: jest.fn(() => 'transactionId'),
    getEsignetConfiguration: jest.fn((key) =>
      key === configurationKeys.eKYCStepsConfig ? 'eKYCStepsURL' : null
    ),
    getRedirectUri: jest.fn(() => 'mockRedirectUri'),
  }));

  authService.mockImplementation(() => mockAuthService);

  useTranslation.mockImplementation((ns, { keyPrefix }) => ({
    t: (key) =>
      (keyPrefix
        ? {
            'consentDetails.proceed': 'Proceed',
            'consentDetails.cancel': 'Cancel',
            'consentDetails.header': 'Header',
            'consentDetails.essential_claims': 'Essential Claims',
            'consentDetails.voluntary_claims': 'Voluntary Claims',
            'consentDetails.popup_header': 'Attention!',
            'consentDetails.popup_body': 'Are you sure you want to cancel?',
            'consentDetails.stay_btn': 'Stay',
            'consentDetails.discontinue_btn': 'Discontinue',
            'consentDetails.Email': 'Email',
            'consentDetails.Name': 'Name',
            'consentDetails.verified': 'verified',
            'consentDetails.not-verified': 'not-verified',
            'consentDetails.available': 'available',
            'consentDetails.not-available': 'not-available',
            'consentDetails.message': 'Footer message',
            'consentDetails.logo_alt': 'Alt logo',
            'errors.some_error_code': 'Some error',
            'errors.signup_error': 'Signup failed',
            'errors.authorization_failed_msg': 'Authorization failed',
            consent_details_rejected: 'Consent rejected',
          }[`${keyPrefix}.${key}`]
        : key) || key,
    i18n: { language: 'en' },
  }));

  jest.spyOn(window, 'atob').mockImplementation(() =>
    JSON.stringify({
      clientName: { '@none': 'Test Client' },
      logoUrl: '/logo.png',
      essentialClaims: ['Email'],
      voluntaryClaims: ['Name'],
    })
  );
});

afterEach(() => {
  jest.clearAllMocks();
});

// ---------- TEST CASES ----------

test('initial loading state shows spinner', () => {
  render(<ClaimDetails />);
  expect(screen.getByText('Loading...')).toBeInTheDocument();
});

test('handles consentAction: NOCAPTURE with redirect success', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue({
    response: {
      claimStatus: [],
      profileUpdateRequired: false,
      consentAction: 'NOCAPTURE',
    },
    errors: [],
  });

  mockAuthService.post_AuthCode = jest.fn().mockResolvedValue({
    response: {
      redirectUri: 'http://example.com',
      state: 'mockState',
      code: 'mockCode',
    },
    errors: [],
  });

  render(<ClaimDetails />);
  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalledWith(
      expect.stringContaining('http://example.com?state=mockState')
    );
  });
});

test('handles post_AuthCode error', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue({
    response: {
      claimStatus: [],
      profileUpdateRequired: false,
      consentAction: 'NOCAPTURE',
    },
    errors: [],
  });

  mockAuthService.post_AuthCode = jest.fn().mockResolvedValue({
    errors: [{ errorCode: 'some_error_code' }],
  });

  render(<ClaimDetails />);
  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalled();
  });
});

test('Proceed triggers prepareSignupRedirect success', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue({
    response: {
      claimStatus: [],
      profileUpdateRequired: true,
    },
    errors: [],
  });

  mockAuthService.prepareSignupRedirect.mockResolvedValue({
    response: {
      idToken: 'token123',
      redirectUri: 'https://redirect.com',
      code: 'abc',
      state: 'state1',
    },
    errors: [],
  });

  render(<ClaimDetails />);
  await waitFor(() => screen.getByText('Proceed'));
  fireEvent.click(screen.getByText('Proceed'));
  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalledWith(
      expect.stringContaining('eKYCStepsURL')
    );
  });
});

test('Proceed triggers prepareSignupRedirect error', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue({
    response: {
      claimStatus: [],
      profileUpdateRequired: true,
    },
    errors: [],
  });

  mockAuthService.prepareSignupRedirect.mockResolvedValue({
    errors: [{ errorCode: 'signup_error' }],
  });

  render(<ClaimDetails />);
  await waitFor(() => screen.getByText('Proceed'));
  fireEvent.click(screen.getByText('Proceed'));
  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalled();
  });
});

test('Cancel opens modal and clicking Stay closes it', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);

  render(<ClaimDetails />);
  await waitFor(() => screen.getByText('Cancel'));

  fireEvent.click(screen.getByText('Cancel'));
  expect(screen.getByText('Attention!')).toBeInTheDocument();

  fireEvent.click(screen.getByText('Stay'));
  await waitFor(() => {
    expect(screen.queryByText('Attention!')).not.toBeInTheDocument();
  });
});

test('Discontinue redirects on cancel modal', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);

  render(<ClaimDetails />);
  fireEvent.click(await screen.findByText('Cancel'));
  fireEvent.click(await screen.findByText('Discontinue'));

  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalled();
  });
});

test('getClaimDetails with error redirects', async () => {
  mockAuthService.getClaimDetails.mockResolvedValue({
    errors: [{ errorCode: 'some_error_code' }],
  });

  render(<ClaimDetails />);
  await waitFor(() => {
    expect(window.location.replace).toHaveBeenCalled();
  });
});

test('hides #language_dropdown if present', async () => {
  const el = document.createElement('div');
  el.id = 'language_dropdown';
  el.style.display = 'block';
  document.body.appendChild(el);

  mockAuthService.getClaimDetails.mockResolvedValue(mockResponse);
  render(<ClaimDetails />);
  await waitFor(() => {
    expect(el.style.display).toBe('none');
  });

  document.body.removeChild(el);
});
