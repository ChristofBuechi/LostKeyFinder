import { Component, effect, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { authGuard } from './auth/auth-guard';
import { Auth } from './auth/auth';
import { LanguageService } from './core/language.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly language = inject(LanguageService);
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  private readonly displayedIdentity = signal<string | null>(null);
  private readonly protectedPage = signal(false);

  protected contentAllowed(): boolean {
    const signedIn = this.auth.signedIn();
    const identity = this.auth.firebaseUserId();
    const displayedIdentity = this.displayedIdentity();
    return !this.protectedPage() || (signedIn && identity === displayedIdentity);
  }

  constructor() {
    effect(() => {
      if (!this.contentAllowed()) void this.router.navigateByUrl('/#owner', { replaceUrl: true });
    });
    inject(Router).events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => {
        this.displayedIdentity.set(this.auth.firebaseUserId());
        this.protectedPage.set(!!this.router.routerState.snapshot.root.firstChild?.routeConfig?.canActivate?.includes(authGuard));
        this.focusContent();
      });
  }

  protected focusContent(event?: Event): void {
    event?.preventDefault();
    this.document.getElementById('main-content')?.focus();
  }

  protected toggleLanguage(): void {
    this.language.toggle();
  }
}
