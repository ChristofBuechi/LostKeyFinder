import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, effect, inject, signal, untracked } from '@angular/core';
import { CurrentUserResponse } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../auth/auth';
import { LanguageService } from './language.service';

@Injectable({ providedIn: 'root' })
export class LanguagePreference {
  private readonly auth = inject(Auth);
  private readonly http = inject(HttpClient);
  private readonly language = inject(LanguageService);
  private readonly destroy = inject(DestroyRef);
  readonly busy = signal(false);
  readonly failed = signal(false);

  constructor() {
    effect(onCleanup => {
      const identity = this.auth.signedIn() ? this.auth.firebaseUserId() : null;
      this.failed.set(false);
      this.busy.set(!!identity);
      if (!identity) return;
      // Only session changes load the profile; changing the language must not reload it.
      const request = untracked(() => this.http.get<CurrentUserResponse>('/api/v1/me').subscribe({
        next: user => {
          if (!this.isCurrent(identity)) return;
          this.language.setLanguage(user.language === 'en' ? 'en' : 'de');
          this.busy.set(false);
        },
        error: () => {
          if (this.isCurrent(identity)) { this.failed.set(true); this.busy.set(false); }
        },
      }));
      onCleanup(() => request.unsubscribe());
    });
  }

  async toggle(): Promise<void> {
    if (this.busy()) return;
    const identity = this.auth.signedIn() ? this.auth.firebaseUserId() : null;
    const previous = this.language.language();
    this.language.toggle();
    this.failed.set(false);
    if (!identity) return;
    this.busy.set(true);
    try {
      await firstValueFrom(this.http.patch<CurrentUserResponse>('/api/v1/me/language', { language: this.language.language() }));
    } catch {
      if (this.isCurrent(identity)) { this.language.setLanguage(previous); this.failed.set(true); }
    } finally {
      if (this.isCurrent(identity)) this.busy.set(false);
    }
  }

  private isCurrent(identity: string): boolean {
    return !this.destroy.destroyed && this.auth.signedIn() && this.auth.firebaseUserId() === identity;
  }
}
