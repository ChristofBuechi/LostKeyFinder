import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { Auth } from './auth/auth';
import { App } from './app';

describe('App', () => {
  const signedIn = signal(false);
  beforeEach(() => {
    TestBed.resetTestingModule(); localStorage.clear(); signedIn.set(false);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideRouter([]), { provide: Auth, useValue: { signedIn } }] });
  });

  it('creates an accessible shell with a visible login entry', async () => {
    const fixture = TestBed.createComponent(App); await fixture.whenStable();
    expect(fixture.componentInstance).toBeInstanceOf(App);
    expect(fixture.nativeElement.querySelector('.skip-link').getAttribute('href')).toBe('#main-content');
    expect(fixture.nativeElement.querySelector('main#main-content').getAttribute('tabindex')).toBe('-1');
    expect(fixture.nativeElement.querySelector('.language-toggle').getAttribute('aria-label')).toContain('Auf Englisch wechseln');
    expect(fixture.nativeElement.querySelector('.account-link').textContent).toContain('Anmelden');
    expect(fixture.nativeElement.querySelector('.account-link').getAttribute('href')).toBe('/login#owner');
  });

  it('offers the account after the session changes', async () => {
    const fixture = TestBed.createComponent(App); await fixture.whenStable();
    signedIn.set(true); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.account-link').textContent).toContain('Mein Konto');
    expect(fixture.nativeElement.querySelector('.account-link').getAttribute('href')).toBe('/account');
  });
});
