import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { UsersService } from '@lost-key-finder/api-client';
import { authGuard } from './auth/auth-guard';
import { Auth } from './auth/auth';
import { App } from './app';

@Component({ template: '<p id="private-content">Private information</p>' })
class ProtectedPage {}
@Component({ template: '<p id="public-content">Public login</p>' })
class PublicPage {}

describe('App', () => {
  const signedIn = signal(false);
  const firebaseUserId = signal('firebase-owner');
  beforeEach(() => {
    TestBed.resetTestingModule(); localStorage.clear(); signedIn.set(false); firebaseUserId.set('firebase-owner');
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideRouter([]), { provide: Auth, useValue: { signedIn, firebaseUserId } }] });
  });

  it('creates an accessible shell with a visible login entry', async () => {
    const fixture = TestBed.createComponent(App); await fixture.whenStable();
    expect(fixture.componentInstance).toBeInstanceOf(App);
    expect(fixture.nativeElement.querySelector('.skip-link').getAttribute('href')).toBe('#main-content');
    expect(fixture.nativeElement.querySelector('main#main-content').getAttribute('tabindex')).toBe('-1');
    expect(fixture.nativeElement.querySelector('.language-toggle').getAttribute('aria-label')).toContain('Auf Englisch wechseln');
    expect(fixture.nativeElement.querySelector('.account-link').textContent).toContain('Anmelden');
    expect(fixture.nativeElement.querySelector('.account-link').getAttribute('href')).toBe('/#owner');
  });

  it('offers the account after the session changes', async () => {
    const fixture = TestBed.createComponent(App); await fixture.whenStable();
    signedIn.set(true); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.account-link').textContent).toContain('Mein Konto');
    expect(fixture.nativeElement.querySelector('.account-link').getAttribute('href')).toBe('/account');
  });
  it('removes a protected page immediately when its session ends and blocks returning by direct link', async () => {
    signedIn.set(true);
    TestBed.configureTestingModule({ providers: [provideRouter([
      { path: '', component: PublicPage },
      { path: 'private', component: ProtectedPage, canActivate: [authGuard] },
    ]), { provide: UsersService, useValue: { userControllerMe: () => of({ status: 'ACTIVE', isAdmin: false }) } }] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/private'); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#private-content')).not.toBeNull();
    signedIn.set(false); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#private-content')).toBeNull();
    expect(router.url).toBe('/#owner');
    await router.navigateByUrl('/private'); await fixture.whenStable();
    expect(router.url).toBe('/#owner');
    expect(fixture.nativeElement.querySelector('#private-content')).toBeNull();
  });

  it('drops the old private view when another tab switches to a different Firebase account', async () => {
    signedIn.set(true);
    TestBed.configureTestingModule({ providers: [provideRouter([
      { path: '', component: PublicPage }, { path: 'private', component: ProtectedPage, canActivate: [authGuard] },
    ]), { provide: UsersService, useValue: { userControllerMe: () => of({ status: 'ACTIVE', isAdmin: false }) } }] });
    const fixture = TestBed.createComponent(App); await fixture.whenStable();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/private'); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#private-content')).not.toBeNull();
    firebaseUserId.set('firebase-finder'); await fixture.whenStable();
    expect(router.url).toBe('/#owner');
    expect(fixture.nativeElement.querySelector('#private-content')).toBeNull();
    expect(signedIn()).toBe(true);
  });

});
