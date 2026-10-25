import { Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TagResponse } from '@lost-key-finder/api-client';
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
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }
}
