import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageService } from '../../core/language.service';

@Component({
  selector: 'app-found-tag',
  imports: [RouterLink],
  templateUrl: './found-tag.html',
  styleUrl: './found-tag.scss',
})
export class FoundTag {
  protected readonly language = inject(LanguageService);
}
