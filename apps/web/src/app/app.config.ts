import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { ApiConfiguration } from '@lost-key-finder/api-client';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(),
    {
      provide: ApiConfiguration,
      useFactory: () => {
        const configuration = new ApiConfiguration();
        configuration.rootUrl = '';
        return configuration;
      },
    },
    provideRouter(routes)
  ]
};
