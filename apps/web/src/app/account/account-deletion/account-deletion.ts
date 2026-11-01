import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '@lost-key-finder/api-client';
import { Auth } from '../../auth/auth';
import { translateSection } from '../../core/translations';

@Component({
  selector: 'app-account-deletion', imports: [],
  template: `<button class="tag-action tag-action--danger" type="button" (click)="requestConfirmation()" [disabled]="busy()" aria-describedby="account-delete-hint">{{ busy() ? copy().busy : copy().title }}</button>
    <p id="account-delete-hint">{{ copy().resume }}</p>
    @if (failed()) { <p role="alert">{{ recentRequired() ? copy().recent : copy().failed }}</p> }
    @if (failed()) { <button type="button" (click)="signInAgain()" [disabled]="busy()">{{ copy().signInAgain }}</button> }`,
  styles: `:host { display: block; margin-block: 1.5rem; }`,
})
export class AccountDeletion {
  protected readonly copy = translateSection('deletion');
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  protected readonly recentRequired = signal(false);
  private readonly users = inject(UsersService);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  protected async requestConfirmation(): Promise<void> {
    if (this.busy()) return;
    const identity = this.auth.firebaseUserId();
    this.busy.set(true); this.failed.set(false); this.recentRequired.set(false);
    try {
      const response = await firstValueFrom(this.users.userControllerRequestDeletionEmail());
      if (this.destroy.destroyed || identity !== this.auth.firebaseUserId()) return;
      await this.router.navigateByUrl(response.confirmationRequired ? '/account/deletion-email-sent' : '/account/deletion-requested', { replaceUrl: true });
      if (!response.confirmationRequired && identity === this.auth.firebaseUserId()) {
        try { await this.auth.signOut(); } catch { this.auth.expireSession(); }
      }
    } catch (error) {
      if (!this.destroy.destroyed) { this.failed.set(true); this.recentRequired.set(error instanceof HttpErrorResponse && error.status === 409); }
    } finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
  protected async signInAgain(): Promise<void> {
    try { await this.auth.signOut(); await this.router.navigateByUrl('/#owner', { replaceUrl: true }); }
    catch { this.failed.set(true); }
  }
}
