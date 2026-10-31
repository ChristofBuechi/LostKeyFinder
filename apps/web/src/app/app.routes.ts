import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { Home } from './home/home';
import { NotFound } from './not-found/not-found';
import { authGuard } from './auth/auth-guard';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login/link-sent', loadComponent: () => import('./auth/link-sent/link-sent').then(m => m.LinkSent) },
  { path: 'f/:publicId', loadComponent: () => import('./finder/found-tag/found-tag').then(m => m.FoundTag) },
  { path: 'login', pathMatch: 'full', redirectTo: ({ fragment }) => inject(Router).createUrlTree(['/'], { fragment: fragment === 'finder' ? 'finder' : 'owner' }) },
  { path: 'auth/callback', loadComponent: () => import('./auth/callback/callback').then(m => m.Callback) },
  { path: 'chats', canActivate: [authGuard], loadComponent: () => import('./chat/chat-list').then(m => m.ChatList) },
  { path: 'chats/:chatId', canActivate: [authGuard], loadComponent: () => import('./chat/chat-detail').then(m => m.ChatDetail) },
  { path: 'finder-reports/:flowId', canActivate: [authGuard], loadComponent: () => import('./chat/finder-complete').then(m => m.FinderComplete) },
  { path: 'account/deleted', loadComponent: () => import('./account/account-deletion/account-deleted').then(m => m.AccountDeleted) },
  { path: 'account/deletion', canActivate: [authGuard], data: { deletion: true }, loadComponent: () => import('./account/account-deletion/account-deletion').then(m => m.AccountDeletion) },
  { path: 'account', canActivate: [authGuard], loadComponent: () => import('./account/account').then(m => m.Account) },
  { path: 'tag-registrations/:flowId', canActivate: [authGuard], loadComponent: () => import('./tags/tag-registration/tag-registration').then(m => m.TagRegistration) },
  { path: 'tags/print', canActivate: [authGuard], loadComponent: () => import('./tags/tag-print/tag-print').then(m => m.TagPrint) },
  { path: 'tags/:tagId', canActivate: [authGuard], loadComponent: () => import('./tags/tag-detail/tag-detail').then(m => m.TagDetail) },
  { path: 'admin/tag-batches', canActivate: [authGuard], data: { admin: true }, loadComponent: () => import('./tags/tag-batches/tag-batches').then(m => m.TagBatches) },
  { path: 'admin/tags/:tagId', canActivate: [authGuard], data: { admin: true }, loadComponent: () => import('./tags/tag-detail/tag-detail').then(m => m.TagDetail) },
  { path: 'tags', canActivate: [authGuard], loadComponent: () => import('./tags/tag-list/tag-list').then(m => m.TagList) },
  { path: 'admin/tags', canActivate: [authGuard], data: { admin: true }, loadComponent: () => import('./tags/tag-list/tag-list').then(m => m.TagList) },
  { path: '**', component: NotFound },
];
