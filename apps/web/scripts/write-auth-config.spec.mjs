import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, copyFileSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';

function generate(values) {
  const directory = mkdtempSync(join(tmpdir(), 'firebase-config-'));
  mkdirSync(join(directory, 'scripts'));
  mkdirSync(join(directory, 'public'));
  const script = join(directory, 'scripts/write-auth-config.mjs');
  copyFileSync(new URL('./write-auth-config.mjs', import.meta.url), script);
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('FIREBASE_')));
  try {
    const result = spawnSync(process.execPath, [script], { env: { ...env, ...values }, encoding: 'utf8' });
    const output = join(directory, 'public/auth-config.json');
    return { ...result, configuration: existsSync(output) ? JSON.parse(readFileSync(output, 'utf8')) : undefined };
  } finally {
    rmSync(directory, { recursive: true });
  }
}

test('writes only the four public Firebase values and trims environment whitespace', () => {
  const result = generate({ FIREBASE_API_KEY: ' key ', FIREBASE_AUTH_DOMAIN: 'test.firebaseapp.com',
    FIREBASE_PROJECT_ID: 'test', FIREBASE_APP_ID: 'app', FIREBASE_ADMIN_SECRET: 'must-not-be-public' });
  assert.equal(result.status, 0);
  assert.deepEqual(result.configuration, { apiKey: 'key', authDomain: 'test.firebaseapp.com', projectId: 'test', appId: 'app' });
});

test('allows public-only local builds without Auth configuration', () => {
  const result = generate({});
  assert.equal(result.status, 0);
  assert.deepEqual(result.configuration, { apiKey: '', authDomain: '', projectId: '', appId: '' });
});

test('rejects each missing or whitespace-only required value without writing configuration', () => {
  const complete = { FIREBASE_API_KEY: 'key', FIREBASE_AUTH_DOMAIN: 'test.firebaseapp.com',
    FIREBASE_PROJECT_ID: 'test', FIREBASE_APP_ID: 'app' };
  for (const key of Object.keys(complete)) {
    for (const missing of ['', '   ']) {
      const result = generate({ ...complete, [key]: missing });
      assert.notEqual(result.status, 0);
      assert.equal(result.configuration, undefined);
      assert.match(result.stderr, /Firebase Auth requires/);
    }
  }
});
