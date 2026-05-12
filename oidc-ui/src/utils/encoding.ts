/**
 * Encodes a string to base64url format (RFC 4648 Section 5).
 */
export function encodeBase64Url(str: string): string {
  const base64 = btoa(
    String.fromCharCode(...new TextEncoder().encode(str)),
  );
  return base64
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Decodes a base64-encoded string back to UTF-8.
 */
export function decodeBase64(hash: string): string {
  const binary = atob(hash);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/**
 * Decodes a base64url-encoded string (e.g. a JWT segment) back to UTF-8.
 */
export function decodeBase64Url(str: string): string {
  // Convert base64url to standard base64
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return decodeURIComponent(
    Array.from(bytes)
      .map((b) => `%${b.toString(16).padStart(2, '0')}`)
      .join(''),
  );
}
