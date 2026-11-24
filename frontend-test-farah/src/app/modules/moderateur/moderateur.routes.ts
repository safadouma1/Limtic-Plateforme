import { Routes } from '@angular/router';

export const moderateurRoutes: Routes = [
  { path: '', redirectTo: 'contenu', pathMatch: 'full' },
  { path: 'contenu', title: "Contenu de l'accueil · LIMTIC",
    loadComponent: () => import('./pages/accueil/accueil.component').then(c => c.AccueilComponent) },
  { path: 'actualites', title: 'Actualités · LIMTIC',
    loadComponent: () => import('./pages/actualites/actualites.component').then(c => c.ActualitesComponent) },
  { path: 'projets', title: 'Projets · LIMTIC',
    loadComponent: () => import('./pages/projets/projets.component').then(c => c.ProjetsComponent) },
];
