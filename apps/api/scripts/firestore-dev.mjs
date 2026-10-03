import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const mode = process.argv[2] ?? 'verify';
if (!['verify', 'start'].includes(mode)) {
  throw new Error('Usage: node scripts/firestore-dev.mjs [verify|start]');
}

// Obtain short-lived credentials without printing or persisting them.
const gcloud = (args) => execFileSync('gcloud', args, {
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'inherit'],
}).trim();

const uri = new URL(gcloud([
  'firestore', 'databases', 'connection-string',
  '--project=lost-key-finder-dev', '--database=dev1', '--auth=none',
]));
uri.username = 'access_token';
uri.password = gcloud(['auth', 'print-access-token']);
uri.searchParams.set('authMechanism', 'PLAIN');
uri.searchParams.set('authSource', '$external');
uri.searchParams.set('serverSelectionTimeoutMS', '15000');

const result = spawnSync('./gradlew', [mode === 'verify' ? 'integrationTest' : 'bootRun'], {
  cwd: fileURLToPath(new URL('../', import.meta.url)),
  stdio: 'inherit',
  env: {
    ...process.env,
    SPRING_PROFILES_ACTIVE: 'dev',
    FIRESTORE_MONGODB_URI: uri.toString(),
  },
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
