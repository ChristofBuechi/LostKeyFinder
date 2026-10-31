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
  beforeEach(() => {
    localStorage.clear(); signedIn.set(true); firebaseUserId.set('owner'); signOut.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: DELETION_CONFIRMATION_TOKEN, useValue: token },
      { provide: Auth, useValue: { signedIn, firebaseUserId, signOut, expireSession: vi.fn() } }] });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('requires an explicit click and sends the secret only in the authenticated request body', async () => {
    const fixture = TestBed.createComponent(AccountDeletionConfirm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'owner', email: 'owner@example.com' });
    await fixture.whenStable();
    http.expectNone(request => request.method === 'DELETE');
    expect(fixture.nativeElement.textContent).not.toContain(token);
    expect(fixture.nativeElement.textContent).toContain('owner@example.com');
    const confirm = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    confirm.click(); confirm.click();
    const request = await vi.waitFor(() => http.expectOne('/api/v1/me'));
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ token });
    expect(request.request.urlWithParams).not.toContain(token);
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
  it('reloads the profile and clears the previous email when the Firebase identity changes', async () => {
    const fixture = TestBed.createComponent(AccountDeletionConfirm);
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'owner', email: 'owner@example.com' });
    await fixture.whenStable();
    firebaseUserId.set('other');
    const next = await vi.waitFor(() => http.expectOne('/api/v1/me'));
    expect(fixture.nativeElement.textContent).not.toContain('owner@example.com');
    next.flush({ id: 'other', email: 'other@example.com' }); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('other@example.com');
    http.expectNone(request => request.method === 'DELETE');
    fixture.destroy();
  });
  it('shows a failed confirmation and preserves the account session', async () => {
    const fixture = TestBed.createComponent(AccountDeletionConfirm);
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ id: 'other', email: 'other@example.com' });
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({}, { status: 404, statusText: 'Invalid confirmation' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect(signOut).not.toHaveBeenCalled(); fixture.destroy();
  });
});
