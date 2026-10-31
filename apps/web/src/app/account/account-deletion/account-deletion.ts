import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '@lost-key-finder/api-client';
import { Auth } from '../../auth/auth';
import { translateSection } from '../../core/translations';

@Component({
  selector: 'app-account-deletion', imports: [RouterLink],
  template: `<section class="auth-panel" aria-labelledby="delete-title">
    <h1 id="delete-title">{{ copy().title }}</h1><p>{{ copy().consequences }}</p><p>{{ copy().history }}</p>
    <label><input type="checkbox" [checked]="confirmed()" (change)="confirmed.set(!confirmed())" [disabled]="busy()"> {{ copy().confirm }}</label>
    <button type="button" (click)="remove()" [disabled]="!confirmed() || busy()">{{ busy() ? copy().busy : copy().remove }}</button>
    @if (failed()) { <p role="alert">{{ recentRequired() ? copy().recent : copy().failed }}</p> }
    @if (failed()) { <button type="button" (click)="signInAgain()" [disabled]="busy()">{{ copy().signInAgain }}</button> }
    <p>{{ copy().resume }}</p><a routerLink="/account">{{ copy().back }}</a>
  </section>`,
  styles: `.auth-panel input { width: auto; } label { margin-block: 1rem; }`,
})
export class AccountDeletion {
  protected readonly copy = translateSection('deletion');
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  protected readonly recentRequired = signal(false);
  protected readonly confirmed = signal(false);
  private readonly users = inject(UsersService);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  protected async remove(): Promise<void> {
    if (this.busy() || !this.confirmed()) return;
    const identity = this.auth.firebaseUserId();
    this.busy.set(true); this.failed.set(false); this.recentRequired.set(false);
    try {
      let completed = false;
      while (!completed && !this.destroy.destroyed && this.auth.signedIn() && identity === this.auth.firebaseUserId()) {
        const response = await firstValueFrom(this.users.userControllerDelete());
        completed = response.completed;
      }
      if (!completed || this.destroy.destroyed || identity !== this.auth.firebaseUserId()) return;
      await this.auth.signOut();
      await this.router.navigateByUrl('/account/deleted', { replaceUrl: true });
    } catch (error) {
      if (!this.destroy.destroyed) { this.failed.set(true); this.recentRequired.set(error instanceof HttpErrorResponse && error.status === 409); }
    } finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
  protected async signInAgain(): Promise<void> {
    try { await this.auth.signOut(); await this.router.navigateByUrl('/#owner', { replaceUrl: true }); }
    catch { this.failed.set(true); }
  }
}
