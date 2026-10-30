import { isUuidV4 } from '../../core/uuid';
import { TranslocoPipe } from '@jsverse/transloco';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormField, disabled, email, form, required, submit } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FinderEntry } from '../../finder/finder-entry/finder-entry';
import { Auth } from '../../auth/auth';

@Component({
  selector: 'app-home-entry',
  imports: [TranslocoPipe, FormField, RouterLink, FinderEntry],
  templateUrl: './home-entry.html',
  styleUrl: './home-entry.scss',
})
export class HomeEntry {
  protected readonly auth = inject(Auth);
  protected readonly model = signal({ email: '' });
  protected readonly status = signal<'idle' | 'failed'>('idle');
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
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
        const chatId = this.route.snapshot.queryParamMap.get('chatId');
        if (chatId && isUuidV4(chatId)) {
          await this.auth.sendLink(this.model().email, undefined, { chatId: chatId.toLowerCase() });
        } else await this.auth.sendLink(this.model().email);
        if (this.destroyRef.destroyed) return;
        await this.router.navigateByUrl('/login/link-sent', { replaceUrl: true });
      } catch {
        this.status.set('failed');
      } finally {
        this.sending.set(false);
      }
    });
  }
}
