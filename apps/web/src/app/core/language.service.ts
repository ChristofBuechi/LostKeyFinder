import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject } from '@angular/core';
import { TranslocoService, translateSignal } from '@jsverse/transloco';

export type Language = 'de' | 'en';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  private readonly translations = inject(TranslocoService);
  readonly language = computed<Language>(() => this.translations.activeLang() === 'en' ? 'en' : 'de');
  private readonly brand = translateSignal('common.brand');

  constructor() {
    this.setLanguage(localStorage.getItem('language') === 'en' ? 'en' : 'de');
    effect(() => {
      const language = this.language();
      this.document.documentElement.lang = language;
      this.document.title = this.brand();
      localStorage.setItem('language', language);
    });
  }

  setLanguage(language: Language): void { this.translations.setActiveLang(language); }

  toggle(): void { this.setLanguage(this.language() === 'de' ? 'en' : 'de'); }
}
