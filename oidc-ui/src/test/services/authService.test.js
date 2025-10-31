import authService from '../../services/authService';
import { ApiService } from '../../services/api.service';
import localStorageService from '../../services/local-storageService';
import { CSRF } from '../../constants/routes';

// ✅ Mock global Buffer before tests
global.Buffer = {
  from: jest.fn((input) => ({
    toString: jest.fn((encoding) => {
      if (encoding === 'base64') return `mockedBase64StringOf(${input})`;
      return `mockedString(${input})`;
    }),
  })),
};

jest.mock('../../services/api.service');
jest.mock('../../services/local-storageService');

describe('authService', () => {
  let service;
  const mockOpenIDConnectService = {
    getOauthDetailsHash: jest.fn().mockResolvedValue('mockedHash'),
    getTransactionId: jest.fn().mockResolvedValue('mockedTransactionId'),
  };

  beforeEach(() => {
    service = new authService(mockOpenIDConnectService);
    jest.clearAllMocks();
    localStorageService.getCookie.mockReturnValue('mockedXsrfToken');
  });

  it('post_AuthenticateUser should call ApiService.post with correct parameters and headers', async () => {
    ApiService.post.mockResolvedValue({ data: 'authenticateResponse' });

    const result = await service.post_AuthenticateUser(
      'tx123',
      'user123',
      [],
      'captchaToken',
      'mockedHash'
    );

    expect(ApiService.post).toHaveBeenCalled();
    expect(result).toBe('authenticateResponse');
  });

  it('post_OauthDetails_v2 should call ApiService.post correctly', async () => {
    ApiService.post.mockResolvedValue({ data: 'oauthDetailsResponse' });
    const result = await service.post_OauthDetails_v2({ foo: 'bar' });
    expect(result).toBe('oauthDetailsResponse');
  });

  it('post_OauthDetails_v3 should call ApiService.post correctly', async () => {
    ApiService.post.mockResolvedValue({ data: 'oauthDetailsV3Response' });
    const result = await service.post_OauthDetails_v3({ baz: 'qux' });
    expect(result).toBe('oauthDetailsV3Response');
  });

  it('post_OauthDetails_v3 should handle invalid parameters', async () => {
    ApiService.post.mockRejectedValue(new Error('Invalid Parameters'));
    await expect(service.post_OauthDetails_v3({})).rejects.toThrow(
      'Invalid Parameters'
    );
  });

  it('post_SendOtp should call ApiService.post with expected params', async () => {
    ApiService.post.mockResolvedValue({ data: 'otpResponse' });
    const result = await service.post_SendOtp(
      'tx',
      'uid',
      ['phone'],
      'captcha'
    );
    expect(result).toBe('otpResponse');
  });

  it('resume should post to RESUME with correct headers', async () => {
    ApiService.post.mockResolvedValue({ data: 'resumeData' });
    const result = await service.resume('tx');
    expect(result).toBe('resumeData');
  });

  it('buildRedirectParams returns encoded values', () => {
    const result = service.buildRedirectParams(
      'n1',
      's2',
      { foo: 'bar' },
      'approve'
    );
    expect(result).toContain('nonce=n1');
    expect(result).toContain('state=s2');
    expect(result).toContain('consentAction=approve');
  });

  it('getAuthorizeQueryParam handles error gracefully', () => {
    jest.spyOn(Storage.prototype, 'getItem').mockImplementationOnce(() => {
      throw new Error('LocalStorage Error');
    });
    expect(() => service.getAuthorizeQueryParam()).toThrow(
      'LocalStorage Error'
    );
  });

  it('storeQueryParam should encode query and store it in localStorage', () => {
    // Mock Buffer.from to simulate encoding
    const mockBufferFrom = jest.spyOn(Buffer, 'from').mockReturnValue({
      toString: jest.fn(() => 'mockedBase64String'),
    });

    // Mock localStorage.setItem
    const mockSetItem = jest
      .spyOn(window.localStorage.__proto__, 'setItem')
      .mockImplementation(() => {});

    // Execute method
    service.storeQueryParam('nonce=abc&state=xyz');

    // Cleanup mocks
    mockBufferFrom.mockRestore();
    mockSetItem.mockRestore();
  });

  it('should build full redirect params string with all fields and base64-encoded oauthResponse', () => {
    // 🔧 Temporarily restore real Buffer
    const realBuffer = jest.requireActual('buffer').Buffer;
    const mockDate = new Date(1730280000000);
    jest.spyOn(global, 'Date').mockImplementation(() => mockDate);

    const buildParam = {
      nonce: 'abc123',
      state: 'xyz789',
      ui_locales: 'en',
      consentAction: 'approve',
      oauthResponse: { success: true, code: 'ok' },
    };

    const result = service.buildRedirectParamsV2(buildParam);

    const expectedAuthTime = Math.floor(mockDate.getTime() / 1000);
    const expectedOauthResponse = realBuffer
      .from(JSON.stringify(buildParam.oauthResponse))
      .toString('base64');

    expect(result).toBe(
      `?nonce=abc123&state=xyz789&ui_locales=en&consentAction=approve&authenticationTime=${expectedAuthTime}#${expectedOauthResponse}`
    );

    jest.restoreAllMocks();
  });

  it('post_ParOauthDetails should call ApiService.post with correct params', async () => {
    ApiService.post.mockResolvedValue({ data: 'parResponse' });

    const params = { clientId: 'client123', requestUri: 'https://req.uri' };
    const result = await service.post_ParOauthDetails(params);

    expect(ApiService.post).toHaveBeenCalledWith(
      expect.stringContaining('par-oauth'), // matches PAR_OAUTH_DETAIL
      expect.objectContaining({ request: params }),
      expect.objectContaining({
        headers: { 'Content-Type': 'application/json' },
      })
    );
    expect(result).toBe('parResponse');
  });

  it('post_AuthCode should call ApiService.post with correct headers and body', async () => {
    ApiService.post.mockResolvedValue({ data: 'authCodeResponse' });

    const result = await service.post_AuthCode(
      'txABC',
      ['claim1'],
      ['scope1'],
      'mockedHash'
    );

    expect(ApiService.post).toHaveBeenCalledWith(
      expect.stringContaining('auth-code'), // matches AUTHCODE
      expect.objectContaining({
        request: expect.objectContaining({
          transactionId: 'txABC',
          acceptedClaims: ['claim1'],
          permittedAuthorizeScopes: ['scope1'],
        }),
      }),
      expect.objectContaining({
        headers: expect.objectContaining({
          'oauth-details-hash': 'mockedHash',
          'oauth-details-key': 'txABC',
        }),
      })
    );
    expect(result).toBe('authCodeResponse');
  });

  it('getClaimDetails should call ApiService.get with correct headers', async () => {
    ApiService.get.mockResolvedValue({ data: 'claimDetailsResponse' });

    const result = await service.getClaimDetails();
    expect(result).toBe('claimDetailsResponse');
  });

  it('prepareSignupRedirect should call ApiService.post correctly', async () => {
    ApiService.post.mockResolvedValue({ data: 'signupRedirectResponse' });

    const result = await service.prepareSignupRedirect('tx001', 'signup-path');

    expect(result).toBe('signupRedirectResponse');
  });
});
