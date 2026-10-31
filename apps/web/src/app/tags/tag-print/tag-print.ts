import { DOCUMENT } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TagPage, TagResponse } from '@lost-key-finder/api-client';
import { Auth } from '../../auth/auth';
import { translateSection } from '../../core/translations';
import { isUuidV4 } from '../../core/uuid';

@Component({
  selector: 'app-tag-print', imports: [RouterLink],
  templateUrl: './tag-print.html', styleUrl: './tag-print.scss',
})
export class TagPrint {
  protected readonly copy = translateSection('print');
  protected readonly tagsCopy = translateSection('tags');
  private readonly auth = inject(Auth);
  private readonly document = inject(DOCUMENT);
  private readonly destroy = inject(DestroyRef);
  private readonly id = inject(ActivatedRoute).snapshot.queryParamMap.get('tagId');
  protected readonly pageNumber = signal(1);
  protected readonly page = httpResource<TagPage>(() => this.auth.signedIn() && !this.id ? `/api/v1/tags?page=${this.pageNumber()}` : undefined);
  protected readonly single = httpResource<TagResponse>(() => this.auth.signedIn() && this.id && isUuidV4(this.id) ? `/api/v1/tags/${this.id}` : undefined);
  protected readonly available = computed(() => (this.single.hasValue() ? [this.single.value()] : this.page.hasValue() ? this.page.value().items : [])
    .filter(tag => tag.origin === 'SELF_CREATED' && !tag.deletedAt && ['ACTIVE', 'INACTIVE'].includes(tag.status)));
  protected readonly selected = signal<TagResponse[]>([]);
  protected readonly sheet = signal(true);
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  protected readonly loadFailed = computed(() => !!this.page.error() || !!this.single.error() || !!(this.id && !isUuidV4(this.id)));
  protected readonly loading = computed(() => this.page.isLoading() || this.single.isLoading());

  protected checked(tag: TagResponse): boolean { return this.selected().some(item => item.id === tag.id); }
  protected toggle(tag: TagResponse): void {
    this.selected.update(items => this.checked(tag) ? items.filter(item => item.id !== tag.id) : items.length < 25 ? [...items, tag] : items);
  }
  protected async download(): Promise<void> {
    if (this.busy() || !this.selected().length) return;
    const identity = this.auth.firebaseUserId();
    this.busy.set(true); this.failed.set(false);
    try {
      const { tagPdf } = await import('./tag-pdf');
      const bytes = await tagPdf(this.selected().map(tag => tag.publicId), this.document.location.origin, this.sheet(), this.copy().instruction);
      if (this.destroy.destroyed || !this.auth.signedIn() || this.auth.firebaseUserId() !== identity) return;
      const url = URL.createObjectURL(new Blob([new Uint8Array(bytes)], { type: 'application/pdf' }));
      const link = this.document.createElement('a');
      link.href = url; link.download = 'lost-key-finder-tags.pdf'; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { if (!this.destroy.destroyed) this.failed.set(true); }
    finally { if (!this.destroy.destroyed) this.busy.set(false); }
  }
}
