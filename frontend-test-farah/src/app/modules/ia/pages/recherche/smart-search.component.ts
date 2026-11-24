import { Component, inject, input, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IaService, SearchResponse } from '../../../../core/services/ia.service';
import { messageErreur } from '../../../../shared/erreur';
import { IaStatusBannerComponent } from '../ia-status-banner.component';
import { ResultatCardComponent } from '../resultat-card.component';

@Component({
  selector: 'app-smart-search',
  imports: [FormsModule, IaStatusBannerComponent, ResultatCardComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title"><span class="spark">✦</span> Recherche sémantique</h1>
        <p class="page-subtitle">Décrivez ce que vous cherchez avec vos mots : l'IA compare le sens de votre requête à celui des publications.</p>
      </div>
    </div>

    <app-ia-status-banner />

    <form class="bigsearch" (ngSubmit)="lancer()">
      <span class="mi">manage_search</span>
      <input type="search" name="q" [(ngModel)]="query" placeholder="Ex : détecter des tumeurs sur des images médicales" aria-label="Requête" />
      <button class="btn btn-primary" type="submit" [disabled]="loading() || !query.trim()">
        @if (loading()) { <span class="mi sm spin">autorenew</span> } @else { Rechercher }
      </button>
    </form>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (res(); as r) {
      <div class="row mb-16">
        <span class="muted small">{{ r.resultats.length }} publication(s) · {{ titre() }}</span>
        @if (r.mode === 'lexical') {
          <span class="chip chip-gold" title="Gemini non configuré ou indisponible : correspondance par mots (TF-IDF)">mode lexical (sans IA)</span>
        } @else {
          <span class="chip chip-purple">embeddings Gemini</span>
        }
      </div>
      <div class="stack">
        @for (item of r.resultats; track item.id) {
          <app-resultat-card [resultat]="item" (similaires)="similaires($event)" />
        } @empty {
          <div class="card empty"><span class="mi">search_off</span><p>Aucune publication pertinente trouvée.</p></div>
        }
      </div>
    }
  `,
  styles: [`
    .bigsearch { display: flex; align-items: center; gap: 10px; max-width: 760px; margin-bottom: 24px;
      background: var(--surface); border: 1px solid var(--border); border-radius: 14px; padding: 6px 6px 6px 16px;
      transition: border-color .15s, box-shadow .15s; box-shadow: var(--shadow);
      &:focus-within { border-color: var(--accent); box-shadow: var(--ring); }
      .mi { color: var(--accent3); }
      input { flex: 1; min-width: 0; background: none; border: none; outline: none; font-size: 15px; padding: 10px 0; &::placeholder { color: var(--faint); } }
    }
  `],
})
export class SmartSearchComponent {
  private ia = inject(IaService);
  private router = inject(Router);
  /** Requête passée dans l'URL (?q=...) depuis la barre de recherche du haut. */
  q = input<string>();

  query = '';
  loading = signal(false);
  error = signal('');
  res = signal<SearchResponse | null>(null);
  titre = signal('tri par pertinence');

  constructor() {
    effect(() => {
      const q = this.q();
      if (q) untracked(() => { this.query = q; this.executer(q); });
    });
  }

  lancer() {
    const q = this.query.trim();
    if (!q) return;
    // Met la requête dans l'URL (partageable, et relance via l'effet)
    if (q === this.q()) this.executer(q);
    else this.router.navigate([], { queryParams: { q } });
  }

  private executer(q: string) {
    this.loading.set(true);
    this.error.set('');
    this.ia.smartSearch(q).subscribe({
      next: r => { this.res.set(r); this.titre.set('tri par pertinence'); this.loading.set(false); },
      error: e => { this.error.set(messageErreur(e)); this.loading.set(false); },
    });
  }

  similaires(id: number) {
    this.loading.set(true);
    this.ia.recommander(id).subscribe({
      next: r => { this.res.set(r); this.titre.set('publications similaires'); this.loading.set(false); window.scrollTo?.({ top: 0 }); },
      error: e => { this.error.set(messageErreur(e)); this.loading.set(false); },
    });
  }
}
