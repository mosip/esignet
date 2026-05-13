/**
 * Generates a base64url-encoded SHA-256 hash of the given value using the Web Crypto API.
 * Replaces the old crypto-js implementation.
 */
export async function sha256Base64Url(value: unknown): Promise<string> {
  const data = new TextEncoder().encode(JSON.stringify(value));
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = new Uint8Array(hashBuffer);

  // Convert to base64
  const binary = Array.from(hashArray, (byte) => String.fromCharCode(byte)).join('');
  const base64 = btoa(binary);

  // Convert to base64url
  return base64
    .replace(/=+$/, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}
