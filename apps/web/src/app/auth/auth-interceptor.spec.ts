import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Auth } from './auth';
import { authInterceptor } from './auth-interceptor';

describe('API bearer token scoping', () => {
  let http: HttpTestingController;
  const accessToken = vi.fn();
  const expireSession = vi.fn();

  beforeEach(() => {
    TestBed.resetTestingModule();
    expireSession.mockReset();
    accessToken.mockReset().mockResolvedValue('test-token');
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: Auth, useValue: { accessToken, expireSession } }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('attaches a current session token to the same-origin versioned API', async () => {
    const response = firstValueFrom(TestBed.inject(HttpClient).get('/api/v1/me'));
    await Promise.resolve();
    const request = http.expectOne('/api/v1/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({});
    await response;
  });

  it('never sends a session token to another origin or to public assets', async () => {
    for (const url of ['https://foreign.example/api/v1/me', '/auth-config.json', '/api/v10/me',
      '/api/v1/health/ready', '/api/v1/version', '/api/v1/public/tags/example']) {
      const response = firstValueFrom(TestBed.inject(HttpClient).get(url));
      const request = http.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({});
      await response;
    }
    expect(accessToken).not.toHaveBeenCalled();
  });
  it('expires the local session on a protected API 401 but not on an authorization denial', async () => {
    for (const status of [403, 401]) {
      const response = firstValueFrom(TestBed.inject(HttpClient).get('/api/v1/admin/tags'));
      const rejection = expect(response).rejects.toMatchObject({ status });
      await Promise.resolve();
      http.expectOne('/api/v1/admin/tags').flush({}, { status, statusText: 'Denied' });
      await rejection;
      expect(expireSession).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
    }
  });

  it('expires the session if Firebase cannot refresh its token and never sends the protected request', async () => {
    accessToken.mockRejectedValue(new Error('Expired refresh token'));
    await expect(firstValueFrom(TestBed.inject(HttpClient).get('/api/v1/me'))).rejects.toThrow();
    expect(expireSession).toHaveBeenCalledTimes(1);
    http.expectNone('/api/v1/me');
  });

  it('expires a session when the account endpoint reports the account is blocked', async () => {
    const response = firstValueFrom(TestBed.inject(HttpClient).get('/api/v1/me'));
    const rejection = expect(response).rejects.toMatchObject({ status: 403 });
    await Promise.resolve();
    http.expectOne('/api/v1/me').flush({}, { status: 403, statusText: 'Account unavailable' });
    await rejection;
    expect(expireSession).toHaveBeenCalledTimes(1);
  });

});
