export interface AuthCallbackData {
  link: string;
  failed: boolean;
}

// Remove credentials before configuration fetches, SDK initialization and routing.
export function consumeAuthCallback(location: Location, history: History): AuthCallbackData {
  const data = { link: '', failed: false };
  if (location.pathname !== '/auth/callback') return data;
  const parameters = new URLSearchParams(location.search);
  data.failed = parameters.has('error') || !!location.hash || parameters.get('mode') !== 'signIn' ||
    !parameters.get('oobCode') || !parameters.get('apiKey');
  if (!data.failed) {
    // Keep only the fields Firebase needs, in memory only.
    const link = new URL(location.origin + '/auth/callback');
    for (const name of ['apiKey', 'oobCode', 'mode', 'tenantId']) {
      const value = parameters.get(name);
      if (value) link.searchParams.set(name, value);
    }
    data.link = link.href;
  }
  history.replaceState(null, '', '/auth/callback');
  return data;
}
