import { httpResource } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrentUserResponse, UsersService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../auth/auth';
import { DELETION_CONFIRMATION_TOKEN } from '../../core/deletion-confirmation';
import { translateSection } from '../../core/translations';

@Component({ selector: 'app-account-deletion-confirm', imports: [RouterLink],
  template: `<section class="auth-panel" aria-labelledby="confirm-title">
    <h1 id="confirm-title">{{ copy().confirmTitle }}</h1><p>{{ copy().consequences }}</p><p>{{ copy().history }}</p>
    @if (!token) { <p role="alert">{{ copy().openEmail }}</p> }
    @if (auth.signedIn()) {
      @if (me.hasValue()) { <p>{{ copy().signedInAs }}: {{ me.value().email }}</p> }
      <button type="button" (click)="confirm()" [disabled]="busy() || !token || !me.hasValue()">{{ busy() ? copy().busy : copy().remove }}</button>
    } @else { <p>{{ copy().loginRequired }}</p> }
    <p><a routerLink="/" fragment="owner" target="_blank" rel="noopener">{{ copy().loginOtherTab }}</a></p>
    @if (failed() || me.error()) { <p role="alert">{{ copy().invalidConfirmation }}</p> }
    <p><a routerLink="/account">{{ copy().back }}</a></p>
  </section>`,
})
export class AccountDeletionConfirm {
  protected readonly copy = translateSection('deletion');
  protected readonly auth = inject(Auth);
  protected readonly token = inject(DELETION_CONFIRMATION_TOKEN);
  protected readonly me = httpResource<CurrentUserResponse>(() => this.auth.signedIn() && this.auth.firebaseUserId() ? '/api/v1/me' : undefined);
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  private readonly users = inject(UsersService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  protected async confirm(): Promise<void> {
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
