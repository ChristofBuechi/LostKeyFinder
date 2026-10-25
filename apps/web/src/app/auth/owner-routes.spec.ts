import { HealthService } from '@lost-key-finder/api-client';
import { of } from 'rxjs';
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
  const needsEmail = signal(false);
  const completeCallback = vi.fn();
  const signOut = vi.fn();
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    signedIn.set(false);
    needsEmail.set(false);
    completeCallback.mockReset();
    signOut.mockReset().mockImplementation(async () => signedIn.set(false));
    TestBed.configureTestingModule({ providers: [{ provide: HealthService, useValue: { healthControllerReady: () => of({ status: 'ok' }) } }, provideZonelessChangeDetection(), provideRouter(routes),
      provideHttpClient(), provideHttpClientTesting(), { provide: Auth, useValue: {
        signedIn, needsEmail, configured: true, ready: Promise.resolve(), completeCallback, signOut,
      } }] });
  });

  it('redirects an unauthenticated account visit to login without calling the API', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account');
    expect(TestBed.inject(Router).url).toBe('/#owner');
    TestBed.inject(HttpTestingController).expectNone('/api/v1/me');
  });

  it('preserves the finder destination when an old login bookmark is opened', async () => {
    const harness = await RouterTestingHarness.create('/login#finder');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/#finder');
    expect(harness.routeNativeElement!.querySelector('#finder #tag-id')).not.toBeNull();
    expect(harness.routeNativeElement!.querySelector('#owner #email')).not.toBeNull();
  });

  it('shows a safe callback failure with a path to request a new link', async () => {
    completeCallback.mockRejectedValue(new Error('private-provider-code'));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/auth/callback');
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('ungültig');
    expect(harness.routeNativeElement?.textContent).not.toContain('private-provider-code');
    expect(harness.routeNativeElement?.querySelector('a')?.getAttribute('href')).toBe('/#owner');
  });

  it('requests email on another device and submits it before navigating', async () => {
    needsEmail.set(true);
    completeCallback.mockImplementation(async (email: string) => {
      if (email) needsEmail.set(false);
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/auth/callback');
    await harness.fixture.whenStable();
    const input = harness.routeNativeElement!.querySelector('input')!;
    input.value = 'owner@example.com';
    input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await harness.fixture.whenStable();
    expect(completeCallback).toHaveBeenLastCalledWith('owner@example.com');
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
    expect(TestBed.inject(Router).url).toBe('/#owner');
    expect(harness.routeNativeElement?.textContent).not.toContain('owner@example.com');
    http.verify();
  });

  it('opens the global inventory after an admin login while keeping the account reachable', async () => {
    signedIn.set(true);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account?entry=login');
    const http = TestBed.inject(HttpTestingController);
    const user = { id: 'local-id', email: 'admin@example.com', status: 'ACTIVE', isAdmin: true,
      createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' };
    http.expectOne('/api/v1/me').flush(user);
    const inventory = await vi.waitFor(() => http.expectOne('/api/v1/admin/tags'));
    inventory.flush({ items: [], nextCursor: null });
    const counts = await vi.waitFor(() => http.expectOne('/api/v1/admin/tags/summary'));
    counts.flush({ total: 0, assigned: 0, unassigned: 0, blocked: 0, retired: 0, prepared: 0, released: 0 });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/admin/tags');
    await harness.navigateByUrl('/account');
    http.expectOne('/api/v1/me').flush(user);
    await Promise.resolve();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/account');
    expect(harness.routeNativeElement?.querySelector('a[href="/admin/tags"]')).not.toBeNull();
    http.verify();
  });

  it('does not redirect from a destroyed account after a late admin response', async () => {
    signedIn.set(true);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account?entry=login');
    const http = TestBed.inject(HttpTestingController);
    const me = http.expectOne('/api/v1/me');
    await harness.navigateByUrl('/missing-page');
    me.flush({ id: 'local-id', email: 'admin@example.com', status: 'ACTIVE', isAdmin: true,
      createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/missing-page');
    http.verify();
  });

  it('continues a registration after email login instead of opening the admin inventory', async () => {
    const id = '550e8400-e29b-41d4-a716-446655440000';
    signedIn.set(true);
    TestBed.overrideProvider(Auth, { useValue: { signedIn, needsEmail, configured: true, ready: Promise.resolve(),
      completeCallback: vi.fn().mockResolvedValue(undefined), registrationId: id } });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/auth/callback');
    const http = TestBed.inject(HttpTestingController);
    const view = await vi.waitFor(() => http.expectOne(`/api/v1/tag-registrations/${id}`));
    view.flush({ flowId: id, publicId: id, completed: false, expiresAt: '2026-12-01T00:00:00Z' });
    http.expectOne('/api/v1/me').flush({ id, email: 'admin@example.com', status: 'ACTIVE', isAdmin: true,
      createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe(`/tag-registrations/${id}`);
    expect(harness.routeNativeElement?.textContent).toContain('admin@example.com');
    http.expectNone(request => request.method === 'POST');
    http.verify();
  });
});
