import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ChatCopy } from './chat-copy';
@Component({ selector: 'app-chat-image', template: `
  @if (url(); as source) { <a [href]="source" target="_blank" rel="noopener"><img [src]="source" [alt]="copy().attachment" loading="lazy"></a> }
  @else { <button type="button" (click)="load()" [disabled]="busy()">{{ busy() ? copy().loading : copy().showImage }}</button> }
  @if (error()) { <p role="alert">{{ copy().error }}</p> }`,
  styles: `img { display: block; max-width: 100%; max-height: 28rem; object-fit: contain; border-radius: .5rem; }` })
export class ChatImage {
  readonly imageId = input.required<string>();
  protected readonly copy = inject(ChatCopy).text;
  protected readonly url = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal(false);
  private readonly http = inject(HttpClient);
  private readonly destroy = inject(DestroyRef);
  constructor() { this.destroy.onDestroy(() => { const url = this.url(); if (url) URL.revokeObjectURL(url); }); }
  protected async load(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true); this.error.set(false);
    try {
      const blob = await firstValueFrom(this.http.get(`/api/v1/chat-images/${encodeURIComponent(this.imageId())}`, { responseType: 'blob' }));
      if (!this.destroy.destroyed) this.url.set(URL.createObjectURL(blob));
    } catch { if (!this.destroy.destroyed) this.error.set(true); }
    finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
}
