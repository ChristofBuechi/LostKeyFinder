import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Auth } from '../auth';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-callback',
  imports: [RouterLink, FormsModule],
  templateUrl: './callback.html',
  styleUrl: './callback.scss',
})
export class Callback implements OnInit {
  protected readonly auth = inject(Auth);
  protected email = '';
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly language = inject(LanguageService);
  protected readonly failed = signal(false);

  async ngOnInit(): Promise<void> {
    await this.complete();
  }

  protected async complete(): Promise<void> {
    try {
      await this.auth.completeCallback(this.email);
      if (this.destroyRef.destroyed || this.auth.needsEmail()) return;
      await this.router.navigateByUrl('/account?entry=login', { replaceUrl: true });
    } catch {
      this.failed.set(true);
    }
  }
}
