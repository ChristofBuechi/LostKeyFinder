import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { Auth } from './auth';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const url = new URL(request.url, location.origin);
  if (url.origin !== location.origin || !url.pathname.startsWith('/api/v1/') ||
    url.pathname.startsWith('/api/v1/health/') || url.pathname.startsWith('/api/v1/public/') || url.pathname === '/api/v1/version') return next(request);
  const auth = inject(Auth);
  return from(auth.accessToken()).pipe(
    catchError(error => { auth.expireSession(); return throwError(() => error); }),
    switchMap(token => next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request)),
    catchError(error => {
      if (error instanceof HttpErrorResponse && (error.status === 401 || (error.status === 403 && url.pathname === '/api/v1/me'))) auth.expireSession();
      return throwError(() => error);
    }),
  );
};
