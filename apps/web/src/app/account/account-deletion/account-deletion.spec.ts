import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { AccountDeletion } from './account-deletion';
describe('Account deletion', () => {
  const signOut = vi.fn();
  const expireSession = vi.fn();
  beforeEach(() => {
    localStorage.clear();
    expireSession.mockReset();
    signOut.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn: signal(true), firebaseUserId: signal('user'), signOut, expireSession } }] });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('requests a confirmation email without deleting or signing out', async () => {
    const fixture = TestBed.createComponent(AccountDeletion);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    const remove = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(remove.disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('input[type=checkbox]')).toBeNull();
    remove.click();
    const http = TestBed.inject(HttpTestingController);
    const first = await vi.waitFor(() => http.expectOne('/api/v1/me/deletion-confirmations'));
    expect(first.request.method).toBe('POST');
    first.flush({ confirmationRequired: true }, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/account/deletion-email-sent', { replaceUrl: true }));
    http.expectNone(request => request.method === 'DELETE');
    expect(signOut).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('acknowledges an already confirmed job even if Firebase SDK sign-out fails', async () => {
    signOut.mockRejectedValueOnce(new Error('SDK offline'));
    const fixture = TestBed.createComponent(AccountDeletion);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    (await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne('/api/v1/me/deletion-confirmations'))).flush({ confirmationRequired: false }, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/account/deletion-requested', { replaceUrl: true }));
    expect(expireSession).toHaveBeenCalledOnce();
    fixture.destroy();
  });
  it('offers a fresh login after an expired authentication and allows retry after transport failure', async () => {
    const fixture = TestBed.createComponent(AccountDeletion);
    await fixture.whenStable();
    const remove = fixture.nativeElement.querySelector('button') as HTMLButtonElement; remove.click();
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me/deletion-confirmations'))).flush({}, { status: 409, statusText: 'Recent login required' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('frischen E-Mail-Link');
    expect(signOut).not.toHaveBeenCalled();
    remove.click();
    (await vi.waitFor(() => http.expectOne('/api/v1/me/deletion-confirmations'))).flush({}, { status: 503, statusText: 'Offline' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Annahme konnte nicht bestätigt werden');
    fixture.destroy();
  });
});
