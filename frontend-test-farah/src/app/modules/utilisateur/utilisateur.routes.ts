import { Routes } from '@angular/router';

export const utilisateurRoutes: Routes = [
  { path: '', redirectTo: 'chercheurs', pathMatch: 'full' },
  { path: 'chercheurs', title: 'Chercheurs · LIMTIC',
    loadComponent: () => import('./pages/chercheurs/chercheurs-list.component').then(c => c.ChercheursListComponent) },
  { path: 'chercheurs/:id', title: 'Profil chercheur · LIMTIC',
    loadComponent: () => import('./pages/chercheurs/chercheur-detail.component').then(c => c.ChercheurDetailComponent) },
  { path: 'publications', title: 'Publications · LIMTIC',
    loadComponent: () => import('./pages/publications/publications-list.component').then(c => c.PublicationsListComponent) },
  { path: 'publications/:id', title: 'Publication · LIMTIC',
    loadComponent: () => import('./pages/publications/publication-detail.component').then(c => c.PublicationDetailComponent) },
  { path: 'domaines', title: 'Domaines · LIMTIC',
    loadComponent: () => import('./pages/domaines/domaines-list.component').then(c => c.DomainesListComponent) },
  { path: 'ma-fiche', title: 'Ma page chercheur · LIMTIC',
    loadComponent: () => import('./pages/profil/ma-fiche.component').then(c => c.MaFicheComponent) },
  { path: 'profil', title: 'Mon profil · LIMTIC',
    loadComponent: () => import('./pages/profil/profil.component').then(c => c.ProfilComponent) },
];
