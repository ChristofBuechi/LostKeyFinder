import { translateSection } from '../../core/translations';
import { Component, DestroyRef, ElementRef, Injector, afterNextRender, afterRenderEffect, computed, inject, linkedSignal, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { AdminTagsService, TagHistoryPage, TagResponse, TagsService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
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
  protected readonly copy = translateSection('tags');
  private readonly auth = inject(Auth);
  protected readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly admin = this.route.snapshot.data['admin'] === true;
  private readonly params = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  private readonly tagId = computed(() => this.params().get('tagId') ?? '');
  protected readonly tag = httpResource<TagResponse>(() => this.auth.signedIn()
    ? `/api/v1/${this.admin ? 'admin/' : ''}tags/${encodeURIComponent(this.tagId())}` : undefined);
  private readonly tags = inject(TagsService);
  private readonly adminTags = inject(AdminTagsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly labelInput = viewChild<ElementRef<HTMLInputElement>>('labelInput');
  private readonly labelButton = viewChild<ElementRef<HTMLButtonElement>>('labelButton');
  protected readonly editingLabel = linkedSignal({ source: () => ({ id: this.tagId(), signedIn: this.auth.signedIn() }), computation: () => false });
  protected readonly labelDraft = signal('');
  protected readonly savingLabel = signal(false);
  protected readonly labelError = signal<unknown>(null);

  constructor() {
    afterRenderEffect(() => {
      const input = this.labelInput();
      if (input) { input.nativeElement.focus(); input.nativeElement.select(); }
    });
  }

  protected editLabel(): void {
    if (!this.tag.hasValue() || this.tag.value().deletedAt || this.savingLabel()) return;
    this.labelDraft.set(this.tag.value().label ?? '');
    this.labelError.set(null);
    this.editingLabel.set(true);
  }

  protected cancelLabel(): void {
    if (this.savingLabel()) return;
    this.editingLabel.set(false);
    this.labelError.set(null);
    afterNextRender(() => this.labelButton()?.nativeElement.focus(), { injector: this.injector });
  }

  protected async saveLabel(event: Event): Promise<void> {
    event.preventDefault();
    if (this.savingLabel() || !this.editingLabel() || !this.tag.hasValue() || this.tag.value().deletedAt || this.labelDraft().length > 80) return;
    const tagId = this.tag.value().id;
    const body = { label: this.labelDraft().trim() };
    this.savingLabel.set(true);
    this.labelError.set(null);
    try {
      const updated = await firstValueFrom(this.admin
        ? this.adminTags.adminTagControllerChangeLabel({ tagId, body })
        : this.tags.tagControllerChangeLabel({ tagId, body }));
      if (!this.destroyRef.destroyed && this.auth.signedIn() && this.tagId() === tagId) {
        this.tag.set(updated);
        this.editingLabel.set(false);
        this.history.reload();
        afterNextRender(() => this.labelButton()?.nativeElement.focus(), { injector: this.injector });
      }
    } catch (error) {
      if (!this.destroyRef.destroyed && this.tagId() === tagId) this.labelError.set(error);
    } finally {
      if (!this.destroyRef.destroyed) this.savingLabel.set(false);
    }
  }

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
  protected async removed(): Promise<void> {
    if (this.admin) { this.tag.reload(); this.history.reload(); }
    else await this.router.navigateByUrl('/tags', { replaceUrl: true });
  }
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }
}
