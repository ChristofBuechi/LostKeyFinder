import { writeFileSync } from 'node:fs';

const configuration = Object.fromEntries(['apiKey', 'authDomain', 'projectId', 'appId'].map(key => [
  key, process.env[`FIREBASE_${key.replace(/[A-Z]/g, letter => `_${letter}`).toUpperCase()}`] ?? '',
]));
if (Object.values(configuration).some(Boolean) && !Object.values(configuration).every(Boolean)) {
  throw new Error('Firebase Auth requires FIREBASE_API_KEY, FIREBASE_AUTH_DOMAIN, FIREBASE_PROJECT_ID and FIREBASE_APP_ID');
}
writeFileSync(new URL('../public/auth-config.json', import.meta.url), JSON.stringify(configuration));
