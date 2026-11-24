import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IaService, SearchResponse } from '../../../../core/services/ia.service';
import { PublicationService } from '../../../../core/services/publication.service';
import { Publication } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';
import { IaStatusBannerComponent } from '../ia-status-banner.component';
import { ResultatCardComponent } from '../resultat-card.component';

@Component({
  selector: 'app-recommandations',
  imports: [FormsModule, IaStatusBannerComponent, ResultatCardComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title"><span class="spark">✦</span> Recommandations</h1>
        <p class="page-subtitle">Choisissez une publication : l'IA trouve les 5 travaux les plus proches (similarité cosinus des embeddings Gemini).</p>
      </div>
    </div>

    <app-ia-status-banner />

    <div class="card card-body mb-24">
      <label class="form-label" for="pub">Publication de référence</label>
      <select id="pub" class="form-control" [ngModel]="selection()" (ngModelChange)="choisir($event)">
        <option [ngValue]="null" disabled>— Sélectionner une publication —</option>
        @for (p of publications(); track p.id) { <option [ngValue]="p.id">{{ p.titre }}@if (p.annee) { ({{ p.annee }}) }</option> }
      </select>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    @if (loading()) { <div class="stack">@for (i of [1, 2, 3]; track i) { <div class="skeleton" style="height:130px"></div> }</div> }

    @if (res(); as r) {
      @if (!loading()) {
        <div class="row mb-16">
          <span class="muted small">{{ r.resultats.length }} recommandation(s)</span>
          <span class="chip" [class.chip-gold]="r.mode === 'lexical'" [class.chip-purple]="r.mode !== 'lexical'">
            {{ r.mode === 'lexical' ? 'mode lexical (sans IA)' : 'embeddings Gemini' }}
          </span>
        </div>
        <div class="stack">
          @for (item of r.resultats; track item.id) {
            <app-resultat-card [resultat]="item" (similaires)="choisir($event)" />
          } @empty {
            <div class="card empty"><span class="mi">hub</span><p>Aucune publication similaire.</p></div>
          }
        </div>
      }
    }
  `,
})
export class RecommandationsComponent implements OnInit {
  private ia = inject(IaService);
  private pubSvc = inject(PublicationService);

  publications = signal<Publication[]>([]);
  selection = signal<number | null>(null);
  res = signal<SearchResponse | null>(null);
  loading = signal(false);
  error = signal('');

  ngOnInit() {
    this.pubSvc.getAll().subscribe({ next: p => this.publications.set(p), error: e => this.error.set(messageErreur(e)) });
  }

  choisir(id: number) {
    this.selection.set(id);
    this.loading.set(true);
    this.error.set('');
    this.ia.recommander(id).subscribe({
      next: r => { this.res.set(r); this.loading.set(false); },
      error: e => { this.error.set(messageErreur(e)); this.loading.set(false); },
    });
  }
}
