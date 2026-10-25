import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const mode = process.argv[2] ?? 'verify';
if (!['verify', 'start', 'tag-indexes', 'schema'].includes(mode)) {
  throw new Error('Usage: node scripts/firestore-dev.mjs [verify|start|tag-indexes|schema <operator arguments>]');
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

const task = { verify: 'integrationTest', start: 'bootRun', 'tag-indexes': 'provisionTagIndexes', schema: 'simplifySchema' }[mode];
const argumentsForTask = [task, '-Pkotlin.compiler.execution.strategy=in-process'];
if (mode === 'schema') argumentsForTask.push(`-PmigrationArgs=${process.argv.slice(3).join(' ') || 'inspect'}`);
const needsKey = ['verify', 'start'].includes(mode) || (mode === 'schema' && process.argv[3] === 'cleanup');
const rateLimitKey = needsKey ? gcloud(['secrets', 'versions', 'access', '1', '--secret=public-rate-limit-hmac', '--project=lost-key-finder-dev']) : '';
const result = spawnSync('./gradlew', argumentsForTask, {
  cwd: fileURLToPath(new URL('../', import.meta.url)),
  stdio: 'inherit',
  env: {
    ...process.env,
    SPRING_PROFILES_ACTIVE: 'dev',
    FIRESTORE_MONGODB_URI: uri.toString(),
    PUBLIC_RATE_LIMIT_HMAC_KEY: rateLimitKey,
    MIGRATION_PROJECT: 'lost-key-finder-dev',
  },
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
