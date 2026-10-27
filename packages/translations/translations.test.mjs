import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';

function texts(value, prefix = '') {
  return Object.entries(value).flatMap(([key, text]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof text === 'object' && text !== null && !Array.isArray(text)) return texts(text, path);
    assert.equal(typeof text, 'string', `${path} must be text`);
    assert.ok(text.trim(), `${path} must not be empty`);
    return [[path, text]];
  });
}

test('German and English have identical keys and named placeholders', () => {
  const de = new Map(texts(JSON.parse(readFileSync(new URL('./de.json', import.meta.url), 'utf8'))));
  const en = new Map(texts(JSON.parse(readFileSync(new URL('./en.json', import.meta.url), 'utf8'))));
  assert.deepEqual([...de.keys()].sort(), [...en.keys()].sort());
  const placeholders = text => [...text.matchAll(/\{([A-Za-z][A-Za-z0-9]*)}/g)].map(match => match[1]).sort();
  for (const [key, text] of de) assert.deepEqual(placeholders(text), placeholders(en.get(key)), key);
});
