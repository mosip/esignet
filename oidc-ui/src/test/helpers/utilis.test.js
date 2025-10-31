import {
  encodeString,
  decodeHash,
  checkConfigProperty,
  getOauthDetailsHash,
  base64UrlDecode,
  getPollingConfig,
} from '../../helpers/utils';

describe('Utility functions', () => {
  describe('encodeString', () => {
    it('should encode an empty string', () => {
      expect(encodeString('')).toBe('');
    });
  });

  describe('decodeHash', () => {
    it('should decode a base64 string', () => {
      const original = 'Hello, world!';
      const encoded = Buffer.from(original).toString('base64');
      expect(decodeHash(encoded)).toBe(original);
    });

    it('should decode an empty base64 string', () => {
      expect(decodeHash('')).toBe('');
    });
  });

  describe('checkConfigProperty', () => {
    it('should return true if property exists in config', () => {
      const config = { foo: 'bar' };
      expect(checkConfigProperty(config, 'foo')).toBe(true);
    });

    it('should return false if property does not exist', () => {
      const config = { foo: 'bar' };
      expect(checkConfigProperty(config, 'baz')).toBe(false);
    });

    it('should return false if config is undefined', () => {
      expect(checkConfigProperty(undefined, 'foo')).toBe(false);
    });

    it('should return false if config is null', () => {
      expect(checkConfigProperty(null, 'foo')).toBe(false);
    });
  });

  describe('getOauthDetailsHash', () => {
    it('should generate a base64url-encoded SHA-256 hash', async () => {
      const value = {
        client_id: 'abc123',
        redirect_uri: 'https://example.com',
      };
      const hash = await getOauthDetailsHash(value);
      expect(typeof hash).toBe('string');
      expect(hash).not.toMatch(/[+=/]/); // should not contain +, /, or =
    });

    it('should return the same hash for the same input', async () => {
      const val = { test: 123 };
      const hash1 = await getOauthDetailsHash(val);
      const hash2 = await getOauthDetailsHash(val);
      expect(hash1).toBe(hash2);
    });
  });

  describe('base64UrlDecode', () => {
    it('should decode a base64url string', () => {
      const original = JSON.stringify({ name: 'John' });
      const base64 = Buffer.from(original).toString('base64');
      const base64url = base64
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .split('=')[0]; // ✅ safe way to strip padding
      const decoded = base64UrlDecode(base64url);
      expect(decoded).toBe(original);
    });

    it('should decode empty string to empty string', () => {
      expect(base64UrlDecode('')).toBe('');
    });
  });

  describe('getPollingConfig', () => {
    const originalEnv = process.env;
    const originalWindowEnv = window._env_;

    beforeEach(() => {
      process.env = { ...originalEnv };
      window._env_ = {};
    });

    afterEach(() => {
      process.env = originalEnv;
      window._env_ = originalWindowEnv;
    });

    it('should return default config in production', () => {
      delete process.env.NODE_ENV;
      const config = getPollingConfig();
      expect(typeof config.url).toBe('string');
      expect(config.interval).toBe(10000);
      expect(config.timeout).toBe(5000);
      expect(config.enabled).toBe(true);
    });

    it('should return config using window._env_', () => {
      window._env_ = {
        POLLING_URL: 'https://custom.com/health',
        POLLING_INTERVAL: '3000',
        POLLING_TIMEOUT: '2000',
        POLLING_ENABLED: 'false',
      };
      const config = getPollingConfig();
      expect(config.url).toBe('https://custom.com/health');
      expect(config.interval).toBe(3000);
      expect(config.timeout).toBe(2000);
      expect(config.enabled).toBe(false);
    });

    it('should use REACT_APP_ESIGNET_API_URL in development mode', () => {
      process.env.NODE_ENV = 'development';
      process.env.REACT_APP_ESIGNET_API_URL = 'http://localhost:3000';
      const config = getPollingConfig();
      expect(config.url).toBe('http://localhost:3000/actuator/health');
    });
  });
});
