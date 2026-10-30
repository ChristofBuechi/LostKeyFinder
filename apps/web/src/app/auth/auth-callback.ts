import { isUuidV4 } from '../core/uuid';
export interface AuthCallbackData {
  link: string;
  failed: boolean;
  registrationId?: string;
  finderReportId?: string;
  chatId?: string;
}

// Remove credentials before configuration fetches, SDK initialization and routing.
export function consumeAuthCallback(location: Location, history: History): AuthCallbackData {
  const data: AuthCallbackData = { link: '', failed: false };
  if (location.pathname !== '/auth/callback') return data;
  const parameters = new URLSearchParams(location.search);
  data.failed = parameters.has('error') || !!location.hash || parameters.get('mode') !== 'signIn' ||
    !parameters.get('oobCode') || !parameters.get('apiKey');
  for (const name of ['apiKey', 'oobCode', 'mode', 'registrationId', 'finderReportId', 'chatId', 'continueUrl']) {
    if (parameters.getAll(name).length > 1) data.failed = true;
  }
  const flowNames = ['registrationId', 'finderReportId', 'chatId'] as const;
  const flows = new URLSearchParams();
  for (const name of flowNames) {
    if (parameters.has(name)) flows.set(name, parameters.get(name)!);
  }
  if (parameters.has('continueUrl')) {
    try {
      const continuation = new URL(parameters.get('continueUrl')!);
      if (continuation.origin !== location.origin || continuation.pathname !== '/auth/callback' || continuation.hash ||
        [...continuation.searchParams.keys()].some(key => !flowNames.includes(key as typeof flowNames[number]))) data.failed = true;
      for (const name of flowNames) {
        if (continuation.searchParams.getAll(name).length > 1) data.failed = true;
        const nested = continuation.searchParams.get(name);
        if (nested !== null) {
          if (flows.has(name) && flows.get(name) !== nested) data.failed = true;
          flows.set(name, nested);
        }
      }
    } catch { data.failed = true; }
  }
  if (flows.size > 1) data.failed = true;
  for (const name of flowNames) {
    const value = flows.get(name);
    if (value !== null) {
      if (!isUuidV4(value)) data.failed = true;
      else if (!data.failed) data[name] = value.toLowerCase();
    }
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
