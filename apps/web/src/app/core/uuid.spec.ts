import { describe, expect, it } from 'vitest';
import { isUuidV4 } from './uuid';

const id = '550e8400-e29b-41d4-a716-446655440000';

describe('UUID-v4 validation', () => {
  it('accepts both letter cases and every valid variant', () => {
    for (const variant of ['8', '9', 'a', 'b']) {
      const value = id.slice(0, 19) + variant + id.slice(20);
      expect(isUuidV4(value)).toBe(true);
      expect(isUuidV4(value.toUpperCase())).toBe(true);
    }
  });
  it.each(['', ` ${id}`, `${id} `, id.replace('-41d4-', '-11d4-'), id.replace('-a716-', '-7716-'),
    id.replace('550e', '550g'), id.replaceAll('-', ''), `https://example.test/f/${id}`])('rejects %s', value => {
    expect(isUuidV4(value)).toBe(false);
  });
});
