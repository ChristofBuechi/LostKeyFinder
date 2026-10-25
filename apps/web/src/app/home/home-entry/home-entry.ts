import { Component, inject, signal } from '@angular/core';
import { FormField, disabled, email, form, required, submit } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { FinderEntry } from '../../finder/finder-entry/finder-entry';
import { Auth } from '../../auth/auth';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-home-entry',
  imports: [FormField, RouterLink, FinderEntry],
  templateUrl: './home-entry.html',
  styleUrl: './home-entry.scss',
})
export class HomeEntry {
  protected readonly auth = inject(Auth);
  protected readonly language = inject(LanguageService);
  protected readonly model = signal({ email: '' });
  protected readonly status = signal<'idle' | 'failed'>('idle');
  private readonly router = inject(Router);
  private readonly sending = signal(false);
  protected readonly loginForm = form(this.model, path => {
    required(path.email);
    email(path.email);
    disabled(path.email, () => this.sending() || !this.auth.configured);
  });

  protected send(event: Event): void {
    event.preventDefault();
    if (this.loginForm().submitting() || !this.auth.configured) return;
    this.status.set('idle');
    void submit(this.loginForm, async () => {
      this.sending.set(true);
      try {
        await this.auth.sendLink(this.model().email);
        await this.router.navigateByUrl('/login/link-sent', { replaceUrl: true });
      } catch {
        this.status.set('failed');
      } finally {
        this.sending.set(false);
      }
    });
  }
}
