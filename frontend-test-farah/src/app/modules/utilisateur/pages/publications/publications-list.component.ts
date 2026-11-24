import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicationService } from '../../../../core/services/publication.service';
import { Publication } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-publications-list',
  imports: [RouterLink, FormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Publications</h1>
        <p class="page-subtitle">Consulter les publications scientifiques de la plateforme</p>
      </div>
      <a routerLink="/ia/recherche" class="btn btn-ia"><span class="mi sm">manage_search</span> Recherche sémantique IA</a>
    </div>

    <div class="row mb-24">
      <div class="search-input" style="flex:1;max-width:420px">
        <span class="mi">search</span>
        <input class="form-control" type="search" placeholder="Titre, auteur, mot-clé…" [ngModel]="q()" (ngModelChange)="q.set($event)" />
      </div>
      <select class="form-control" style="width:auto" [ngModel]="annee()" (ngModelChange)="annee.set($event)">
        <option [ngValue]="null">Toutes les années</option>
        @for (a of annees(); track a) { <option [ngValue]="a">{{ a }}</option> }
      </select>
      <span class="muted small">{{ filtrees().length }} publication(s)</span>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <div class="stack">
      @for (p of filtrees(); track p.id) {
        <a [routerLink]="[p.id]" class="card card-link card-body">
          <div class="row" style="justify-content:space-between;align-items:flex-start">
            <h3 style="font-size:15.5px;font-weight:600;flex:1;min-width:220px">{{ p.titre }}</h3>
            <div class="row">
              @if (p.domaine) { <span class="chip chip-blue">{{ p.domaine.nom }}</span> }
              @if (p.annee) { <span class="chip">{{ p.annee }}</span> }
            </div>
          </div>
          @if (p.resume) { <p class="muted small clamp-3 mt-8">{{ p.resume }}</p> }
          <div class="row mt-8" style="justify-content:space-between">
            <div class="chips">@for (k of p.motsCles; track k) { <span class="chip chip-purple">{{ k }}</span> }</div>
            <span class="small muted">{{ auteurs(p) }}</span>
          </div>
        </a>
      } @empty {
        @if (!loading()) { <div class="card empty"><span class="mi">search_off</span><p>Aucune publication ne correspond.</p></div> }
      }
    </div>
  `,
})
export class PublicationsListComponent implements OnInit {
  private svc = inject(PublicationService);
  publications = signal<Publication[]>([]);
  q = signal('');
  annee = signal<number | null>(null);
  loading = signal(true);
  error = signal('');

  annees = computed(() => [...new Set(this.publications().map(p => p.annee).filter((a): a is number => !!a))].sort((a, b) => b - a));
  filtrees = computed(() => {
    const q = this.q().toLowerCase().trim();
    const a = this.annee();
    return this.publications().filter(p =>
      (a == null || p.annee === a) &&
      (!q || `${p.titre} ${p.resume ?? ''} ${p.motsCles.join(' ')} ${this.auteurs(p)}`.toLowerCase().includes(q)));
  });

  ngOnInit() {
    this.svc.getAll().subscribe({
      next: p => { this.publications.set(p); this.loading.set(false); },
      error: e => { this.error.set(messageErreur(e)); this.loading.set(false); },
    });
  }

  auteurs(p: Publication) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
}
