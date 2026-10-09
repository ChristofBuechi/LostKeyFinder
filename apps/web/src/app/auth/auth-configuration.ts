import type { AuthConfiguration } from './auth';

export async function loadAuthConfiguration(): Promise<AuthConfiguration> {
  try {
    // ponytail: native timeout also covers reading the response body.
    const response = await fetch('/auth-config.json', { cache: 'no-store', signal: AbortSignal.timeout(5000) });
    if (response.ok) return await response.json();
  } catch {
    // Public pages remain available when Auth configuration cannot be loaded.
  }
  return { url: '', publishableKey: '' };
}
