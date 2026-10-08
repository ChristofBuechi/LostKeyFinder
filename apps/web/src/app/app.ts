import { Component, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { LanguageService } from './core/language.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly language = inject(LanguageService);
  private readonly document = inject(DOCUMENT);

  constructor() {
    inject(Router).events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.focusContent());
  }

  protected focusContent(event?: Event): void {
    event?.preventDefault();
    this.document.getElementById('main-content')?.focus();
  }

  protected toggleLanguage(): void {
    this.language.toggle();
  }
}
