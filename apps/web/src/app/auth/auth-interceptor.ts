import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { Auth } from './auth';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const url = new URL(request.url, location.origin);
  if (url.origin !== location.origin || !url.pathname.startsWith('/api/v1/') ||
    url.pathname.startsWith('/api/v1/health/') || url.pathname === '/api/v1/version') return next(request);
  const auth = inject(Auth);
  return from(auth.accessToken()).pipe(switchMap((token) => next(token ? request.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  }) : request)));
};
