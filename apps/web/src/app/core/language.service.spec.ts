import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { LanguageService } from './language.service';

describe('LanguageService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('starts in German and updates the document language', () => {
    const service = TestBed.inject(LanguageService);
    TestBed.tick();

    expect(service.language()).toBe('de');
    expect(document.documentElement.lang).toBe('de');
  });

  it('persists a language change', () => {
    const service = TestBed.inject(LanguageService);
    service.toggle();
    TestBed.tick();

    expect(service.language()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
    expect(localStorage.getItem('language')).toBe('en');
  });
});
