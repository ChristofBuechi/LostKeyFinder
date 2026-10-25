import { DOCUMENT, DatePipe } from '@angular/common';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Component, DestroyRef, InjectionToken, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TagPage, TagResponse, TagsService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../auth/auth';
import { TagCopy } from '../tag-copy';

export const QR_ENCODER = new InjectionToken('QR encoder loader', {
  providedIn: 'root', factory: () => () => import('qrcode'),
});

@Component({
  selector: 'app-tag-list',
  imports: [RouterLink, FormsModule, DatePipe],
  templateUrl: './tag-list.html',
  styleUrl: './tag-list.scss',
})
export class TagList {
  protected readonly auth = inject(Auth);
  protected readonly copy = inject(TagCopy).text;
  protected readonly admin = inject(ActivatedRoute).snapshot.data['admin'] === true;
  private readonly tags = inject(TagsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly encoder = inject(QR_ENCODER);
  private readonly query = signal('');
  private appliedFilters = '';
  protected readonly page = httpResource<TagPage>(() => this.auth.signedIn()
    ? `/api/v1/${this.admin ? 'admin/' : ''}tags${this.query()}` : undefined);
  protected readonly busy = signal(false);
  protected readonly actionError = signal<unknown>(null);
  protected readonly validationError = signal(false);
  protected readonly pending = signal<{ key: string; label: string } | null>(null);
  protected label = '';
  protected publicId = '';
  protected origin = '';
  protected status = '';
  protected assigned = '';

  protected errorText(error: unknown): string {
    return error instanceof HttpErrorResponse && error.status === 403 ? this.copy().denied
      : error instanceof HttpErrorResponse && error.status === 401 ? this.copy().expired : this.copy().failed;
  }

  protected filter(cursor?: string): void {
    const parameters = new URLSearchParams();
    const id = this.publicId.trim();
    this.validationError.set(!!id && !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id));
    if (this.validationError()) return;
    if (this.admin) {
      for (const [name, value] of Object.entries({ publicId: id, origin: this.origin, status: this.status, assigned: this.assigned })) {
        if (value) parameters.set(name, value);
      }
    }
    const filters = parameters.toString();
    if (cursor && filters === this.appliedFilters) parameters.set('cursor', cursor);
    this.appliedFilters = filters;
    const value = parameters.size ? `?${parameters}` : '';
    if (value === this.query()) this.page.reload();
    else this.query.set(value);
  }

  protected async create(): Promise<void> {
    if (this.busy()) return;
    const request = this.pending() ?? { key: crypto.randomUUID(), label: this.label.trim() };
    this.pending.set(request);
    this.busy.set(true);
    this.actionError.set(null);
    try {
      await firstValueFrom(this.tags.tagControllerCreate({ 'Idempotency-Key': request.key, body: { label: request.label } }));
      if (this.destroyRef.destroyed) return;
      this.pending.set(null);
      this.label = '';
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
