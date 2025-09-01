import React from 'react';
import { render, screen } from '@testing-library/react';
import ErrorIndicator from '../../common/ErrorIndicator';
import { useTranslation } from 'react-i18next';
import { useLocation, useSearchParams } from 'react-router-dom';

// Mocks
jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
}));
jest.mock('react-router-dom', () => ({
  useLocation: jest.fn(),
  useSearchParams: jest.fn(),
}));

// Mock Buffer for base64 decoding
global.Buffer = require('buffer').Buffer;

describe('ErrorIndicator', () => {
  let tMock;
  let setSearchParamsMock;

  beforeEach(() => {
    tMock = jest.fn((key, fallback) => {
      if (key === 'invalid_transaction') return 'Invalid transaction';
      if (key === 'link_code_limit_reached') return 'Link code limit reached';
      if (key === 'prefix') return 'Prefix';
      if (key === 'custom_error') return 'Custom error';
      return fallback || key;
    });
    useTranslation.mockReturnValue({ t: tMock });
    setSearchParamsMock = jest.fn();
    jest.clearAllMocks();
  });

  it('renders error message with prefix and customClass', () => {
    useSearchParams.mockReturnValue([{ get: jest.fn() }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash: '' });

    render(
      <ErrorIndicator
        prefix="prefix"
        errorCode="custom_error"
        defaultMsg="Default error"
        customClass="custom-class"
      />
    );
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Prefix: Custom error');
    expect(alert.className).toContain('custom-class');
  });

  it('renders error message without prefix', () => {
    useSearchParams.mockReturnValue([{ get: jest.fn() }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash: '' });

    render(
      <ErrorIndicator errorCode="custom_error" defaultMsg="Default error" />
    );
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Custom error');
  });

  it('renders error message with fallback if translation not found', () => {
    tMock.mockImplementation((key, fallback) => fallback || key);
    useSearchParams.mockReturnValue([{ get: jest.fn() }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash: '' });

    render(
      <ErrorIndicator errorCode="unknown_error" defaultMsg="Fallback error" />
    );
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Fallback error');
  });

  it('renders errorCode as fallback if defaultMsg not provided', () => {
    tMock.mockImplementation((key, fallback) => fallback || key);
    useSearchParams.mockReturnValue([{ get: jest.fn() }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash: '' });

    render(<ErrorIndicator errorCode="unknown_error" />);
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('unknown_error');
  });

  it('redirects if errorCode is invalid_transaction and all params present', () => {
    const nonce = 'nonce123';
    const state = 'state456';
    const redirectUri = 'https://redirect.com/callback';
    const oAuthDetails = { redirectUri };
    const hash = Buffer.from(JSON.stringify(oAuthDetails)).toString('base64');

    const getMock = jest.fn().mockImplementation((key) => {
      if (key === 'nonce') return nonce;
      if (key === 'state') return state;
      return null;
    });

    useSearchParams.mockReturnValue([{ get: getMock }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash });

    delete window.location;
    window.location = { replace: jest.fn() };
    window.onbeforeunload = jest.fn();

    render(
      <ErrorIndicator
        errorCode="invalid_transaction"
        defaultMsg="Default error"
      />
    );

    expect(window.location.replace).toHaveBeenCalled();
    const calledUrl = window.location.replace.mock.calls[0][0];
    expect(calledUrl).toContain(redirectUri);
    expect(calledUrl).toContain('nonce=nonce123');
    expect(calledUrl).toContain('state=state456');
    expect(calledUrl).toContain('error_description=Invalid transaction');
    expect(calledUrl).toContain('error=invalid_transaction');
  });

  it('returns nothing if errorCode is invalid_transaction but no hash', () => {
    useSearchParams.mockReturnValue([{ get: jest.fn() }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash: '' });

    // window.location.replace should not be called
    delete window.location;
    window.location = { replace: jest.fn() };

    const { container } = render(
      <ErrorIndicator
        errorCode="invalid_transaction"
        defaultMsg="Default error"
      />
    );
    expect(window.location.replace).not.toHaveBeenCalled();
    expect(container.firstChild).toBeNull();
  });

  it('returns nothing if errorCode is invalid_transaction but no redirectUri', () => {
    const nonce = 'nonce123';
    const state = 'state456';
    const oAuthDetails = {}; // no redirectUri
    const hash = Buffer.from(JSON.stringify(oAuthDetails)).toString('base64');

    const getMock = jest.fn().mockImplementation((key) => {
      if (key === 'nonce') return nonce;
      if (key === 'state') return state;
      return null;
    });

    useSearchParams.mockReturnValue([{ get: getMock }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash });

    delete window.location;
    window.location = { replace: jest.fn() };

    const { container } = render(
      <ErrorIndicator
        errorCode="invalid_transaction"
        defaultMsg="Default error"
      />
    );
    expect(window.location.replace).not.toHaveBeenCalled();
    expect(container.firstChild).toBeNull();
  });

  it('uses i18nKeyPrefix prop', () => {
    useSearchParams.mockReturnValue([{ get: jest.fn() }, setSearchParamsMock]);
    useLocation.mockReturnValue({ hash: '' });

    render(
      <ErrorIndicator errorCode="custom_error" i18nKeyPrefix="customErrors" />
    );
    expect(useTranslation).toHaveBeenCalledWith('translation', {
      keyPrefix: 'customErrors',
    });
  });
});
