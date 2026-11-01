import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { DELETION_CONFIRMATION_TOKEN } from '../../core/deletion-confirmation';
import { AccountDeletionConfirm } from './account-deletion-confirm';
const token = 'A'.repeat(43);
describe('Email-confirmed account deletion', () => {
  const signedIn = signal(true);
  const signOut = vi.fn();
  const firebaseUserId = signal('owner');
  let visible = true;
  const prerendering = Object.getOwnPropertyDescriptor(document, 'prerendering');
  beforeEach(() => {
    visible = true;
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visible ? 'visible' : 'hidden');
    Object.defineProperty(document, 'prerendering', { configurable: true, value: false });
    localStorage.clear(); signedIn.set(true); firebaseUserId.set('owner'); signOut.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: DELETION_CONFIRMATION_TOKEN, useValue: token },
      { provide: Auth, useValue: { signedIn, firebaseUserId, signOut, expireSession: vi.fn() } }] });
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.restoreAllMocks();
    if (prerendering) Object.defineProperty(document, 'prerendering', prerendering);
    else Reflect.deleteProperty(document, 'prerendering');
  });
  it('automatically confirms the email link and sends the secret only in the authenticated request body', async () => {
    const fixture = TestBed.createComponent(AccountDeletionConfirm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const http = TestBed.inject(HttpTestingController);
    http.expectNone(request => request.method === 'DELETE');
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'owner', email: 'owner@example.com' });
    const request = await vi.waitFor(() => http.expectOne('/api/v1/me'));
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ token });
    expect(request.request.urlWithParams).not.toContain(token);
    expect(fixture.nativeElement.textContent).not.toContain(token);
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/account/deletion-requested', { replaceUrl: true }));
    expect(signOut).toHaveBeenCalledOnce(); fixture.destroy();
  });
  it('prompts an anonymous recipient to sign in without sending the token anywhere', async () => {
    signedIn.set(false);
    const fixture = TestBed.createComponent(AccountDeletionConfirm); await fixture.whenStable();
    TestBed.inject(HttpTestingController).expectNone(() => true);
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('genau dem Konto');
    expect(fixture.nativeElement.querySelector('a[target=_blank]').getAttribute('href')).not.toContain(token);
    fixture.destroy();
  });
  it.each(['hidden', 'prerendering'])('waits for activation of a %s page and submits only once', async state => {
    visible = state !== 'hidden';
    Object.defineProperty(document, 'prerendering', { configurable: true, value: state === 'prerendering' });
    const fixture = TestBed.createComponent(AccountDeletionConfirm);
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'owner' });
    await fixture.whenStable();
    http.expectNone(request => request.method === 'DELETE');
    visible = true;
    Object.defineProperty(document, 'prerendering', { configurable: true, value: false });
    document.dispatchEvent(new Event(state === 'hidden' ? 'visibilitychange' : 'prerenderingchange'));
    document.dispatchEvent(new Event('visibilitychange'));
    const request = await vi.waitFor(() => http.expectOne('/api/v1/me'));
    expect(request.request.method).toBe('DELETE');
    request.flush({}, { status: 404, statusText: 'Invalid confirmation' });
    await fixture.whenStable();
    document.dispatchEvent(new Event('visibilitychange'));
    http.expectNone(() => true);
    expect(signOut).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('reloads the profile after an identity switch and preserves the session when its token is rejected', async () => {
    visible = false;
    const fixture = TestBed.createComponent(AccountDeletionConfirm);
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'owner' });
    await fixture.whenStable();
    firebaseUserId.set('other');
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'other' });
    await fixture.whenStable();
    http.expectNone(request => request.method === 'DELETE');
    visible = true; document.dispatchEvent(new Event('visibilitychange'));
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({}, { status: 404, statusText: 'Invalid confirmation' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect(signOut).not.toHaveBeenCalled();
    http.expectNone(() => true);
    fixture.destroy();
  });
});
