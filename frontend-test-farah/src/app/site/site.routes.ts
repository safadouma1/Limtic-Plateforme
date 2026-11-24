import { Routes } from '@angular/router';

/** Front-office public (Partie 1, chapitre 3) : accessible sans connexion. */
export const siteRoutes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./pages/accueil.component').then(c => c.SiteAccueilComponent) },
  { path: 'laboratoire', title: 'Le laboratoire · LIMTIC', loadComponent: () => import('./pages/laboratoire.component').then(c => c.SiteLaboratoireComponent) },
  { path: 'axes', title: 'Axes de recherche · LIMTIC', loadComponent: () => import('./pages/axes.component').then(c => c.SiteAxesComponent) },
  { path: 'axes/:id', loadComponent: () => import('./pages/axes.component').then(c => c.SiteAxeComponent) },
  { path: 'chercheurs', title: 'Chercheurs · LIMTIC', loadComponent: () => import('./pages/chercheurs.component').then(c => c.SiteChercheursComponent) },
  { path: 'chercheurs/:id', loadComponent: () => import('./pages/chercheur.component').then(c => c.SiteChercheurComponent) },
  { path: 'publications', title: 'Publications · LIMTIC', loadComponent: () => import('./pages/publications.component').then(c => c.SitePublicationsComponent) },
  { path: 'publications/:id', loadComponent: () => import('./pages/publication.component').then(c => c.SitePublicationComponent) },
  { path: 'evenements', title: 'Événements · LIMTIC', loadComponent: () => import('./pages/evenements.component').then(c => c.SiteEvenementsComponent) },
  { path: 'evenements/:id', loadComponent: () => import('./pages/evenements.component').then(c => c.SiteEvenementComponent) },
  { path: 'doctorants', title: 'Doctorants · LIMTIC', data: { type: 'doctorants' }, loadComponent: () => import('./pages/etudiants.component').then(c => c.SiteEtudiantsComponent) },
  { path: 'masteriens', title: 'Étudiants en mastère · LIMTIC', data: { type: 'masteriens' }, loadComponent: () => import('./pages/etudiants.component').then(c => c.SiteEtudiantsComponent) },
  { path: 'contact', title: 'Contact · LIMTIC', loadComponent: () => import('./pages/contact.component').then(c => c.SiteContactComponent) },
];
