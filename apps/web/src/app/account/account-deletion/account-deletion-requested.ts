import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { translateSection } from '../../core/translations';

@Component({ selector: 'app-account-deletion-requested', imports: [RouterLink],
  template: `<section class="auth-panel"><h1>{{ copy().done }}</h1><p>{{ copy().doneText }}</p><a routerLink="/">{{ copy().home }}</a></section>`,
})
export class AccountDeletionRequested { protected readonly copy = translateSection('deletion'); }
