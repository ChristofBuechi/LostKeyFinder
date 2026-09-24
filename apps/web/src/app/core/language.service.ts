import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, effect, inject, signal } from '@angular/core';

export type Language = 'de' | 'en';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);
  readonly language = signal<Language>(this.readStoredLanguage());
  readonly copy = computed(() => this.language() === 'de' ? {
    skip: 'Zum Inhalt springen',
    home: 'Lost Key Finder Startseite',
    switchLabel: 'Auf Englisch wechseln',
    languageName: 'Deutsch · English',
    footer: 'Verloren ist nicht für immer.',
    eyebrow: 'Sicher zurückgeben',
    title: 'Ein verlorener Gegenstand. Ein sicherer Weg zurück.',
    intro: 'Lost Key Finder verbindet Finder und Besitzer über einen QR-Tag, ohne private Kontaktdaten offenzulegen.',
    how: 'So funktioniert es',
    found: 'Ich habe etwas gefunden',
    promise: 'Keine Namen. Kein Umweg. Nur der direkte Weg zurück.',
    stepsEyebrow: 'In drei Schritten',
    stepsTitle: 'Rückgabe, ohne mehr preiszugeben als nötig.',
    scan: 'Scannen',
    scanText: 'Finder scannen den QR-Code auf deinem Tag und hinterlassen eine Nachricht.',
    confirm: 'Bestätigen',
    confirmText: 'Eine sichere E-Mail-Bestätigung schützt beide Seiten vor Missbrauch.',
    connect: 'Verbinden',
    connectText: 'Ihr schreibt privat miteinander und vereinbart die Rückgabe selbst.',
    finderEyebrow: 'Für Finder',
    finderTitle: 'Du hast etwas gefunden?',
    finderText: 'Scanne den Tag. Wir kümmern uns um den sicheren ersten Kontakt.',
    promiseLabel: 'Produktversprechen',
    checking: 'Verbindung wird geprüft',
    available: 'Dienst verfügbar',
    unavailable: 'Dienst vorübergehend nicht verfügbar',
    notFoundCode: '404',
    notFoundTitle: 'Diese Seite ist nicht zurückzufinden.',
    notFoundLink: 'Zur Startseite',
  } : {
    skip: 'Skip to content',
    home: 'Lost Key Finder home',
    switchLabel: 'Switch to German',
    languageName: 'English · Deutsch',
    footer: 'Lost does not have to mean gone forever.',
    eyebrow: 'Return with confidence',
    title: 'A lost item. A safe way back.',
    intro: 'Lost Key Finder connects finders and owners through a QR tag without exposing private contact details.',
    how: 'How it works',
    found: 'I found something',
    promise: 'No names. No detours. Just a direct way back.',
    stepsEyebrow: 'Three simple steps',
    stepsTitle: 'Return what matters, without sharing more than necessary.',
    scan: 'Scan',
    scanText: 'Finders scan the QR code on your tag and leave a message.',
    confirm: 'Confirm',
    confirmText: 'A secure email confirmation protects both sides from misuse.',
    connect: 'Connect',
    connectText: 'You chat privately and arrange the return between you.',
    finderEyebrow: 'For finders',
    finderTitle: 'Found something?',
    finderText: 'Scan the tag. We take care of the safe first contact.',
    promiseLabel: 'Product promise',
    checking: 'Checking connection',
    available: 'Service available',
    unavailable: 'Service temporarily unavailable',
    notFoundCode: '404',
    notFoundTitle: 'This page could not be found.',
    notFoundLink: 'Back to home',
  });

  constructor() {
    effect(() => {
      const language = this.language();
      this.document.documentElement.lang = language;
      if (isPlatformBrowser(this.platformId)) {
        localStorage.setItem('language', language);
      }
    });
  }

  toggle(): void {
    this.language.update((current) => current === 'de' ? 'en' : 'de');
  }

  private readStoredLanguage(): Language {
    if (!isPlatformBrowser(this.platformId)) {
      return 'de';
    }
    return localStorage.getItem('language') === 'en' ? 'en' : 'de';
  }
}
