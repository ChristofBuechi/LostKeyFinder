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
  beforeEach(() => {
    localStorage.clear();
    signOut.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn: signal(true), firebaseUserId: signal('user'), signOut } }] });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('requires confirmation and completes all bounded steps before signing out', async () => {
    const fixture = TestBed.createComponent(AccountDeletion);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    const remove = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(remove.disabled).toBe(true);
    (fixture.nativeElement.querySelector('input') as HTMLInputElement).click();
    await fixture.whenStable(); remove.click();
    const http = TestBed.inject(HttpTestingController);
    const first = await vi.waitFor(() => http.expectOne('/api/v1/me'));
    expect(first.request.method).toBe('DELETE'); first.flush({ completed: false });
    expect(signOut).not.toHaveBeenCalled();
    (await vi.waitFor(() => http.expectOne('/api/v1/me'))).flush({ completed: true });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/account/deleted', { replaceUrl: true }));
    expect(signOut).toHaveBeenCalledOnce(); fixture.destroy();
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
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('noch nicht abgeschlossen');
    fixture.destroy();
  });
});
