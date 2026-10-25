import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { Home } from './home/home';
import { NotFound } from './not-found/not-found';
import { authGuard } from './auth/auth-guard';

export const routes: Routes = [
  { path: '', component: Home, title: 'Lost Key Finder' },
  { path: 'login/link-sent', loadComponent: () => import('./auth/link-sent/link-sent').then(m => m.LinkSent), title: 'Lost Key Finder' },
  { path: 'f/:publicId', loadComponent: () => import('./finder/found-tag/found-tag').then(m => m.FoundTag), title: 'Lost Key Finder' },
  { path: 'login', pathMatch: 'full', redirectTo: ({ fragment }) => inject(Router).createUrlTree(['/'], { fragment: fragment === 'finder' ? 'finder' : 'owner' }) },
  { path: 'auth/callback', loadComponent: () => import('./auth/callback/callback').then(m => m.Callback), title: 'Lost Key Finder' },
  { path: 'account', canActivate: [authGuard], loadComponent: () => import('./account/account').then(m => m.Account), title: 'Lost Key Finder' },
  { path: '**', component: NotFound, title: 'Lost Key Finder' },
];
