import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { translateSection } from '../../core/translations';

@Component({ selector: 'app-account-deletion-email-sent', imports: [RouterLink],
  template: `<section class="auth-panel"><h1>{{ copy().emailSent }}</h1><p>{{ copy().emailSentText }}</p><a routerLink="/account">{{ copy().back }}</a></section>`,
})
export class AccountDeletionEmailSent { protected readonly copy = translateSection('deletion'); }
