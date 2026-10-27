import { DOCUMENT, DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, linkedSignal, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { ChatService as ChatApi, ChatView, ChatMessage, MessagePage, CurrentUserResponse } from '@lost-key-finder/api-client';
import { Auth } from '../auth/auth';
import { ChatCopy } from './chat-copy';
import { ChatImage } from './chat-image';

@Component({ selector: 'app-chat-detail', imports: [DatePipe, FormsModule, RouterLink, ChatImage],
  templateUrl: './chat-detail.html', styleUrl: './chat-detail.scss' })
export class ChatDetail {
  protected readonly auth = inject(Auth);
  protected readonly copy = inject(ChatCopy).text;
  private readonly chats = inject(ChatApi);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly route = inject(ActivatedRoute);
  private readonly params = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  protected readonly messageId = computed(() => this.query()?.get('messageId') ?? null);
  protected readonly id = computed(() => this.params().get('chatId') ?? '');
  protected readonly chat = httpResource<ChatView>(() => this.auth.signedIn() ? `/api/v1/chats/${encodeURIComponent(this.id())}` : undefined);
  protected readonly me = httpResource<CurrentUserResponse>(() => this.auth.signedIn() ? '/api/v1/me' : undefined);
  protected readonly latest = httpResource<MessagePage>(() => this.auth.signedIn() ? `/api/v1/chats/${encodeURIComponent(this.id())}/messages${this.messageId() ? '?messageId=' + encodeURIComponent(this.messageId()!) : ''}` : undefined);
  protected readonly messages = linkedSignal({ source: () => `${this.id()}:${this.messageId()}:${this.auth.signedIn()}`, computation: (): ChatMessage[] => [] });
  protected readonly before = computed(() => {
    const items = this.messages();
    if (!items.length) return null;
    if (items[0].sequence! > 1) return items[0].sequence!;
    return items.find((item, i) => i > 0 && item.sequence! > items[i - 1].sequence! + 1)?.sequence ?? null;
  });
  protected readonly text = linkedSignal({ source: () => this.id(), computation: () => '' });
  protected readonly busy = signal(false);
  protected readonly loadingOlder = signal(false);
  protected readonly error = signal(false);
  protected readonly confirmingClose = linkedSignal({ source: () => this.id(), computation: () => false });
  private image: File | null = null;
  private pending: { signature: string; key: string } | null = null;
  private readThrough = 0;
  private loadedId = '';

  constructor() {
    effect(() => {
      if (!this.auth.signedIn() || !this.latest.hasValue()) return;
      const value = this.latest.value();
      if (this.loadedId !== this.id()) { this.loadedId = this.id(); this.readThrough = 0; }
      this.merge(value.items ?? []);
      this.markRead();
    });
    const timer = setInterval(() => {
      if (this.document.visibilityState !== 'visible' || !this.auth.signedIn()) return;
      if (!this.latest.isLoading()) this.latest.reload();
      if (!this.chat.isLoading()) this.chat.reload();
    }, 10000);
    this.destroy.onDestroy(() => clearInterval(timer));
  }

  private merge(items: ChatMessage[]): void {
    this.messages.update(previous => [...new Map([...previous, ...items].map(item => [item.id, item])).values()]
      .sort((a, b) => a.sequence! - b.sequence!));
  }
  private markRead(): void {
    if (this.document.visibilityState !== 'visible') return;
    const through = this.messages().at(-1)?.sequence ?? 0;
    if (through <= this.readThrough || !this.me.hasValue()) return;
    // Admin reads do not change a participant's unread state.
    if (!this.chat.hasValue() || !this.chat.value().participant) return;
    const id = this.id();
    void firstValueFrom(this.chats.chatControllerRead({ chatId: id, body: { through } })).then(() => {
      if (this.id() === id && !this.destroy.destroyed) this.readThrough = through;
    }).catch(() => { /* next poll retries the read acknowledgement */ });
  }
  protected newest(): void { void this.router.navigate([], { relativeTo: this.route, queryParams: {} }); }
  protected async older(): Promise<void> {
    if (!this.before() || this.loadingOlder()) return;
    this.loadingOlder.set(true); this.error.set(false);
    const id = this.id();
    try {
      const page = await firstValueFrom(this.chats.chatControllerMessages({ chatId: id, before: this.before()! }));
      if (!this.destroy.destroyed && id === this.id()) { this.merge(page.items ?? []); }
    } catch { this.error.set(true); }
    finally { if (!this.destroy.destroyed) this.loadingOlder.set(false); }
  }
  protected chooseImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.image = input.files?.[0] ?? null;
    this.pending = null;
    if (this.image && this.image.size > 5_000_000) { this.image = null; input.value = ''; this.error.set(true); }
  }
  protected async send(event: Event, file: HTMLInputElement): Promise<void> {
    event.preventDefault();
    if (this.busy() || !this.chat.value()?.writable || (!this.text().trim() && !this.image)) return;
    this.busy.set(true); this.error.set(false);
    const id = this.id();
    const signature = JSON.stringify([id, this.text(), this.image?.name, this.image?.size, this.image?.lastModified]);
    if (!this.pending || this.pending.signature !== signature) this.pending = { signature, key: crypto.randomUUID() };
    try {
      const message = await firstValueFrom(this.chats.chatControllerSend({ chatId: id, 'Idempotency-Key': this.pending.key,
        body: { text: this.text(), image: this.image } }));
      if (!this.destroy.destroyed && id === this.id()) {
        this.merge([message]); this.text.set(''); this.image = null; file.value = ''; this.pending = null;
        this.chat.reload(); this.latest.reload();
      }
    } catch { if (!this.destroy.destroyed && id === this.id()) this.error.set(true); }
    finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
  protected async close(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true); this.error.set(false);
    const id = this.id();
    try {
      await firstValueFrom(this.chats.chatControllerClose({ chatId: id }));
      if (!this.destroy.destroyed && id === this.id()) { this.chat.reload(); this.confirmingClose.set(false); }
    } catch { this.error.set(true); }
    finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
}
