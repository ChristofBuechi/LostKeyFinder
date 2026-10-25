import { HealthService } from '@lost-key-finder/api-client';
import { of } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { LanguageService } from '../../core/language.service';
import { HomeEntry } from './home-entry';
import { routes } from '../../app.routes';

// Finder and owner forms are separate; owner submission never interprets the tag input.
describe('Owner login form and sent page', () => {
  const sendLink = vi.fn();
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    sendLink.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [{ provide: HealthService, useValue: { healthControllerReady: () => of({ status: 'ok' }) } }, provideZonelessChangeDetection(), provideRouter(routes),
      { provide: Auth, useValue: { configured: true, signedIn: signal(false), sendLink } }] });
  });

  it('shows an associated error and never sends an invalid email', async () => {
    const fixture = TestBed.createComponent(HomeEntry);
    await fixture.whenStable();
    fixture.nativeElement.querySelector('#owner-login').dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#email');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toContain('email-error');
    expect(fixture.nativeElement.querySelector('#email-error').textContent).toContain('gültige E-Mail');
    expect(sendLink).not.toHaveBeenCalled();
  });

  it('replaces the form with the sent page only after successful delivery request', async () => {
    const harness = await RouterTestingHarness.create('/login');
    const input = harness.routeNativeElement!.querySelector('#email') as HTMLInputElement;
    input.value = 'owner@example.com';
    input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelector('#owner-login')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await harness.fixture.whenStable();
    expect(sendLink).toHaveBeenCalledExactlyOnceWith('owner@example.com');
    expect(TestBed.inject(Router).url).toBe('/login/link-sent');
    expect(harness.routeNativeElement!.textContent).toContain('Du kannst diese Seite jetzt schliessen');
    expect(harness.routeNativeElement!.textContent).toContain('Spam- oder Junk-Ordner');
    expect(harness.routeNativeElement!.querySelector('form')).toBeNull();
    expect(TestBed.inject(Router).lastSuccessfulNavigation()?.extras.replaceUrl).toBe(true);
    TestBed.inject(LanguageService).toggle();
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement!.textContent).toContain('You can close this page now');
    await harness.navigateByUrl('/login/link-sent');
    expect(sendLink).toHaveBeenCalledTimes(1);
  });

  it('stays on login and shows a safe error if Firebase rejects sending', async () => {
    sendLink.mockRejectedValue(new Error('private upstream detail'));
    const harness = await RouterTestingHarness.create('/login');
    const input = harness.routeNativeElement!.querySelector('#email') as HTMLInputElement;
    input.value = 'owner@example.com'; input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelector('#owner-login')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/#owner');
    expect(harness.routeNativeElement!.querySelector('#owner-login [role="alert"]')!.textContent).toContain('nicht gesendet');
    expect(harness.routeNativeElement!.textContent).not.toContain('private upstream detail');
  });

  it('does not submit twice while a request is in flight', async () => {
    let finish!: () => void;
    sendLink.mockReturnValue(new Promise<void>(resolve => { finish = resolve; }));
    const fixture = TestBed.createComponent(HomeEntry);
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#email');
    input.value = 'owner@example.com'; input.dispatchEvent(new Event('input'));
    const ownerForm = fixture.nativeElement.querySelector('#owner-login');
    ownerForm.dispatchEvent(new Event('submit', { cancelable: true }));
    ownerForm.dispatchEvent(new Event('submit', { cancelable: true }));
    expect(sendLink).toHaveBeenCalledTimes(1);
    finish();
    await fixture.whenStable();
  });

  it('does not leave the current route when an earlier email request completes late', async () => {
    let finish!: () => void;
    sendLink.mockReturnValue(new Promise<void>(resolve => { finish = resolve; }));
    const harness = await RouterTestingHarness.create('/');
    const input = harness.routeNativeElement!.querySelector('#email') as HTMLInputElement;
    input.value = 'owner@example.com'; input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelector('#owner-login')!.dispatchEvent(new Event('submit', { cancelable: true }));
    expect(sendLink).toHaveBeenCalledTimes(1);
    const router = TestBed.inject(Router);
    const destination = '/f/123e4567-e89b-42d3-a456-426614174000';
    await router.navigateByUrl(destination);
    const navigate = vi.spyOn(router, 'navigateByUrl');
    finish();
    await harness.fixture.whenStable();
    expect(router.url).toBe(destination);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('prominently offers a tag input and camera scan without requiring owner authentication', async () => {
    const fixture = TestBed.createComponent(HomeEntry);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#finder #tag-id')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#finder .scan-button').textContent).toContain('QR-Code scannen');
    expect(fixture.nativeElement.querySelector('#email')).not.toBeNull();
  });
});
