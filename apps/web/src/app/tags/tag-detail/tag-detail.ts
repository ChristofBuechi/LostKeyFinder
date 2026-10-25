import { Component, computed, inject, linkedSignal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TagHistoryPage, TagResponse } from '@lost-key-finder/api-client';
import { TagCopy } from '../tag-copy';
import { tagError } from '../tag-error';
import { TagRemoval } from '../tag-removal/tag-removal';
import { Auth } from '../../auth/auth';

@Component({
  selector: 'app-tag-detail',
  imports: [RouterLink, DatePipe, TagRemoval],
  templateUrl: './tag-detail.html',
  styleUrl: './tag-detail.scss',
})
export class TagDetail {
  protected readonly copy = inject(TagCopy).text;
  private readonly auth = inject(Auth);
  protected readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly admin = this.route.snapshot.data['admin'] === true;
  private readonly params = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  private readonly tagId = computed(() => this.params().get('tagId') ?? '');
  protected readonly tag = httpResource<TagResponse>(() => this.auth.signedIn()
    ? `/api/v1/${this.admin ? 'admin/' : ''}tags/${encodeURIComponent(this.tagId())}` : undefined);
  protected readonly historyPage = linkedSignal({ source: this.tagId, computation: () => 1 });
  protected readonly history = httpResource<TagHistoryPage>(() => this.auth.signedIn()
    ? `/api/v1/${this.admin ? 'admin/' : ''}tags/${encodeURIComponent(this.tagId())}/history?page=${this.historyPage()}` : undefined);
  protected historyText(action: string): string { return (this.copy() as Record<string, string>)[action] ?? action; }
  protected changeField(field: string): string {
    const copy = this.copy();
    const names: Record<string, string> = { publicId: copy.tag, origin: copy.origin, primaryOwnerUserId: copy.owner,
      status: copy.status, label: copy.label, batchId: copy.batch, inventoryStatus: copy.inventory,
      createdAt: copy.created, updatedAt: copy.updated, registeredAt: copy.registeredAt, deletedAt: copy.deletedAt,
      count: copy.count, batchLabel: copy.batchLabel, batchReleasedAt: copy.releaseDate };
    return names[field] ?? field;
  }
  protected changeValue(field: string, value: string | null): string {
    if (value === null) return this.copy().notSet;
    if (value === '') return this.copy().emptyValue;
    return ['origin', 'status', 'inventoryStatus'].includes(field) ? this.historyText(value) : value;
  }
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }
}
