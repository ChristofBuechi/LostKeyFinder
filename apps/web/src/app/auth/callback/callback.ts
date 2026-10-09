import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-callback',
  imports: [RouterLink],
  templateUrl: './callback.html',
  styleUrl: './callback.scss',
})
export class Callback implements OnInit {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  protected readonly language = inject(LanguageService);
  protected readonly failed = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      await this.auth.completeCallback();
      await this.router.navigateByUrl('/account', { replaceUrl: true });
    } catch {
      this.failed.set(true);
    }
  }
}
