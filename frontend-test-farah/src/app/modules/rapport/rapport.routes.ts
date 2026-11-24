import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';

const admin = { canActivate: [roleGuard], data: { roles: ['ADMIN'] } };

export const rapportRoutes: Routes = [
  { path: '', title: 'Rapport d\'activité · LIMTIC', loadComponent: () => import('./pages/campagne.component').then(c => c.CampagneComponent) },
  { path: 'mon-activite', title: 'Mon activité · LIMTIC', loadComponent: () => import('./pages/mon-activite.component').then(c => c.MonActiviteComponent) },
  { path: 'saisie/:categorie', title: 'Saisie · LIMTIC', loadComponent: () => import('./pages/saisie.component').then(c => c.SaisieComponent) },
  { path: 'identite', title: 'Identification · LIMTIC', ...admin, loadComponent: () => import('./pages/identite.component').then(c => c.IdentiteComponent) },
  { path: 'resume', title: 'Résumé · LIMTIC', ...admin, loadComponent: () => import('./pages/resume.component').then(c => c.ResumeComponent) },
  { path: 'budget', title: 'Situation financière · LIMTIC', ...admin, loadComponent: () => import('./pages/budget.component').then(c => c.BudgetComponent) },
  { path: 'controles', title: 'Contrôles · LIMTIC', ...admin, loadComponent: () => import('./pages/controles.component').then(c => c.ControlesComponent) },
  { path: 'apercu', title: 'Aperçu · LIMTIC', ...admin, loadComponent: () => import('./pages/apercu.component').then(c => c.ApercuComponent) },
  { path: 'rapports', title: 'Rapports · LIMTIC', ...admin, loadComponent: () => import('./pages/rapports.component').then(c => c.RapportsComponent) },
  { path: 'parametres', title: 'Paramètres · LIMTIC', ...admin, loadComponent: () => import('./pages/parametres.component').then(c => c.ParametresComponent) },
  { path: 'journal', title: 'Journal · LIMTIC', ...admin, loadComponent: () => import('./pages/journal.component').then(c => c.JournalComponent) },
];
