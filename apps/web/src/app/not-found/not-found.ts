import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="not-found" aria-labelledby="not-found-title">
      <p class="eyebrow">404</p>
      <h1 id="not-found-title">Diese Seite ist nicht zurückzufinden.</h1>
      <a routerLink="/">Zur Startseite</a>
    </section>
   `,
  styles: [`
    .not-found { width: min(100% - 3rem, 76rem); margin: 0 auto; padding-block: 8rem; }
    .eyebrow { color: var(--signal); font-weight: 800; letter-spacing: .14em; }
    h1 { max-width: 12ch; font: 400 clamp(3rem, 8vw, 6rem)/.92 Georgia, 'Times New Roman', serif; letter-spacing: -.06em; }
    a { color: var(--ink); font-weight: 750; text-decoration-color: var(--signal); text-underline-offset: .3rem; }
   `],
})
export class NotFound {}
