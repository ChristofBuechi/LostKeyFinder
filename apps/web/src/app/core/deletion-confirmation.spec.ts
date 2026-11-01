import { describe, expect, it, vi } from 'vitest';
import { consumeDeletionConfirmation } from './deletion-confirmation';
const token = 'A'.repeat(43);
describe('Deletion confirmation URL hygiene', () => {
  it('keeps the secret in memory and removes it from the address immediately', () => {
    const replaceState = vi.fn();
    const result = consumeDeletionConfirmation(new URL(`https://example.test/account/deletion-confirm#${token}`) as unknown as Location,
      { replaceState } as unknown as History);
    expect(result).toBe(token);
    expect(replaceState).toHaveBeenCalledWith(null, '', '/account/deletion-confirm');
  });
  it.each([`?token=${token}`, `#short`, `#${token}&userId=other`, `?userId=other#${token}`, ''])('rejects malformed links and strips their contents: %s', suffix => {
    const replaceState = vi.fn();
    expect(consumeDeletionConfirmation(new URL(`https://example.test/account/deletion-confirm${suffix}`) as unknown as Location,
      { replaceState } as unknown as History)).toBe('');
    expect(replaceState).toHaveBeenCalledWith(null, '', '/account/deletion-confirm');
  });
  it('does not touch unrelated login callbacks', () => {
    const replaceState = vi.fn();
    expect(consumeDeletionConfirmation(new URL('https://example.test/auth/callback?mode=signIn') as unknown as Location,
      { replaceState } as unknown as History)).toBe('');
    expect(replaceState).not.toHaveBeenCalled();
  });
});
