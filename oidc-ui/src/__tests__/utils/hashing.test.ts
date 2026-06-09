import { describe, it, expect } from 'vitest';
import { sha256Base64Url } from '../../utils/hashing';

describe('hashing utilities', () => {
  describe('sha256Base64Url', () => {
    it('produces a base64url-encoded SHA-256 hash', async () => {
      const result = await sha256Base64Url({ test: 'value' });
      expect(typeof result).toBe('string');
      expect(result.length).toBeGreaterThan(0);
    });

    it('does not contain base64 padding', async () => {
      const result = await sha256Base64Url({ key: 'data' });
      expect(result).not.toContain('=');
    });

    it('does not contain + or / characters', async () => {
      const result = await sha256Base64Url({ lots: 'of data here' });
      expect(result).not.toContain('+');
      expect(result).not.toContain('/');
    });

    it('produces consistent output for same input', async () => {
      const input = { consistent: true };
      const result1 = await sha256Base64Url(input);
      const result2 = await sha256Base64Url(input);
      expect(result1).toBe(result2);
    });

    it('produces different output for different inputs', async () => {
      const result1 = await sha256Base64Url({ a: 1 });
      const result2 = await sha256Base64Url({ a: 2 });
      expect(result1).not.toBe(result2);
    });
  });
});
