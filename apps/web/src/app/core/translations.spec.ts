import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoPipe } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { LanguageService } from './language.service';
import { translateSection } from './translations';

@Component({ imports: [TranslocoPipe], template: `
  <h1>{{ 'common.loginPageTitle' | transloco }}</h1>
  <p class="tags">{{ tags().mine }}</p>
  <p class="mail">{{ 'emails.unreadMessages.body' | transloco: { chatUrl: link } }}</p>
` })
class TranslatedPage {
  readonly tags = translateSection('tags');
  readonly link = 'https://example.test/chats/test';
}

describe('Shared Transloco catalogs', () => {
  beforeEach(() => {
    TestBed.resetTestingModule(); localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  it('renders pipes, typed object signals and shared placeholders in both languages', async () => {
    const language = TestBed.inject(LanguageService);
    const fixture = TestBed.createComponent(TranslatedPage); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Wie können wir dir helfen?');
    expect(fixture.nativeElement.querySelector('.tags').textContent).toBe('Meine Tags');
    expect(fixture.nativeElement.querySelector('.mail').textContent).toContain('Du hast neue Nachrichten.');
    expect(fixture.nativeElement.querySelector('.mail').textContent).toContain(fixture.componentInstance.link);
    language.toggle(); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('How can we help?');
    expect(fixture.nativeElement.querySelector('.tags').textContent).toBe('My tags');
    expect(fixture.nativeElement.querySelector('.mail').textContent).toContain('You have new messages.');
    expect(fixture.nativeElement.querySelector('.mail').textContent).toContain(fixture.componentInstance.link);
    expect(fixture.nativeElement.textContent).not.toContain('{chatUrl}');
    expect(document.documentElement.lang).toBe('en');
    expect(document.title).toBe('Lost Key Finder');
  });

  it('uses a stored English preference for the first render', async () => {
    localStorage.setItem('language', 'en');
    TestBed.inject(LanguageService);
    const fixture = TestBed.createComponent(TranslatedPage); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('How can we help?');
    expect(fixture.nativeElement.querySelector('.tags').textContent).toBe('My tags');
  });
});
