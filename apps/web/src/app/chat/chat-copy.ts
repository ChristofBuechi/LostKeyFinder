import { Injectable, computed, inject } from '@angular/core';
import { LanguageService } from '../core/language.service';

@Injectable({ providedIn: 'root' })
export class ChatCopy {
  private readonly language = inject(LanguageService);
  readonly text = computed(() => this.language.translations().chat);
}
