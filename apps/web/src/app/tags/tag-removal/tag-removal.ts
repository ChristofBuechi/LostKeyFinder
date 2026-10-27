import { translateSection } from '../../core/translations';
import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { AdminTagsService, TagResponse, TagsService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { tagError } from '../tag-error';

@Component({
  selector: 'app-tag-removal',
  template: `
    @if (!tag().deletedAt && (admin() || tag().status === 'ACTIVE' || tag().status === 'INACTIVE')) {
      <button class="tag-action tag-action--danger" type="button" (click)="remove()" [disabled]="busy()"
        [attr.aria-label]="action() + ' ' + tag().publicId">{{ action() }}</button>
    }
    @if (failure(); as error) { <p role="alert">{{ errorText(error) }}</p> }
  `,
  styles: `:host { display: block; }
    button { width: 100%; }
    [role="alert"] { color: var(--signal); }`,
})
export class TagRemoval {
  readonly tag = input.required<TagResponse>();
  readonly admin = input(false);
  readonly removed = output<void>();
  private readonly tags = inject(TagsService);
  private readonly adminTags = inject(AdminTagsService);
  private readonly copy = translateSection('tags');
  private readonly destroyRef = inject(DestroyRef);
  protected readonly busy = signal(false);
  protected readonly failure = signal<unknown>(null);
  private readonly deleting = computed(() => this.admin() || this.tag().origin === 'SELF_CREATED');
  protected readonly action = computed(() => this.deleting() ? this.copy().deleteTag : this.copy().releaseTag);
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }

  protected async remove(): Promise<void> {
    const tag = this.tag();
    // ponytail: the browser supplies confirmation, focus handling and keyboard controls.
    if (this.busy() || !window.confirm(`${tag.publicId}\n\n${this.admin() ? this.copy().adminDeleteConfirm : this.deleting() ? this.copy().deleteConfirm : this.copy().releaseConfirm}`)) return;
    this.busy.set(true); this.failure.set(null);
    try {
      await firstValueFrom(this.admin() ? this.adminTags.adminTagControllerDelete({ tagId: tag.id }) : this.deleting()
        ? this.tags.tagControllerDelete({ tagId: tag.id })
        : this.tags.tagControllerRelease({ tagId: tag.id }));
      if (!this.destroyRef.destroyed && this.tag().id === tag.id) this.removed.emit();
    } catch (error) {
      if (!this.destroyRef.destroyed && this.tag().id === tag.id) this.failure.set(error);
    } finally {
      if (!this.destroyRef.destroyed) this.busy.set(false);
    }
  }
}
