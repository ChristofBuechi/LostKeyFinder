import { InjectionToken } from '@angular/core';

export const DELETION_CONFIRMATION_TOKEN = new InjectionToken<string>('Deletion confirmation token', { providedIn: 'root', factory: () => '' });

// Strip the secret before configuration fetches, Firebase initialization or navigation.
export function consumeDeletionConfirmation(location: Location, history: History): string {
  if (!/^\/account\/deletion-confirm\/?$/.test(location.pathname)) return '';
  const token = !location.search && /^[A-Za-z0-9_-]{43}$/.test(location.hash.slice(1)) ? location.hash.slice(1) : '';
  history.replaceState(null, '', '/account/deletion-confirm');
  return token;
}
