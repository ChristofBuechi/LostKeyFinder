import { Injectable, computed, inject } from '@angular/core';
import { LanguageService } from '../core/language.service';

@Injectable({ providedIn: 'root' })
export class TagCopy {
  private readonly language = inject(LanguageService);
  readonly text = computed(() => this.language.translations().tags);
}
