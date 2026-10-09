export interface AuthCallbackData {
  code: string;
  failed: boolean;
}

// Run before configuration fetches, Supabase initialization and Angular routing.
export function consumeAuthCallback(location: Location, history: History): AuthCallbackData {
  const data = { code: '', failed: false };
  if (location.pathname !== '/auth/callback') return data;
  const parameters = new URLSearchParams(location.search);
  data.code = parameters.get('code') ?? '';
  data.failed = parameters.has('error') || !!location.hash || !data.code;
  history.replaceState(null, '', '/auth/callback');
  return data;
}
