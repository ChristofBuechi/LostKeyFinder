import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, effect, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrentUserResponse, UsersService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../auth/auth';
import { LanguageService } from '../core/language.service';
import { TagCopy } from '../tags/tag-copy';

@Component({
  selector: 'app-account',
  imports: [RouterLink],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account implements OnInit {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly users = inject(UsersService);
  protected readonly language = inject(LanguageService);
  protected readonly tagCopy = inject(TagCopy).text;
  protected readonly user = signal<CurrentUserResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<'accountBlocked' | 'accountFailed' | 'sessionExpired' | 'logoutFailed' | null>(null);
  protected readonly signingOut = signal(false);

  constructor() {
    effect(() => {
      if (!this.auth.signedIn()) {
        this.user.set(null);
        void this.router.navigateByUrl('/#owner', { replaceUrl: true });
      }
    });
  }

  ngOnInit(): void { void this.load(); }

  protected async load(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.user.set(null);
    try {
      const user = await firstValueFrom(this.users.userControllerMe());
      if (!this.destroyRef.destroyed && this.auth.signedIn()) {
        this.user.set(user);
        if (user.isAdmin && this.router.parseUrl(this.router.url).queryParams['entry'] === 'login') {
          await this.router.navigateByUrl('/admin/tags', { replaceUrl: true });
        }
      }
    } catch (error) {
      this.error.set(error instanceof HttpErrorResponse && error.status === 403 ? 'accountBlocked'
        : error instanceof HttpErrorResponse && error.status === 401 ? 'sessionExpired' : 'accountFailed');
    } finally {
      this.loading.set(false);
    }
  }

  protected async logout(): Promise<void> {
    if (this.signingOut()) return;
    this.signingOut.set(true);
    try {
      await this.auth.signOut();
      this.user.set(null);
      await this.router.navigateByUrl('/#owner', { replaceUrl: true });
    } catch {
      this.error.set('logoutFailed');
    } finally {
      this.signingOut.set(false);
    }
  }
}
