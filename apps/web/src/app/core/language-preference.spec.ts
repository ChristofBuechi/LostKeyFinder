import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { Auth } from '../auth/auth';
import { LanguageService } from './language.service';
import { LanguagePreference } from './language-preference';

describe('LanguagePreference', () => {
  const signedIn = signal(false);
  const firebaseUserId = signal<string | null>(null);
  let http: HttpTestingController;
  let preference: LanguagePreference;
  let language: LanguageService;
  beforeEach(() => {
    TestBed.resetTestingModule(); localStorage.clear(); signedIn.set(false); firebaseUserId.set(null);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(),
      { provide: Auth, useValue: { signedIn, firebaseUserId } }] });
    http = TestBed.inject(HttpTestingController);
    language = TestBed.inject(LanguageService);
    preference = TestBed.inject(LanguagePreference);
    TestBed.tick();
  });
  afterEach(() => http.verify());

  function login(identity = 'owner', preferred = 'en') {
    firebaseUserId.set(identity); signedIn.set(true); TestBed.tick();
    expect(preference.busy()).toBe(true);
    http.expectOne('/api/v1/me').flush({ language: preferred }); TestBed.tick();
  }

  it('keeps anonymous choices local and loads the saved language on login', async () => {
    await preference.toggle(); TestBed.tick();
    expect(language.language()).toBe('en');
    expect(localStorage.getItem('language')).toBe('en');
    http.expectNone('/api/v1/me/language');
    login('owner', 'de');
    expect(language.language()).toBe('de');
    expect(document.documentElement.lang).toBe('de');
  });

  it('saves a language change and prevents overlapping writes', async () => {
    login();
    const save = preference.toggle();
    const request = http.expectOne('/api/v1/me/language');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ language: 'de' });
    await preference.toggle();
    expect(language.language()).toBe('de');
    http.expectNone('/api/v1/me/language');
    request.flush({ language: 'de' }); await save; TestBed.tick();
    expect(preference.busy()).toBe(false);
    expect(preference.failed()).toBe(false);
    http.expectNone('/api/v1/me');
  });

  it('restores the saved language and reports a failed write', async () => {
    login();
    const save = preference.toggle();
    http.expectOne('/api/v1/me/language').flush({}, { status: 503, statusText: 'Unavailable' });
    await save; TestBed.tick();
    expect(language.language()).toBe('en');
    expect(preference.failed()).toBe(true);
    expect(preference.busy()).toBe(false);
  });

  it('cancels an old profile request and ignores failed writes from an earlier account', async () => {
    signedIn.set(true); firebaseUserId.set('old'); TestBed.tick();
    const oldLoad = http.expectOne('/api/v1/me');
    firebaseUserId.set('owner'); TestBed.tick();
    expect(oldLoad.cancelled).toBe(true);
    http.expectOne('/api/v1/me').flush({ language: 'en' }); TestBed.tick();
    const oldSave = preference.toggle();
    const save = http.expectOne('/api/v1/me/language');
    firebaseUserId.set('finder'); TestBed.tick();
    http.expectOne('/api/v1/me').flush({ language: 'de' }); TestBed.tick();
    save.flush({}, { status: 503, statusText: 'Unavailable' }); await oldSave; TestBed.tick();
    expect(language.language()).toBe('de');
    expect(preference.failed()).toBe(false);
    expect(preference.busy()).toBe(false);
  });

  it('allows retry after a failed profile read and defaults legacy profiles to German', async () => {
    signedIn.set(true); firebaseUserId.set('owner'); TestBed.tick();
    http.expectOne('/api/v1/me').flush({}, { status: 503, statusText: 'Unavailable' }); TestBed.tick();
    expect(preference.failed()).toBe(true);
    const save = preference.toggle();
    http.expectOne('/api/v1/me/language').flush({ language: 'en' }); await save; TestBed.tick();
    expect(preference.failed()).toBe(false);
    signedIn.set(false); TestBed.tick(); login('legacy', '');
    expect(language.language()).toBe('de');
  });
});
