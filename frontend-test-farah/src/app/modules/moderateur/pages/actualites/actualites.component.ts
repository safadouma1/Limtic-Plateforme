import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-actualites',
  imports: [RouterLink],
  template: `
    <a routerLink="/moderateur/contenu" class="btn btn-ghost btn-sm mb-16"><span class="mi sm">arrow_back</span> Contenu accueil</a>
    <h1 class="page-title">Actualités & annonces</h1>
    <div class="card empty mt-16">
      <span class="mi">campaign</span>
      <p>La gestion des actualités n'est pas encore disponible : elle nécessite une entité et une API dédiées côté backend.</p>
    </div>
  `,
})
export class ActualitesComponent {}
