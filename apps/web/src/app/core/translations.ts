import { Injectable, Signal, isDevMode } from '@angular/core';
import { TranslocoLoader, provideTransloco, translateObjectSignal } from '@jsverse/transloco';
import { of } from 'rxjs';
import de from '../../../../../packages/translations/de.json';
import en from '../../../../../packages/translations/en.json';

export type TranslationCatalog = typeof de;

@Injectable({ providedIn: 'root' })
class BundledTranslations implements TranslocoLoader {
  getTranslation(language: string) { return of(language === 'en' ? en : de); }
}

export function provideTranslations() {
  return provideTransloco({
    config: {
      availableLangs: ['de', 'en'],
      defaultLang: 'de',
      fallbackLang: 'de',
      missingHandler: { useFallbackTranslation: true },
      reRenderOnLangChange: true,
      prodMode: !isDevMode(),
      interpolation: ['{', '}'],
    },
    loader: BundledTranslations,
  });
}

// Transloco's object API is untyped; preserve the catalog's keys for TypeScript and templates.
export function translateSection<K extends keyof TranslationCatalog>(key: K): Signal<TranslationCatalog[K]> {
  return translateObjectSignal(key) as unknown as Signal<TranslationCatalog[K]>;
}
