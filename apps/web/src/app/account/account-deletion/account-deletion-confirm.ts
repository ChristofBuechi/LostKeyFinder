import { DOCUMENT } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrentUserResponse, UsersService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../auth/auth';
import { DELETION_CONFIRMATION_TOKEN } from '../../core/deletion-confirmation';
import { translateSection } from '../../core/translations';

@Component({ selector: 'app-account-deletion-confirm', imports: [RouterLink],
  host: { '(document:visibilitychange)': 'start()', '(document:prerenderingchange)': 'start()' },
  template: `<section class="auth-panel" aria-labelledby="confirm-title">
    <h1 id="confirm-title">{{ copy().confirmTitle }}</h1>
    @if (!token) { <p role="alert">{{ copy().openEmail }}</p> }
    @if (busy()) { <p role="status">{{ copy().busy }}</p> }
    @if (!auth.signedIn()) { <p>{{ copy().loginRequired }}</p> }
    @if (failed() || me.error()) { <p role="alert">{{ copy().invalidConfirmation }}</p> }
    @if (failed()) { <button type="button" (click)="retry()">{{ common().retry }}</button> }
    <p><a routerLink="/" fragment="owner" target="_blank" rel="noopener">{{ copy().loginOtherTab }}</a></p>
    <p><a routerLink="/account">{{ copy().back }}</a></p>
  </section>`,
})
export class AccountDeletionConfirm {
  protected readonly copy = translateSection('deletion');
  protected readonly common = translateSection('common');
  protected readonly auth = inject(Auth);
  protected readonly token = inject(DELETION_CONFIRMATION_TOKEN);
  protected readonly me = httpResource<CurrentUserResponse>(() => this.auth.signedIn() && this.auth.firebaseUserId() ? '/api/v1/me' : undefined);
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  private readonly users = inject(UsersService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  private readonly document = inject(DOCUMENT);
  private attempted: string | null = null;

  constructor() {
    effect(() => {
      const identity = this.auth.signedIn() ? this.auth.firebaseUserId() : null;
      const ready = this.me.hasValue() && !this.me.isLoading();
      this.busy();
      if (!identity) this.attempted = null;
      else if (ready) untracked(() => this.start());
    });
  }

  protected start(): void {
    const identity = this.auth.firebaseUserId();
    if (!identity || !this.auth.signedIn() || !this.token || !this.me.hasValue() || this.me.isLoading() || this.busy() ||
      this.attempted === identity || this.document.visibilityState !== 'visible' ||
      ('prerendering' in this.document && this.document.prerendering === true)) return;
    this.attempted = identity;
    void this.confirm();
  }

  protected retry(): void { this.attempted = null; this.start(); }

  private async confirm(): Promise<void> {
    if (this.busy() || !this.token || !this.auth.signedIn() || !this.me.hasValue()) return;
    const identity = this.auth.firebaseUserId();
    this.busy.set(true); this.failed.set(false);
    try {
      await firstValueFrom(this.users.userControllerDelete({ body: { token: this.token } }));
      if (this.destroy.destroyed || this.auth.firebaseUserId() !== identity) return;
      await this.router.navigateByUrl('/account/deletion-requested', { replaceUrl: true });
      if (this.auth.firebaseUserId() === identity) {
        try { await this.auth.signOut(); } catch { this.auth.expireSession(); }
      }
    } catch { if (!this.destroy.destroyed) this.failed.set(true); }
    finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
}
