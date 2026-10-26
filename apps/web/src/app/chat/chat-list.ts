import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { ChatCopy } from './chat-copy';
import { ChatPage } from '@lost-key-finder/api-client';
@Component({ selector: 'app-chat-list', imports: [RouterLink, DatePipe], template: `
<section class="auth-panel"><h1>{{ copy().title }}</h1>
  @if (chats.isLoading()) { <p role="status">{{ copy().loading }}</p> }
  @if (chats.error()) { <p role="alert">{{ copy().error }}</p><button (click)="chats.reload()" type="button">{{ copy().retry }}</button> }
  @if (chats.hasValue()) {
    <ul>@for (chat of chats.value().items; track chat.id) {
      <li><a [routerLink]="['/chats', chat.id]">{{ chat.lastMessageAt | date:'yyyy-MM-dd HH:mm' }} · {{ chat.id }}</a>
      @if (chat.unread) { <strong> {{ chat.unread }} {{ copy().unread }}</strong> }
      @if (chat.closedAt) { <span> · {{ copy().closed }}</span> }</li>
    } @empty { <li>{{ copy().empty }}</li> }</ul>
    <nav [attr.aria-label]="copy().title">
      <button type="button" [disabled]="chats.value().page === 1" (click)="page.set(page() - 1)">{{ copy().previous }}</button>
      <span>{{ copy().page }} {{ chats.value().page }} {{ copy().of }} {{ chats.value().totalPages }}</span>
      <button type="button" [disabled]="chats.value().page === chats.value().totalPages" (click)="page.set(page() + 1)">{{ copy().next }}</button>
    </nav>
  }
  <p><a routerLink="/account">Account</a></p>
</section>`, styles: `li { margin-block: 1rem; overflow-wrap: anywhere; } nav { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }` })
export class ChatList {
  protected readonly copy = inject(ChatCopy).text;
  protected readonly page = signal(1);
  private readonly route = inject(ActivatedRoute);
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  protected readonly chats = httpResource<ChatPage>(() => ({ url: '/api/v1/chats', params: {
    page: this.page(), ...(this.query().get('tagId') ? { tagId: this.query().get('tagId')! } : {}),
  } }));
}
