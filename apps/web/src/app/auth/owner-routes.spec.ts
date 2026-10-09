import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../app.routes';
import { Auth } from './auth';

describe('Owner route access and callback', () => {
  const signedIn = signal(false);
  const completeCallback = vi.fn();
  const signOut = vi.fn();
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    signedIn.set(false);
    completeCallback.mockReset();
    signOut.mockReset().mockImplementation(async () => signedIn.set(false));
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideRouter(routes),
      provideHttpClient(), provideHttpClientTesting(), { provide: Auth, useValue: {
        signedIn, configured: true, ready: Promise.resolve(), completeCallback, signOut,
      } }] });
  });

  it('redirects an unauthenticated account visit to login without calling the API', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account');
    expect(TestBed.inject(Router).url).toBe('/login');
    TestBed.inject(HttpTestingController).expectNone('/api/v1/me');
  });

  it('shows a safe callback failure with a path to request a new link', async () => {
    completeCallback.mockRejectedValue(new Error('private-provider-code'));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/auth/callback');
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('ungültig');
    expect(harness.routeNativeElement?.textContent).not.toContain('private-provider-code');
    expect(harness.routeNativeElement?.querySelector('a')?.getAttribute('href')).toBe('/login');
  });

  it('does not display account data after a server-side access denial', async () => {
    signedIn.set(true);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/me').flush({}, { status: 403, statusText: 'Forbidden' });
    await Promise.resolve();
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('Zugriff gesperrt');
    expect(harness.routeNativeElement?.querySelector('dl')).toBeNull();
    http.verify();
  });

  it('loads the protected account and clears it on logout before returning to login', async () => {
    signedIn.set(true);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/me').flush({ id: 'local-id', email: 'owner@example.com', status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' });
    await Promise.resolve();
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('owner@example.com');
    harness.routeNativeElement?.querySelector('button')?.click();
    await harness.fixture.whenStable();
    expect(signOut).toHaveBeenCalledTimes(1);
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(harness.routeNativeElement?.textContent).not.toContain('owner@example.com');
    http.verify();
  });
});
