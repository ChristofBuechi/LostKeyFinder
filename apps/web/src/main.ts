import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { AUTH_CALLBACK, AUTH_CONFIGURATION } from './app/auth/auth';
import { consumeAuthCallback } from './app/auth/auth-callback';
import { loadAuthConfiguration } from './app/auth/auth-configuration';

const callback = consumeAuthCallback(location, history);

async function start(): Promise<void> {
  const configuration = await loadAuthConfiguration();
  await bootstrapApplication(App, {
    providers: [...appConfig.providers,
      { provide: AUTH_CONFIGURATION, useValue: configuration },
      { provide: AUTH_CALLBACK, useValue: callback }],
  });
}

void start().catch(() => console.error('Application startup failed'));
