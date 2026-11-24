import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChercheurService } from '../../../../core/services/chercheur.service';
import { DomaineService } from '../../../../core/services/domaine.service';
import { Chercheur, Domaine } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-chercheurs-list',
  imports: [RouterLink, FormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Chercheurs</h1>
        <p class="page-subtitle">Consulter les profils des chercheurs de la plateforme</p>
      </div>
    </div>

    <div class="row mb-24">
      <div class="search-input" style="flex:1;max-width:360px">
        <span class="mi">search</span>
        <input class="form-control" type="search" placeholder="Nom, établissement, spécialité…"
               [ngModel]="q()" (ngModelChange)="q.set($event)" />
      </div>
      <select class="form-control" style="width:auto;min-width:200px" [ngModel]="domaineId()" (ngModelChange)="domaineId.set($event)">
        <option [ngValue]="null">Tous les domaines</option>
        @for (d of domaines(); track d.id) { <option [ngValue]="d.id">{{ d.nom }}</option> }
      </select>
      <span class="muted small">{{ filtres().length }} résultat(s)</span>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <div class="grid-cards">
      @for (c of filtres(); track c.id) {
        <a [routerLink]="[c.id]" class="card card-link card-body">
          <div class="r-cell mb-16">
            <div class="r-avatar" style="width:44px;height:44px;font-size:14px">{{ c.prenom.charAt(0) }}{{ c.nom.charAt(0) }}</div>
            <div style="min-width:0">
              <div style="font-weight:600">{{ c.prenom }} {{ c.nom }}</div>
              <div class="small muted">{{ c.etablissement || '—' }}</div>
            </div>
          </div>
          @if (c.domaine) { <span class="chip chip-purple">{{ c.domaine.nom }}</span> }
          @if (c.specialite) { <div class="small muted mt-8">{{ c.specialite }}</div> }
          <div class="small mt-8" style="color:#8ab4ff"><span class="mi sm" style="vertical-align:-3px">article</span> {{ c.nbPublications }} publication(s)</div>
        </a>
      } @empty {
        @if (!loading()) { <div class="card empty" style="grid-column:1/-1"><span class="mi">person_search</span><p>Aucun chercheur ne correspond.</p></div> }
      }
    </div>
  `,
})
export class ChercheursListComponent implements OnInit {
  private svc = inject(ChercheurService);
  private dsvc = inject(DomaineService);

  /** Paramètre ?domaine=ID (lien depuis la page Domaines). */
  domaine = input<string>();

  chercheurs = signal<Chercheur[]>([]);
  domaines = signal<Domaine[]>([]);
  q = signal('');
  domaineId = signal<number | null>(null);
  loading = signal(true);
  error = signal('');

  filtres = computed(() => {
    const q = this.q().toLowerCase().trim();
    const d = this.domaineId();
    return this.chercheurs().filter(c =>
      (d == null || c.domaine?.id === d) &&
      (!q || `${c.prenom} ${c.nom} ${c.etablissement ?? ''} ${c.specialite ?? ''} ${c.domaine?.nom ?? ''}`.toLowerCase().includes(q)));
  });

  ngOnInit() {
    const d = Number(this.domaine());
    if (d) this.domaineId.set(d);
    this.dsvc.getAll().subscribe({ next: d => this.domaines.set(d), error: () => {} });
    this.svc.getAll().subscribe({
      next: c => { this.chercheurs.set(c); this.loading.set(false); },
      error: e => { this.error.set(messageErreur(e)); this.loading.set(false); },
    });
  }
}
