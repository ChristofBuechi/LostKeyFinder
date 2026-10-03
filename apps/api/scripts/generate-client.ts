import { mkdir } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

async function generate(): Promise<void> {
  await mkdir('../../packages/api-client/src/generated', { recursive: true });
  execFileSync('ng-openapi-gen', [
    '--input',
    'openapi.json',
    '--output',
    '../../packages/api-client/src/generated',
    '--services',
    'true',
    '--promises',
    'false',
    '--index-file',
    'true',
    '--templates',
    '../../packages/api-client/templates',
  ], { stdio: 'inherit' });
}

void generate();
