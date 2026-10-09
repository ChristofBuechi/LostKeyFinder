import { describe, expect, it, vi } from 'vitest';
import { consumeAuthCallback } from './auth-callback';

describe('Auth callback URL hygiene', () => {
  it('captures a PKCE code once and removes all query and fragment data before other work', () => {
    const replaceState = vi.fn();
    const result = consumeAuthCallback({ pathname: '/auth/callback', search: '?code=one-time-code&flowId=private', hash: '' } as Location,
      { replaceState } as unknown as History);
    expect(result).toEqual({ code: 'one-time-code', failed: false });
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
});
