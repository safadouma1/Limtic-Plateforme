import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DomaineService } from '../../../../core/services/domaine.service';
import { Domaine } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-domaines-list',
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Domaines de recherche</h1>
        <p class="page-subtitle">Explorer les domaines de la plateforme</p>
      </div>
    </div>
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    <div class="grid-cards">
      @for (d of domaines(); track d.id) {
        <a routerLink="/utilisateur/chercheurs" [queryParams]="{ domaine: d.id }" class="card card-link card-body">
          <div class="stat-icon si-purple"><span class="mi">science</span></div>
          <div style="font-weight:600;font-size:15px">{{ d.nom }}</div>
          <div class="small muted mt-8">{{ d.description || '—' }}</div>
          <div class="row mt-16">
            <span class="chip chip-blue">{{ d.nbChercheurs }} chercheur(s)</span>
            <span class="chip chip-cyan">{{ d.nbPublications }} publication(s)</span>
          </div>
        </a>
      } @empty {
        <div class="card empty" style="grid-column:1/-1"><span class="mi">category</span><p>Aucun domaine créé.</p></div>
      }
    </div>
  `,
})
export class DomainesListComponent implements OnInit {
  private svc = inject(DomaineService);
  domaines = signal<Domaine[]>([]);
  error = signal('');
  ngOnInit() { this.svc.getAll().subscribe({ next: d => this.domaines.set(d), error: e => this.error.set(messageErreur(e)) }); }
}
