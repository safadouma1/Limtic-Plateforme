import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-projets',
  imports: [RouterLink],
  template: `
    <a routerLink="/moderateur/contenu" class="btn btn-ghost btn-sm mb-16"><span class="mi sm">arrow_back</span> Contenu accueil</a>
    <h1 class="page-title">Projets récents</h1>
    <div class="card empty mt-16">
      <span class="mi">rocket_launch</span>
      <p>La gestion des projets n'est pas encore disponible : elle nécessite une entité et une API dédiées côté backend.</p>
    </div>
  `,
})
export class ProjetsComponent {}
