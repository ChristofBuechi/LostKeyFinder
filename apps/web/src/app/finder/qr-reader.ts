import { InjectionToken } from '@angular/core';

export const QR_READER = new InjectionToken<() => Promise<typeof import('qr-scanner').default>>('Local QR reader', {
  providedIn: 'root',
  factory: () => () => import('qr-scanner').then(module => module.default),
});
