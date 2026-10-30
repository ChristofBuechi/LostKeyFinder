import { TranslocoPipe } from '@jsverse/transloco';
import { translateSection } from '../core/translations';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Component, effect, inject, linkedSignal, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrentUserResponse } from '@lost-key-finder/api-client';
import { Auth } from '../auth/auth';

@Component({
  selector: 'app-account',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  protected readonly chatCopy = translateSection('chat');
  protected readonly tagCopy = translateSection('tags');
  protected readonly user = httpResource<CurrentUserResponse>(() => this.auth.signedIn() ? '/api/v1/me' : undefined);
  protected readonly signingOut = signal(false);
  protected readonly error = linkedSignal<'accountBlocked' | 'accountFailed' | 'sessionExpired' | 'logoutFailed' | null>(() => {
    const error = this.user.error();
    return !error ? null : error instanceof HttpErrorResponse && error.status === 403 ? 'accountBlocked'
      : error instanceof HttpErrorResponse && error.status === 401 ? 'sessionExpired' : 'accountFailed';
  });

  constructor() {
    effect(() => {
      if (!this.auth.signedIn()) void this.router.navigateByUrl('/#owner', { replaceUrl: true });
      else if (this.user.hasValue() && this.user.value().isAdmin && this.router.parseUrl(this.router.url).queryParams['entry'] === 'login') {
        void this.router.navigateByUrl('/admin/tags', { replaceUrl: true });
      }
    });
  }

  protected async logout(): Promise<void> {
    if (this.signingOut()) return;
    this.signingOut.set(true);
    try {
      await this.auth.signOut();
      await this.router.navigateByUrl('/#owner', { replaceUrl: true });
    } catch {
      this.error.set('logoutFailed');
    } finally {
      this.signingOut.set(false);
    }
  }
}
