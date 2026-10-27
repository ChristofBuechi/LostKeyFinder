import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

import de from '../../../../../packages/translations/de.json';
import en from '../../../../../packages/translations/en.json';

export type Language = 'de' | 'en';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  readonly language = signal<Language>(this.readStoredLanguage());
  readonly translations = computed(() => this.language() === 'de' ? de : en);
  readonly copy = computed(() => this.translations().common);

  constructor() {
    effect(() => {
      const language = this.language();
      this.document.documentElement.lang = language;
      this.document.title = this.copy().brand;
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
