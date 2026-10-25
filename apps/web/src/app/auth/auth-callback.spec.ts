import { describe, expect, it, vi } from 'vitest';
import { consumeAuthCallback } from './auth-callback';

describe('Auth callback URL hygiene', () => {
  it('captures a Firebase link once and removes all query and fragment data before other work', () => {
    const replaceState = vi.fn();
    const result = consumeAuthCallback({ origin: 'https://example.com', pathname: '/auth/callback', search: '?mode=signIn&apiKey=test&oobCode=one-time-code&flowId=private', hash: '' } as Location,
      { replaceState } as unknown as History);
    expect(result).toEqual({ link: 'https://example.com/auth/callback?apiKey=test&oobCode=one-time-code&mode=signIn', failed: false });
    expect(replaceState).toHaveBeenCalledExactlyOnceWith(null, '', '/auth/callback');
  });

  it('rejects implicit token callbacks and provider errors without retaining their contents', () => {
    for (const input of [{ search: '', hash: '#access_token=secret&refresh_token=secret' },
      { search: '?error=access_denied&error_description=private', hash: '' }]) {
      const replaceState = vi.fn();
      const result = consumeAuthCallback({ pathname: '/auth/callback', ...input } as Location,
        { replaceState } as unknown as History);
      expect(result.failed).toBe(true);
      expect(JSON.stringify(result)).not.toContain('secret');
      expect(JSON.stringify(result)).not.toContain('private');
      expect(replaceState).toHaveBeenCalledWith(null, '', '/auth/callback');
    }
  });

  it('does not rewrite unrelated application routes', () => {
    const replaceState = vi.fn();
    consumeAuthCallback({ pathname: '/login' } as Location, { replaceState } as unknown as History);
    expect(replaceState).not.toHaveBeenCalled();
  });

  it('retains validated cross-browser registration state separately from the Firebase link', () => {
    const id = '550e8400-e29b-41d4-a716-446655440000';
    const base = { origin: 'https://example.com', pathname: '/auth/callback', hash: '' };
    for (const suffix of [`&registrationId=${id}`, `&continueUrl=${encodeURIComponent(`https://example.com/auth/callback?registrationId=${id}`)}`]) {
      const result = consumeAuthCallback({ ...base, search: `?mode=signIn&apiKey=test&oobCode=code${suffix}` } as Location,
        { replaceState: vi.fn() } as unknown as History);
      expect(result.registrationId).toBe(id);
      expect(result.failed).toBe(false);
      expect(result.link).not.toContain('registrationId');
    }
    for (const suffix of ['&registrationId=bad', '&registrationId=', '&mode=signIn',
      `&continueUrl=${encodeURIComponent(`https://foreign.example/auth/callback?registrationId=${id}`)}`,
      `&continueUrl=${encodeURIComponent(`https://example.com/auth/callback?email=private&registrationId=${id}`)}`]) {
      const result = consumeAuthCallback({ ...base, search: `?mode=signIn&apiKey=test&oobCode=code${suffix}` } as Location,
        { replaceState: vi.fn() } as unknown as History);
      expect(result.failed).toBe(true);
      expect(result.link).toBe('');
      expect(result.registrationId).toBeUndefined();
    }
  });
});
