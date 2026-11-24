import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-accueil',
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Contenu de la page d'accueil</h1>
        <p class="page-subtitle">Modérateur · gérer le contenu affiché publiquement</p>
      </div>
      <a routerLink="/" class="btn btn-ghost" target="_blank"><span class="mi sm">open_in_new</span> Voir l'accueil</a>
    </div>
    <div class="alert alert-info"><span class="mi sm">info</span>
      La page d'accueil affiche automatiquement les dernières publications, les domaines et les statistiques réelles de la plateforme.
    </div>
    <div class="grid-2">
      <a routerLink="/moderateur/actualites" class="card card-link card-body">
        <div class="stat-icon si-blue"><span class="mi">campaign</span></div>
        <div class="card-title">Actualités & annonces</div>
        <p class="small muted mt-8">Publier des actualités sur la plateforme.</p>
      </a>
      <a routerLink="/moderateur/projets" class="card card-link card-body">
        <div class="stat-icon si-purple"><span class="mi">rocket_launch</span></div>
        <div class="card-title">Projets récents</div>
        <p class="small muted mt-8">Choisir les projets mis en avant.</p>
      </a>
    </div>
  `,
})
export class AccueilComponent {}
