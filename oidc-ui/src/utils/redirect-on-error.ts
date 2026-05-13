import { decodeBase64 } from './encoding';

/**
 * Redirects the browser to the OAuth2 redirect_uri with error parameters.
 * Used when a fatal error occurs and we need to hand control back to the relying party.
 */
export function redirectOnError(
  errorCode: string,
  errorDescription?: string,
): void {
  const urlObj = new URL(window.location.href);
  const state = urlObj.searchParams.get('state');
  const nonce = urlObj.searchParams.get('nonce');
  const hash = urlObj.hash.substring(1);

  if (!hash || !state || !nonce) return;

  let oauthDetails: { redirectUri?: string };
  try {
    oauthDetails = JSON.parse(decodeBase64(hash));
  } catch {
    return;
  }

  const redirectUri = oauthDetails.redirectUri;
  if (!redirectUri) return;

  // Validate redirect URI origin to prevent open-redirect attacks
  let redirectUrl: URL;
  try {
    redirectUrl = new URL(redirectUri);
  } catch {
    console.error('Invalid redirect URI:', redirectUri);
    return;
  }

  if (redirectUrl.origin !== window.location.origin) {
    console.error(
      'Redirect URI origin mismatch: expected %s, got %s',
      window.location.origin,
      redirectUrl.origin,
    );
    return;
  }

  const params = new URLSearchParams();
  if (errorDescription) params.set('error_description', errorDescription);
  params.set('state', state);
  params.set('error', errorCode);

  window.onbeforeunload = null;
  window.location.replace(`${redirectUri}?${params.toString()}`);
}
