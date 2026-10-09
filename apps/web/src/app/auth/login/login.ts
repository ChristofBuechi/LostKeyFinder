import { Component, inject, signal } from '@angular/core';
import { FormField, disabled, email, form, required, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-login',
  imports: [FormField, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  protected readonly auth = inject(Auth);
  protected readonly language = inject(LanguageService);
  protected readonly model = signal({ email: '' });
  protected readonly status = signal<'idle' | 'sent' | 'failed'>('idle');
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
        this.status.set('sent');
      } catch {
        this.status.set('failed');
      } finally {
        this.sending.set(false);
      }
    });
  }
}
