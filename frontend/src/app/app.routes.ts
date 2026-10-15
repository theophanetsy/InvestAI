import type { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
import { HOME_URL } from './core/auth/return-url';

/** Every page is loaded on demand; all of them but the login screen require a session. */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Connexion · InvestAI',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: HOME_URL },
      {
        path: 'dashboard',
        title: 'Tableau de bord · InvestAI',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'assistant',
        title: 'Assistant · InvestAI',
        loadComponent: () => import('./features/chat/chat').then((m) => m.Chat),
      },
      {
        path: 'actu',
        title: 'Actu · InvestAI',
        loadComponent: () => import('./features/news/news').then((m) => m.News),
      },
      {
        path: 'simulation',
        title: 'Simulation · InvestAI',
        loadComponent: () => import('./features/simulation/simulation').then((m) => m.Simulation),
      },
      {
        path: '**',
        title: 'Page introuvable · InvestAI',
        loadComponent: () => import('./core/layout/not-found').then((m) => m.NotFound),
      },
    ],
  },
];
