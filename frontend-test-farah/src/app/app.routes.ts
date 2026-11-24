import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  // Mot de passe oublié / réinitialisation : accessibles connecté ou non
  { path: 'auth/mot-de-passe-oublie', title: 'Mot de passe oublié · LIMTIC',
    loadComponent: () => import('./pages/auth/reinitialisation/reinitialisation.component').then(c => c.OubliComponent) },
  { path: 'auth/reinitialiser', title: 'Nouveau mot de passe · LIMTIC',
    loadComponent: () => import('./pages/auth/reinitialisation/reinitialisation.component').then(c => c.ReinitialisationComponent) },
  { path: 'auth', canActivate: [guestGuard],
    loadChildren: () => import('./pages/auth/auth.routes').then(r => r.authRoutes) },

  // Site public (Partie 1) : accueil, laboratoire, chercheurs, publications, événements, contact…
  { path: '', loadComponent: () => import('./site/site-layout.component').then(c => c.SiteLayoutComponent),
    loadChildren: () => import('./site/site.routes').then(r => r.siteRoutes) },

  // Espace connecté avec layout
  {
    path: '',
    loadComponent: () => import('./layout/layout.component').then(c => c.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', title: 'Tableau de bord · LIMTIC',
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(c => c.DashboardComponent) },
      { path: 'admin', canActivate: [roleGuard], data: { roles: ['ADMIN'] },
        loadChildren: () => import('./modules/admin/admin.routes').then(r => r.adminRoutes) },
      { path: 'moderateur', canActivate: [roleGuard], data: { roles: ['ADMIN', 'MODERATEUR'] },
        loadChildren: () => import('./modules/moderateur/moderateur.routes').then(r => r.moderateurRoutes) },
      { path: 'utilisateur',
        loadChildren: () => import('./modules/utilisateur/utilisateur.routes').then(r => r.utilisateurRoutes) },
      { path: 'rapport', canActivate: [roleGuard], data: { roles: ['DIRECTEUR', 'ADMIN', 'CHERCHEUR'] },
        loadChildren: () => import('./modules/rapport/rapport.routes').then(r => r.rapportRoutes) },
      { path: 'ia',
        loadChildren: () => import('./modules/ia/ia.routes').then(r => r.iaRoutes) },
    ],
  },
  { path: '**', redirectTo: '' },
];
