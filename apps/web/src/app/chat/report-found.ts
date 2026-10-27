import { translateSection } from '../core/translations';
import { httpResource } from '@angular/common/http';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ChatService as ChatApi, FinderStarted, CurrentUserResponse } from '@lost-key-finder/api-client';
import { Auth } from '../auth/auth';

@Component({
  selector: 'app-report-found', imports: [FormsModule],
  template: `<h2>{{ copy().report }}</h2><p>{{ copy().intro }}</p>
    <form (submit)="send($event)">
      @if (!auth.signedIn()) {
        <label for="finder-email">{{ copy().email }}</label><input id="finder-email" name="email" type="email" autocomplete="email" required maxlength="254" [(ngModel)]="email" [disabled]="busy()">
      }
      <label for="finder-message">{{ copy().message }}</label>
      <textarea id="finder-message" name="message" rows="5" maxlength="2000" required [(ngModel)]="text" [disabled]="busy()"></textarea>
      <label for="finder-image">{{ copy().image }}</label>
      <input id="finder-image" type="file" accept="image/jpeg,image/png" aria-describedby="finder-image-hint" (change)="selectImage($event)" [disabled]="busy()">
      <p id="finder-image-hint">{{ copy().imageHint }}</p>
      @if (error()) { <p role="alert">{{ copy().error }}</p> }
      <button type="submit" [disabled]="busy() || (auth.signedIn() && !me.hasValue()) || !auth.configured">{{ busy() ? copy().sending : auth.signedIn() ? copy().report : copy().confirm }}</button>
    </form>`,
})
export class ReportFound {
  readonly publicId = input.required<string>();
  protected readonly auth = inject(Auth);
  protected readonly copy = translateSection('chat');
  private readonly chats = inject(ChatApi);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  protected readonly me = httpResource<CurrentUserResponse>(() => this.auth.signedIn() ? '/api/v1/me' : undefined);
  protected readonly busy = signal(false);
  protected readonly error = signal(false);
  protected email = '';
  protected text = '';
  private image: File | null = null;
  private started: (FinderStarted & { signature: string }) | null = null;
  private attempt: { signature: string; key: string } | null = null;

  protected selectImage(event: Event): void {
    const element = event.target as HTMLInputElement;
    this.image = element.files?.[0] ?? null;
    this.error.set(!!this.image && this.image.size > 5_000_000);
    if (this.error()) { this.image = null; element.value = ''; }
  }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    if (this.busy() || !this.text.trim() || !this.auth.configured) return;
    this.busy.set(true); this.error.set(false);
    const address = this.auth.signedIn() ? this.me.value()?.email : this.email.trim();
    if (!address) { this.busy.set(false); this.error.set(true); return; }
    const signature = JSON.stringify([this.publicId(), address, this.text, this.image?.name, this.image?.size, this.image?.lastModified]);
    try {
      if (!this.attempt || this.attempt.signature !== signature || (this.started && Date.parse(this.started.expiresAt) <= Date.now())) {
        this.attempt = { signature, key: crypto.randomUUID() }; this.started = null;
      }
      if (!this.started || this.started.signature !== signature) {
        const draft = await firstValueFrom(this.chats.chatControllerStart({ publicId: this.publicId(),
          'Idempotency-Key': this.attempt.key, body: { email: address, text: this.text, image: this.image } }));
        this.started = { ...draft, signature };
      }
      if (this.auth.signedIn()) {
        const chat = await firstValueFrom(this.chats.chatControllerComplete({ flowId: this.started.flowId }));
        if (!this.destroy.destroyed) await this.router.navigateByUrl(`/chats/${chat.id}`);
      } else {
        await this.auth.sendLink(address, undefined, { finderReportId: this.started.flowId });
        if (!this.destroy.destroyed) await this.router.navigateByUrl('/login/link-sent', { replaceUrl: true });
      }
    } catch { if (!this.destroy.destroyed) this.error.set(true); }
    finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
}
