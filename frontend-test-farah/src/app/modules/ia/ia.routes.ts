import { Routes } from '@angular/router';

export const iaRoutes: Routes = [
  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: 'chat', title: 'Assistant IA · LIMTIC',
    loadComponent: () => import('./pages/chat/ia-chat.component').then(c => c.IaChatComponent) },
  { path: 'recherche', title: 'Recherche sémantique · LIMTIC',
    loadComponent: () => import('./pages/recherche/smart-search.component').then(c => c.SmartSearchComponent) },
  { path: 'recommandations', title: 'Recommandations · LIMTIC',
    loadComponent: () => import('./pages/recommandations/recommandations.component').then(c => c.RecommandationsComponent) },
  { path: 'outils', title: 'Résumé & mots-clés · LIMTIC',
    loadComponent: () => import('./pages/outils/ia-outils.component').then(c => c.IaOutilsComponent) },
  { path: 'analytics', title: 'Analytics · LIMTIC',
    loadComponent: () => import('./pages/analytics/analytics.component').then(c => c.AnalyticsComponent) },
];
