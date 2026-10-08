import { writeFileSync } from 'node:fs';

const url = process.env.SUPABASE_URL ?? '';
const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY ?? '';
if (url || publishableKey) {
  const origin = new URL(url);
  if (origin.protocol !== 'https:' || origin.origin !== url || !publishableKey.startsWith('sb_publishable_')) {
    throw new Error('Auth configuration requires an HTTPS origin and a Supabase publishable key');
  }
}
writeFileSync(new URL('../public/auth-config.json', import.meta.url), JSON.stringify({ url, publishableKey }));
