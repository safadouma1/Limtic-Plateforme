import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RapportService } from '../../../core/services/rapport.service';
import { Controle } from '../../../core/models/rapport.model';
import { lienControle } from '../formulaires';
import { messageErreur } from '../../../shared/erreur';

/** Écran des contrôles avant génération (§15.3) : bloquants puis avertissements. */
@Component({
  selector: 'app-rapport-controles',
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Contrôles avant génération</h1>
        <p class="page-subtitle">Année {{ svc.annee() }} · la version finale est refusée tant qu'un contrôle bloquant reste actif</p>
      </div>
      <button class="btn btn-ghost" (click)="charger()"><span class="mi sm">refresh</span> Relancer</button>
    </div>
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (controles(); as l) {
      @if (bloquants().length === 0) {
        <div class="alert alert-success"><span class="mi sm">verified</span> Aucun contrôle bloquant : la campagne peut être clôturée et la version finale générée.</div>
      }
      @for (groupe of [{ t: 'Bloquants', l: bloquants(), c: 'bloq' }, { t: 'Avertissements', l: avertissements(), c: 'avert' }]; track groupe.t) {
        <h2 class="section-title" style="margin-top:8px">{{ groupe.t }} ({{ groupe.l.length }})</h2>
        @for (c of groupe.l; track c.code) {
          <details class="card controle" [class]="groupe.c" [open]="c.elements.length <= 8">
            <summary>
              <span class="mi">{{ groupe.c === 'bloq' ? 'block' : 'warning' }}</span>
              <span class="msg">{{ c.message }}</span>
              @if (c.elements.length) { <span class="chip" [class.chip-red]="groupe.c === 'bloq'" [class.chip-gold]="groupe.c !== 'bloq'">{{ c.elements.length }}</span> }
              <a class="btn btn-ghost btn-sm" [routerLink]="lien(c.categorie)" (click)="$event.stopPropagation()">Corriger <span class="mi sm">arrow_forward</span></a>
            </summary>
            @if (c.elements.length) {
              <ul>@for (e of c.elements; track $index) { <li>{{ e }}</li> }</ul>
            }
          </details>
        } @empty { <p class="muted small mb-16">Aucun.</p> }
      }
    } @else if (!error()) {
      <div class="skeleton" style="height:200px;border-radius:14px"></div>
    }
  `,
  styles: [`
    .controle { margin-bottom:10px; padding:0; }
    .controle summary { display:flex; align-items:center; gap:10px; padding:14px 16px; cursor:pointer; list-style:none; }
    .controle summary::-webkit-details-marker { display:none; }
    .controle .msg { flex:1; font-weight:500; font-size:14px; }
    .controle.bloq summary > .mi { color:var(--red); }
    .controle.avert summary > .mi { color:var(--gold); }
    .controle ul { margin:0 16px 14px 46px; padding:0; font-size:13px; color:var(--text-soft); display:flex; flex-direction:column; gap:4px; }
  `],
})
export class ControlesComponent {
  svc = inject(RapportService);
  controles = signal<Controle[] | null>(null);
  error = signal('');
  bloquants = computed(() => (this.controles() ?? []).filter(c => c.niveau === 'BLOQUANT'));
  avertissements = computed(() => (this.controles() ?? []).filter(c => c.niveau !== 'BLOQUANT'));
  lien = lienControle;

  constructor() {
    if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe();
    effect(() => { this.svc.annee(); untracked(() => this.charger()); });
  }

  charger() {
    this.controles.set(null);
    this.svc.controles(this.svc.annee()).subscribe({ next: c => { this.error.set(''); this.controles.set(c); }, error: e => this.error.set(messageErreur(e)) });
  }
}
