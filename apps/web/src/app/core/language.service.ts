import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type Language = 'de' | 'en';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  readonly language = signal<Language>(this.readStoredLanguage());
  readonly copy = computed(() => this.language() === 'de' ? {
    skip: 'Zum Inhalt springen',
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
    notFoundTitle: 'Diese Seite ist nicht zurückzufinden.',
    notFoundLink: 'Zur Startseite',
    account: 'Mein Konto',
    loginTitle: 'Dein sicherer Zugang.',
    loginIntro: 'Melde dich mit einem einmaligen E-Mail-Link an. Du brauchst kein Passwort.',
    email: 'E-Mail-Adresse',
    emailHint: 'Öffne den Link in diesem Browser auf diesem Gerät.',
    emailInvalid: 'Bitte gib eine gültige E-Mail-Adresse ein.',
    sendLink: 'Anmeldelink senden',
    sendingLink: 'Link wird gesendet …',
    linkSent: 'Prüfe dein Postfach. Der Link kann nur einmal verwendet werden.',
    linkFailed: 'Der Link konnte nicht gesendet werden. Bitte versuche es später erneut.',
    authUnavailable: 'Die Anmeldung ist derzeit nicht verfügbar.',
    callbackChecking: 'Dein Anmeldelink wird geprüft …',
    callbackFailed: 'Dieser Link ist ungültig, abgelaufen oder gehört zu einem anderen Browser. Fordere einen neuen Link an.',
    backToLogin: 'Zur Anmeldung',
    accountLoading: 'Dein Konto wird geladen …',
    accountIntro: 'Du bist angemeldet. Hier verwaltest du künftig deine Tags.',
    accountEmpty: 'Du hast noch keine Tags. Die Tag-Erstellung folgt im nächsten Schritt.',
    accountBlocked: 'Für dieses Konto ist der Zugriff gesperrt.',
    accountFailed: 'Dein Konto konnte nicht geladen werden. Bitte versuche es erneut.',
    sessionExpired: 'Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.',
    retry: 'Erneut versuchen',
    logout: 'Abmelden',
    logoutFailed: 'Die Abmeldung konnte nicht abgeschlossen werden. Bitte versuche es erneut.',
  } : {
    skip: 'Skip to content',
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
    notFoundTitle: 'This page could not be found.',
    notFoundLink: 'Back to home',
    account: 'My account',
    loginTitle: 'Your secure way in.',
    loginIntro: 'Sign in with a one-time email link. No password needed.',
    email: 'Email address',
    emailHint: 'Open the link in this browser on this device.',
    emailInvalid: 'Please enter a valid email address.',
    sendLink: 'Send sign-in link',
    sendingLink: 'Sending link …',
    linkSent: 'Check your inbox. The link can only be used once.',
    linkFailed: 'The link could not be sent. Please try again later.',
    authUnavailable: 'Sign-in is currently unavailable.',
    callbackChecking: 'Checking your sign-in link …',
    callbackFailed: 'This link is invalid, expired or belongs to another browser. Request a new link.',
    backToLogin: 'Back to sign-in',
    accountLoading: 'Loading your account …',
    accountIntro: 'You are signed in. This is where you will manage your tags.',
    accountEmpty: 'You have no tags yet. Tag creation is coming in the next step.',
    accountBlocked: 'Access to this account is blocked.',
    accountFailed: 'Your account could not be loaded. Please try again.',
    sessionExpired: 'Your session has expired. Please sign in again.',
    retry: 'Try again',
    logout: 'Sign out',
    logoutFailed: 'Sign-out could not be completed. Please try again.',
  });

  constructor() {
    effect(() => {
      const language = this.language();
      this.document.documentElement.lang = language;
      localStorage.setItem('language', language);
    });
  }

  private readStoredLanguage(): Language {
    return localStorage.getItem('language') === 'en' ? 'en' : 'de';
  }

  toggle(): void {
    this.language.update((current) => current === 'de' ? 'en' : 'de');
  }

}
