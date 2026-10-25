import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { httpResource } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormField, disabled, email, form, maxLength, required } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';
import { CurrentUserResponse, PublicTagResponse, TagResponse, TagRegistrationService } from '@lost-key-finder/api-client';
import { Auth } from '../../auth/auth';
import { TagCopy } from '../../tags/tag-copy';
import { tagError } from '../../tags/tag-error';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-found-tag',
  imports: [RouterLink, FormField],
  templateUrl: './found-tag.html',
  styleUrl: './found-tag.scss',
})
export class FoundTag {
  protected readonly language = inject(LanguageService);
  protected readonly auth = inject(Auth);
  protected readonly copy = inject(TagCopy).text;
  private readonly route = inject(ActivatedRoute);
  private readonly params = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  protected readonly publicId = computed(() => this.params().get('publicId') ?? '');
  protected readonly resolution = httpResource<PublicTagResponse>(() => `/api/v1/public/tags/${encodeURIComponent(this.publicId())}`);
  protected readonly own = httpResource<TagResponse>(() => this.auth.signedIn() && this.resolution.hasValue() && this.resolution.value().action === 'REPORT_FOUND'
    ? `/api/v1/tags/by-public/${encodeURIComponent(this.publicId())}` : undefined);
  protected readonly currentUser = httpResource<CurrentUserResponse>(() => this.auth.signedIn() && this.resolution.hasValue() && this.resolution.value().action === 'REGISTER'
    ? '/api/v1/me' : undefined);
  private readonly registrations = inject(TagRegistrationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly busy = signal(false);
  protected readonly failure = signal<unknown>(null);
  protected readonly submitted = signal(false);
  private readonly model = signal({ email: '' });
  protected readonly emailForm = form(this.model, path => {
    required(path.email); email(path.email); maxLength(path.email, 254);
    disabled(path.email, () => this.busy() || !this.auth.configured);
  });
  private started: { email: string; flowId: string; publicId: string; expiresAt: string } | null = null;
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    this.submitted.set(true);
    if (this.busy() || this.emailForm().invalid() || !this.auth.configured) return;
    this.busy.set(true); this.failure.set(null);
    try {
      const address = this.model().email.trim();
      const publicId = this.publicId();
      if (!this.started || this.started.email !== address || this.started.publicId !== publicId || Date.parse(this.started.expiresAt) <= Date.now()) {
        const draft = await firstValueFrom(this.registrations.tagRegistrationControllerStart({ publicId, body: { email: address } }));
        if (this.destroyRef.destroyed || this.publicId() !== publicId) return;
        this.started = { email: address, flowId: draft.flowId, publicId, expiresAt: draft.expiresAt };
      }
      await this.auth.sendLink(address, this.started.flowId);
      if (!this.destroyRef.destroyed && this.publicId() === publicId) await this.router.navigateByUrl('/login/link-sent', { replaceUrl: true });
    } catch (error) { if (!this.destroyRef.destroyed) this.failure.set(error); }
    finally { if (!this.destroyRef.destroyed) this.busy.set(false); }
  }

  protected async register(): Promise<void> {
    if (this.busy() || !this.currentUser.hasValue()) return;
    this.busy.set(true); this.failure.set(null);
    const publicId = this.publicId();
    try {
      const tag = await firstValueFrom(this.registrations.tagRegistrationControllerDirect({ publicId }));
      if (!this.destroyRef.destroyed && this.publicId() === publicId) await this.router.navigateByUrl(`/tags/${tag.id}`, { replaceUrl: true });
    } catch (error) { if (!this.destroyRef.destroyed) this.failure.set(error); }
    finally { if (!this.destroyRef.destroyed) this.busy.set(false); }
  }
}
