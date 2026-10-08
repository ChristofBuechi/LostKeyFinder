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

  beforeEach(() => {
    TestBed.resetTestingModule();
    accessToken.mockReset().mockResolvedValue('test-token');
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: Auth, useValue: { accessToken } }] });
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
      '/api/v1/health/ready', '/api/v1/version']) {
      const response = firstValueFrom(TestBed.inject(HttpClient).get(url));
      const request = http.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({});
      await response;
    }
    expect(accessToken).not.toHaveBeenCalled();
  });
});
