import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  { path: '', redirectTo: 'chercheurs', pathMatch: 'full' },
  { path: 'chercheurs', title: 'Gestion chercheurs · LIMTIC',
    loadComponent: () => import('./pages/chercheurs/chercheurs.component').then(c => c.ChercheursAdminComponent) },
  { path: 'chercheurs/nouveau', title: 'Nouveau chercheur · LIMTIC',
    loadComponent: () => import('./pages/chercheurs/chercheur-form/chercheur-form.component').then(c => c.ChercheurFormComponent) },
  { path: 'chercheurs/:id/modifier', title: 'Modifier chercheur · LIMTIC',
    loadComponent: () => import('./pages/chercheurs/chercheur-form/chercheur-form.component').then(c => c.ChercheurFormComponent) },
  { path: 'domaines', title: 'Gestion domaines · LIMTIC',
    loadComponent: () => import('./pages/domaines/domaines.component').then(c => c.DomainesAdminComponent) },
  { path: 'publications', title: 'Gestion publications · LIMTIC',
    loadComponent: () => import('./pages/publications/publications.component').then(c => c.PublicationsAdminComponent) },
  { path: 'evenements', title: 'Événements · LIMTIC',
    loadComponent: () => import('./pages/site/evenements-admin.component').then(c => c.EvenementsAdminComponent) },
  { path: 'site', title: 'Paramètres du site · LIMTIC',
    loadComponent: () => import('./pages/site/parametres-site.component').then(c => c.ParametresSiteComponent) },
  { path: 'messages', title: 'Messages · LIMTIC',
    loadComponent: () => import('./pages/site/messages.component').then(c => c.MessagesComponent) },
  { path: 'comptes', title: 'Comptes & rôles · LIMTIC',
    loadComponent: () => import('./pages/comptes/comptes.component').then(c => c.ComptesComponent) },
];
