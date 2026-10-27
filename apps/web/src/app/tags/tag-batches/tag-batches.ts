import { translateSection } from '../../core/translations';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe, DOCUMENT } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormField, disabled, form, max, maxLength, min } from '@angular/forms/signals';
import { TagBatchPage, TagBatchResponse, TagBatchesService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { tagError } from '../tag-error';
import { Auth } from '../../auth/auth';

@Component({
  selector: 'app-tag-batches',
  imports: [RouterLink, DatePipe, FormField],
  templateUrl: './tag-batches.html',
  styleUrl: './tag-batches.scss',
})
export class TagBatches {
  protected readonly copy = translateSection('tags');
  private readonly auth = inject(Auth);
  private readonly batches = inject(TagBatchesService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly cursor = signal('');
  protected readonly page = httpResource<TagBatchPage>(() => this.auth.signedIn()
    ? `/api/v1/admin/tag-batches${this.cursor() ? `?cursor=${encodeURIComponent(this.cursor())}` : ''}` : undefined);
  protected readonly busy = signal(false);
  protected readonly failure = signal<unknown>(null);
  protected readonly submitted = signal(false);
  private readonly model = signal({ count: 100, label: '' });
  protected readonly pending = signal<{ key: string; count: number; label: string } | null>(null);
  protected readonly batchForm = form(this.model, p => {
    min(p.count, 1); max(p.count, 100); maxLength(p.label, 80);
    disabled(p.count, () => this.busy() || !!this.pending());
    disabled(p.label, () => this.busy() || !!this.pending());
  });
  protected readonly invalid = computed(() => this.batchForm().invalid() || !Number.isInteger(this.model().count));
  protected readonly selected = signal<TagBatchResponse | null>(null);
  private readonly releaseModel = signal({ confirmed: false });
  protected readonly releaseForm = form(this.releaseModel, p => disabled(p.confirmed, () => this.busy()));
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }

  protected async create(event: Event): Promise<void> {
    event.preventDefault(); this.submitted.set(true);
    if (this.busy() || this.invalid()) return;
    const request = this.pending() ?? { key: crypto.randomUUID(), ...this.model() };
    this.pending.set(request); this.busy.set(true); this.failure.set(null);
    try {
      await firstValueFrom(this.batches.tagBatchControllerCreate({ 'Idempotency-Key': request.key, body: { count: request.count, label: request.label } }));
      if (this.destroyRef.destroyed) return;
      this.pending.set(null); this.model.set({ count: 100, label: '' });
      this.first();
    } catch (error) { if (!this.destroyRef.destroyed) this.failure.set(error); }
    finally { if (!this.destroyRef.destroyed) this.busy.set(false); }
  }

  protected first(): void { if (this.cursor()) this.cursor.set(''); else this.page.reload(); }
  protected next(cursor: string): void { this.cursor.set(cursor); }
  protected select(batch: TagBatchResponse | null): void { this.selected.set(batch); this.releaseModel.set({ confirmed: false }); }

  protected async release(): Promise<void> {
    const batch = this.selected();
    if (this.busy() || !batch || !this.releaseModel().confirmed) return;
    this.busy.set(true); this.failure.set(null);
    try {
      await firstValueFrom(this.batches.tagBatchControllerRelease({ batchId: batch.id }));
      if (!this.destroyRef.destroyed) { this.select(null); this.page.reload(); }
    } catch (error) { if (!this.destroyRef.destroyed) this.failure.set(error); }
    finally { if (!this.destroyRef.destroyed) this.busy.set(false); }
  }

  protected async download(batch: TagBatchResponse): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true); this.failure.set(null);
    try {
      const csv = await firstValueFrom(this.batches.tagBatchControllerExport({ batchId: batch.id }));
      if (this.destroyRef.destroyed) return;
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
      const link = this.document.createElement('a'); link.href = url; link.download = `tags-${batch.id}.csv`;
      link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { if (!this.destroyRef.destroyed) this.failure.set(error); }
    finally { if (!this.destroyRef.destroyed) this.busy.set(false); }
  }
}
