import { describe, expect, it } from 'vitest';
import { publicTagId } from './public-tag-id';

const id = '123e4567-e89b-42d3-a456-426614174000';
const origin = 'https://lost-key-finder-dev.web.app';

describe('Tag ID and QR payload validation', () => {
  it('accepts UUID-v4 IDs and this application’s tag URLs', () => {
    expect(publicTagId(` ${id.toUpperCase()} `, origin)).toBe(id);
    expect(publicTagId(`${origin}/f/${id}`, origin)).toBe(id);
    expect(publicTagId(`${origin}/f/${id}/`, origin)).toBe(id);
  });
  it.each(['', 'not-a-tag', '123e4567-e89b-12d3-a456-426614174000', `https://foreign.example/f/${id}`,
    `${origin}.evil.example/f/${id}`, `${origin}/login`, `${origin}/f/${id}?redirect=https://evil.example`,
    `${origin}/f/${id}#private`, 'javascript:alert(1)', `${origin}/f/${id}/other`])('rejects %s', value => {
    expect(publicTagId(value, origin)).toBeNull();
  });
});
