import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-link-sent',
  imports: [RouterLink],
  templateUrl: './link-sent.html',
  styleUrl: './link-sent.scss',
})
export class LinkSent {
  protected readonly language = inject(LanguageService);
}
