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
  { path: 'chats', canActivate: [authGuard], loadComponent: () => import('./chat/chat-list').then(m => m.ChatList), title: 'Lost Key Finder' },
  { path: 'chats/:chatId', canActivate: [authGuard], loadComponent: () => import('./chat/chat-detail').then(m => m.ChatDetail), title: 'Lost Key Finder' },
  { path: 'finder-reports/:flowId', canActivate: [authGuard], loadComponent: () => import('./chat/finder-complete').then(m => m.FinderComplete), title: 'Lost Key Finder' },
  { path: 'account', canActivate: [authGuard], loadComponent: () => import('./account/account').then(m => m.Account), title: 'Lost Key Finder' },
  { path: 'tag-registrations/:flowId', canActivate: [authGuard], loadComponent: () => import('./tags/tag-registration/tag-registration').then(m => m.TagRegistration), title: 'Lost Key Finder' },
  { path: 'tags/:tagId', canActivate: [authGuard], loadComponent: () => import('./tags/tag-detail/tag-detail').then(m => m.TagDetail), title: 'Lost Key Finder' },
  { path: 'admin/tag-batches', canActivate: [authGuard], data: { admin: true }, loadComponent: () => import('./tags/tag-batches/tag-batches').then(m => m.TagBatches), title: 'Lost Key Finder' },
  { path: 'admin/tags/:tagId', canActivate: [authGuard], data: { admin: true }, loadComponent: () => import('./tags/tag-detail/tag-detail').then(m => m.TagDetail), title: 'Lost Key Finder' },
  { path: 'tags', canActivate: [authGuard], loadComponent: () => import('./tags/tag-list/tag-list').then(m => m.TagList), title: 'Lost Key Finder' },
  { path: 'admin/tags', canActivate: [authGuard], data: { admin: true }, loadComponent: () => import('./tags/tag-list/tag-list').then(m => m.TagList), title: 'Lost Key Finder' },
  { path: '**', component: NotFound, title: 'Lost Key Finder' },
];
