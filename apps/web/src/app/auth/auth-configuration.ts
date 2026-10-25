import type { AuthConfiguration } from './auth';

export async function loadAuthConfiguration(): Promise<AuthConfiguration> {
  try {
    // ponytail: native timeout also covers reading the response body.
    const response = await fetch('/auth-config.json', { cache: 'no-store', signal: AbortSignal.timeout(5000) });
    if (response.ok) {
      const configuration: unknown = await response.json();
      if (configuration && typeof configuration === 'object' &&
        ['apiKey', 'authDomain', 'projectId', 'appId'].every(key =>
          key in configuration && typeof Reflect.get(configuration, key) === 'string' &&
          Reflect.get(configuration, key).trim().length > 0)) {
        const { apiKey, authDomain, projectId, appId } = configuration as Required<AuthConfiguration>;
        return { apiKey: apiKey.trim(), authDomain: authDomain.trim(), projectId: projectId.trim(), appId: appId.trim() };
      }
    }
  } catch {
    // Public pages remain available when Auth configuration cannot be loaded.
  }
  return {};
}
