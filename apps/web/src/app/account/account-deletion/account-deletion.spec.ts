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
  it('submits only once and signs out as soon as the backend accepts the job', async () => {
    const fixture = TestBed.createComponent(AccountDeletion);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    const remove = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(remove.disabled).toBe(true);
    (fixture.nativeElement.querySelector('input') as HTMLInputElement).click();
    await fixture.whenStable(); remove.click();
    const http = TestBed.inject(HttpTestingController);
    const first = await vi.waitFor(() => http.expectOne('/api/v1/me'));
    expect(first.request.method).toBe('DELETE');
    first.flush(null, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/account/deletion-requested', { replaceUrl: true }));
    http.expectNone('/api/v1/me');
    expect(signOut).toHaveBeenCalledOnce(); fixture.destroy();
  });
  it('shows acceptance even if Firebase SDK sign-out fails', async () => {
    signOut.mockRejectedValueOnce(new Error('SDK offline'));
    const fixture = TestBed.createComponent(AccountDeletion);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('input') as HTMLInputElement).click(); await fixture.whenStable();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    (await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne('/api/v1/me'))).flush(null, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/account/deletion-requested', { replaceUrl: true }));
    expect(expireSession).toHaveBeenCalledOnce();
    fixture.destroy();
  });
  it('offers a fresh login after an expired authentication and allows retry after transport failure', async () => {
    const fixture = TestBed.createComponent(AccountDeletion);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('input') as HTMLInputElement).click(); await fixture.whenStable();
    const remove = fixture.nativeElement.querySelector('button') as HTMLButtonElement; remove.click();
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({}, { status: 409, statusText: 'Recent login required' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('frischen E-Mail-Link');
    expect(signOut).not.toHaveBeenCalled();
    remove.click();
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({}, { status: 503, statusText: 'Offline' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Annahme konnte nicht bestätigt werden');
    fixture.destroy();
  });
});
