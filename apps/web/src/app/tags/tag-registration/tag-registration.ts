import { translateSection } from '../../core/translations';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { httpResource } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrentUserResponse, RegistrationView, TagRegistrationService } from '@lost-key-finder/api-client';
import { firstValueFrom } from 'rxjs';
import { tagError } from '../tag-error';
import { Auth } from '../../auth/auth';

@Component({
  selector: 'app-tag-registration',
  imports: [RouterLink],
  templateUrl: './tag-registration.html',
  styleUrl: './tag-registration.scss',
})
export class TagRegistration {
  protected readonly copy = translateSection('tags');
  private readonly auth = inject(Auth);
  private readonly route = inject(ActivatedRoute);
  private readonly registrations = inject(TagRegistrationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly params = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  private readonly flowId = computed(() => this.params().get('flowId') ?? '');
  protected readonly view = httpResource<RegistrationView>(() => this.auth.signedIn() ? `/api/v1/tag-registrations/${encodeURIComponent(this.flowId())}` : undefined);
  protected readonly user = httpResource<CurrentUserResponse>(() => this.auth.signedIn() ? '/api/v1/me' : undefined);
  protected readonly busy = signal(false);
  protected readonly failure = signal<unknown>(null);
  protected errorText(error: unknown): string { return tagError(error, this.copy()); }

  protected async complete(): Promise<void> {
    if (this.busy() || !this.view.hasValue() || !this.user.hasValue()) return;
    this.busy.set(true); this.failure.set(null);
    const flowId = this.flowId();
    try {
      const tag = await firstValueFrom(this.registrations.tagRegistrationControllerComplete({ flowId }));
      if (!this.destroyRef.destroyed && this.flowId() === flowId) await this.router.navigateByUrl(`/tags/${tag.id}`, { replaceUrl: true });
    } catch (error) { if (!this.destroyRef.destroyed) this.failure.set(error); }
    finally { if (!this.destroyRef.destroyed) this.busy.set(false); }
  }
}
