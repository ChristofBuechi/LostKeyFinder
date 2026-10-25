import { DOCUMENT, DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, DestroyRef, InjectionToken, computed, inject, signal } from '@angular/core';
import { FormField, disabled, form, maxLength } from '@angular/forms/signals';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TagPage, TagResponse, TagSummary, TagsService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../auth/auth';
import { TagCopy } from '../tag-copy';
import { tagError } from '../tag-error';

export const QR_ENCODER = new InjectionToken('QR encoder loader', {
  providedIn: 'root', factory: () => () => import('qrcode'),
});

@Component({
  selector: 'app-tag-list',
  imports: [RouterLink, FormField, DatePipe],
  templateUrl: './tag-list.html',
  styleUrl: './tag-list.scss',
})
export class TagList {
  protected readonly auth = inject(Auth);
  protected readonly copy = inject(TagCopy).text;
  private readonly route = inject(ActivatedRoute);
  protected readonly admin = this.route.snapshot.data['admin'] === true;
  private readonly tags = inject(TagsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly encoder = inject(QR_ENCODER);
  private readonly initialBatchId = this.admin ? this.route.snapshot.queryParamMap?.get('batchId') ?? '' : '';
  private readonly query = signal(this.initialBatchId ? `?batchId=${encodeURIComponent(this.initialBatchId)}` : '');
  private appliedFilters = this.query().slice(1);
  protected readonly page = httpResource<TagPage>(() => this.auth.signedIn()
    ? `/api/v1/${this.admin ? 'admin/' : ''}tags${this.query()}` : undefined);
  private readonly summaryQuery = computed(() => {
    const params = new URLSearchParams(this.query()); params.delete('page');
    return params.size ? `?${params}` : '';
  });
  protected readonly summary = httpResource<TagSummary>(() => this.admin && this.auth.signedIn()
    ? `/api/v1/admin/tags/summary${this.summaryQuery()}` : undefined);
  protected readonly pageNumbers = computed(() => {
    if (!this.page.hasValue()) return [];
    const { page, totalPages } = this.page.value();
    // ponytail: show the ends and nearby pages, rather than rendering thousands of buttons.
    return [...new Set([1, page - 2, page - 1, page, page + 1, page + 2, totalPages])]
      .filter(number => number >= 1 && number <= totalPages).sort((a, b) => a - b);
  });
  protected readonly busy = signal(false);
  protected readonly actionError = signal<unknown>(null);
  protected readonly validationError = signal(false);
  protected readonly pending = signal<{ key: string; label: string } | null>(null);
  private readonly model = signal({ label: '', publicId: '', origin: '', status: '', assigned: '', batchId: this.initialBatchId, inventoryStatus: '' });
  protected readonly tagForm = form(this.model, p => {
    maxLength(p.label, 80);
    maxLength(p.publicId, 36); maxLength(p.batchId, 36);
    disabled(p.label, () => this.busy() || !!this.pending());
  });

  protected errorText(error: unknown): string {
    return tagError(error, this.copy());
  }

  protected filter(pageNumber = 1): void {
    const parameters = new URLSearchParams();
    const model = this.model();
    const id = model.publicId.trim();
    const batchId = model.batchId.trim();
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    this.validationError.set((!!id && !uuid.test(id)) || (!!batchId && !uuid.test(batchId)));
    if (this.validationError()) return;
    if (this.admin) {
      for (const [name, value] of Object.entries({ publicId: id, origin: model.origin, status: model.status, assigned: model.assigned, batchId, inventoryStatus: model.inventoryStatus })) {
        if (value) parameters.set(name, value);
      }
    }
    const filters = parameters.toString();
    if (pageNumber > 1 && filters === this.appliedFilters) parameters.set('page', String(pageNumber));
    this.appliedFilters = filters;
    const value = parameters.size ? `?${parameters}` : '';
    if (value === this.query()) { this.page.reload(); if (pageNumber === 1) this.summary.reload(); }
    else this.query.set(value);
  }

  protected async create(event: Event): Promise<void> {
    event.preventDefault();
    if (this.busy() || this.tagForm.label().invalid()) return;
    const request = this.pending() ?? { key: crypto.randomUUID(), label: this.model().label.trim() };
    this.pending.set(request);
    this.busy.set(true);
    this.actionError.set(null);
    try {
      await firstValueFrom(this.tags.tagControllerCreate({ 'Idempotency-Key': request.key, body: { label: request.label } }));
      if (this.destroyRef.destroyed) return;
      this.pending.set(null);
      this.model.update(model => ({ ...model, label: '' }));
      this.filter();
    } catch (error) {
      if (!this.destroyRef.destroyed) this.actionError.set(error);
    } finally {
      if (!this.destroyRef.destroyed) this.busy.set(false);
    }
  }

  protected async toggle(tag: TagResponse): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.actionError.set(null);
    try {
      await firstValueFrom(this.tags.tagControllerChangeStatus({ tagId: tag.id, body: { status: tag.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' } }));
      if (!this.destroyRef.destroyed) this.page.reload();
    } catch (error) {
      if (!this.destroyRef.destroyed) this.actionError.set(error);
    } finally {
      if (!this.destroyRef.destroyed) this.busy.set(false);
    }
  }

  protected publicUrl(tag: TagResponse): string {
    return `${location.origin}/f/${tag.publicId}`;
  }

  protected async download(tag: TagResponse): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.actionError.set(null);
    try {
      const qr = await this.encoder();
      const url = await qr.toDataURL(this.publicUrl(tag), { width: 768, margin: 4, errorCorrectionLevel: 'M' });
      if (this.destroyRef.destroyed) return;
      const anchor = this.document.createElement('a');
      anchor.href = url;
      anchor.download = `lost-key-finder-${tag.publicId}.png`;
      anchor.click();
    } catch (error) {
      if (!this.destroyRef.destroyed) this.actionError.set(error);
    } finally {
      if (!this.destroyRef.destroyed) this.busy.set(false);
    }
  }
}
