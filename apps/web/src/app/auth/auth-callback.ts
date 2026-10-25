export interface AuthCallbackData {
  link: string;
  failed: boolean;
  registrationId?: string;
}

// Remove credentials before configuration fetches, SDK initialization and routing.
export function consumeAuthCallback(location: Location, history: History): AuthCallbackData {
  const data: AuthCallbackData = { link: '', failed: false };
  if (location.pathname !== '/auth/callback') return data;
  const parameters = new URLSearchParams(location.search);
  data.failed = parameters.has('error') || !!location.hash || parameters.get('mode') !== 'signIn' ||
    !parameters.get('oobCode') || !parameters.get('apiKey');
  for (const name of ['apiKey', 'oobCode', 'mode', 'registrationId', 'continueUrl']) {
    if (parameters.getAll(name).length > 1) data.failed = true;
  }
  let registrationId = parameters.get('registrationId');
  if (parameters.has('continueUrl')) {
    try {
      const continuation = new URL(parameters.get('continueUrl')!);
      if (continuation.origin !== location.origin || continuation.pathname !== '/auth/callback' || continuation.hash ||
        [...continuation.searchParams.keys()].some(key => key !== 'registrationId') || continuation.searchParams.getAll('registrationId').length > 1) {
        data.failed = true;
      }
      const nested = continuation.searchParams.get('registrationId');
      if (registrationId && nested && nested !== registrationId) data.failed = true;
      registrationId ??= nested;
    } catch { data.failed = true; }
  }
  if (registrationId !== null) {
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(registrationId)) {
      if (!data.failed) data.registrationId = registrationId.toLowerCase();
    } else data.failed = true;
  }
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
