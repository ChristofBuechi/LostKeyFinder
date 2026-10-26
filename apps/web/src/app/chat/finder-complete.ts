import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ChatCopy } from './chat-copy';
@Component({ selector: 'app-finder-complete', template: `<section class="auth-panel"><h1>{{ copy().report }}</h1>
  @if (error()) { <p role="alert">{{ copy().error }}</p><button type="button" (click)="complete()">{{ copy().retry }}</button> }
  @else { <p role="status">{{ copy().completed }}</p> }</section>` })
export class FinderComplete implements OnInit {
  protected readonly copy = inject(ChatCopy).text;
  protected readonly error = signal(false);
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  ngOnInit(): void { void this.complete(); }
  protected async complete(): Promise<void> {
    this.error.set(false);
    try {
      const result = await firstValueFrom(this.http.post<{ id: string }>(`/api/v1/finder-reports/${encodeURIComponent(this.route.snapshot.paramMap.get('flowId') ?? '')}/complete`, {}));
      if (!this.destroy.destroyed) await this.router.navigateByUrl(`/chats/${result.id}`, { replaceUrl: true });
    } catch { if (!this.destroy.destroyed) this.error.set(true); }
  }
}
