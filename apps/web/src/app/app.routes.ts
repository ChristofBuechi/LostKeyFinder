import { Routes } from '@angular/router';
import { Home } from './home/home';
import { NotFound } from './not-found/not-found';
import { authGuard } from './auth/auth-guard';

export const routes: Routes = [
  { path: '', component: Home, title: 'Lost Key Finder' },
  { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.Login), title: 'Lost Key Finder' },
  { path: 'auth/callback', loadComponent: () => import('./auth/callback/callback').then(m => m.Callback), title: 'Lost Key Finder' },
  { path: 'account', canActivate: [authGuard], loadComponent: () => import('./account/account').then(m => m.Account), title: 'Lost Key Finder' },
  { path: '**', component: NotFound, title: 'Lost Key Finder' },
];
