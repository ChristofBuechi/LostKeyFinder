import { Routes } from '@angular/router';
import { Home } from './home/home';
import { NotFound } from './not-found/not-found';

export const routes: Routes = [
  { path: '', component: Home, title: 'Lost Key Finder' },
  { path: '**', component: NotFound, title: 'Lost Key Finder' },
];
