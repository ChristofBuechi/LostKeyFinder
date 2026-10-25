import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { TagResponse, TagsService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { TagCopy } from '../tag-copy';
import { tagError } from '../tag-error';

@Component({
  selector: 'app-tag-removal',
  template: `
    @if (tag().status === 'ACTIVE' || tag().status === 'INACTIVE') {
      <button type="button" (click)="remove()" [disabled]="busy()"
        [attr.aria-label]="action() + ' ' + tag().publicId">{{ action() }}</button>
    }
    @if (failure(); as error) { <p role="alert">{{ errorText(error) }}</p> }
  `,
  styles: `button { padding: .7rem 1rem; margin-block: .5rem; border: 1px solid var(--signal); border-radius: .25rem; background: var(--paper); color: var(--signal); font: inherit; cursor: pointer; }
    button:disabled { opacity: .6; cursor: wait; }
    [role="alert"] { color: var(--signal); }`,
})
export class TagRemoval {
  readonly tag = input.required<TagResponse>();
  readonly removed = output<void>();
  private readonly tags = inject(TagsService);
  private readonly copy = inject(TagCopy).text;
  private readonly destroyRef = inject(DestroyRef);
  protected readonly busy = signal(false);
  protected readonly failure = signal<unknown>(null);
  protected readonly action = computed(() => this.tag().origin === 'SELF_CREATED' ? this.copy().deleteTag : this.copy().releaseTag);
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }

  protected async remove(): Promise<void> {
    const tag = this.tag();
    // ponytail: the browser supplies confirmation, focus handling and keyboard controls.
    if (this.busy() || !window.confirm(`${tag.publicId}\n\n${tag.origin === 'SELF_CREATED' ? this.copy().deleteConfirm : this.copy().releaseConfirm}`)) return;
    this.busy.set(true); this.failure.set(null);
    try {
      await firstValueFrom(tag.origin === 'SELF_CREATED'
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
