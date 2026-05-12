import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { redirectOnError } from '../../utils/redirect-on-error';

describe('redirectOnError', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    // Mock window.location
    Object.defineProperty(window, 'location', {
      value: {
        ...originalLocation,
        href: 'http://localhost:3000/login?state=abc&nonce=xyz#' +
          btoa(JSON.stringify({ redirectUri: 'http://example.com/callback' })),
        replace: vi.fn(),
      },
      writable: true,
    });
    window.onbeforeunload = vi.fn() as unknown as typeof window.onbeforeunload;
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    });
  });

  it('redirects to the redirect_uri with error params', () => {
    redirectOnError('access_denied', 'User denied');

    expect(window.location.replace).toHaveBeenCalledTimes(1);
    const url = (window.location.replace as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toContain('http://example.com/callback?');
    expect(url).toContain('error=access_denied');
    expect(url).toContain('error_description=User+denied');
    expect(url).toContain('state=abc');
  });

  it('clears onbeforeunload before redirect', () => {
    redirectOnError('server_error');
    expect(window.onbeforeunload).toBeNull();
  });

  it('does nothing when hash is empty', () => {
    window.location.href = 'http://localhost:3000/login?state=abc&nonce=xyz';
    redirectOnError('error_code');
    expect(window.location.replace).not.toHaveBeenCalled();
  });

  it('does nothing when state is missing', () => {
    window.location.href = 'http://localhost:3000/login?nonce=xyz#' +
      btoa(JSON.stringify({ redirectUri: 'http://example.com/callback' }));
    redirectOnError('error_code');
    expect(window.location.replace).not.toHaveBeenCalled();
  });
});
