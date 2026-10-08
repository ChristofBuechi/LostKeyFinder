import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { AUTH_CALLBACK, AUTH_CONFIGURATION, AuthConfiguration } from './app/auth/auth';
import { consumeAuthCallback } from './app/auth/auth-callback';

const callback = consumeAuthCallback(location, history);

async function start(): Promise<void> {
  let configuration: AuthConfiguration = { url: '', publishableKey: '' };
  try {
    const response = await fetch('/auth-config.json', { cache: 'no-store' });
    if (response.ok) configuration = await response.json();
  } catch {
    // Public pages remain available when Auth configuration cannot be loaded.
  }
  await bootstrapApplication(App, {
    providers: [...appConfig.providers,
      { provide: AUTH_CONFIGURATION, useValue: configuration },
      { provide: AUTH_CALLBACK, useValue: callback }],
  });
}

void start().catch(() => console.error('Application startup failed'));
