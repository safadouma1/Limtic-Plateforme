import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RapportService } from '../../../core/services/rapport.service';
import { RapportData } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** Aperçu web complet du rapport, avec les avertissements de cohérence signalés en marge (§17.2). */
@Component({
  selector: 'app-rapport-apercu',
  imports: [FormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Aperçu du rapport {{ svc.annee() }}</h1>
        <p class="page-subtitle">Rendu calculé en direct · les numéros de CIN sont masqués à l'écran</p>
      </div>
      <label class="row small" style="gap:8px;cursor:pointer">
        <input type="checkbox" [(ngModel)]="soumis" (ngModelChange)="charger()" /> Inclure les enregistrements soumis
      </label>
    </div>
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (d(); as d) {
      @if (d.controles.length) {
        <div class="alert" [class.alert-error]="nbBloquants(d) > 0" [class.alert-warn]="nbBloquants(d) === 0">
          <span class="mi sm">{{ nbBloquants(d) ? 'block' : 'warning' }}</span>
          {{ nbBloquants(d) }} contrôle(s) bloquant(s) et {{ d.controles.length - nbBloquants(d) }} avertissement(s) : ils figureront en annexe des versions provisoires.
        </div>
      }
      <article class="doc">
        <header class="doc-garde">
          @for (l of d.entete; track l) { <div class="ent">{{ l }}</div> }
          <h2>{{ d.titre }}</h2>
          <table class="doc-table ident">
            <tbody>
              <tr><th>Université / Centre / EPS</th><td>{{ d.identite['universite'] }}</td></tr>
              <tr><th>Établissement</th><td>{{ d.identite['etablissement'] }}</td></tr>
              <tr><th>Dénomination du LR/UR</th><td>{{ d.identite['denomination'] }}</td></tr>
              <tr><th>Code de la structure</th><td>{{ d.identite['code'] }}</td></tr>
              <tr><th>Chef du LR/UR</th><td>{{ d.identite['chef'] }} — {{ d.identite['grade'] }}</td></tr>
              <tr><th>Téléphone / Fax</th><td>{{ d.identite['telephone'] }} / {{ d.identite['fax'] }}</td></tr>
              <tr><th>E-mail</th><td>{{ d.identite['email'] }}</td></tr>
              <tr><th>Site web</th><td>{{ d.identite['siteWeb'] }}</td></tr>
            </tbody>
          </table>
        </header>

        <section>
          <h3>Résumé des activités du LR/UR</h3>
          <h4>Historique</h4><p>{{ d.resume.historique }}</p>
          <h4>Thématiques essentielles de recherche</h4>
          <ul>@for (t of d.resume.thematiques; track t) { <li>{{ t }}</li> }</ul>
          <h4>Bilan global des activités</h4><p>{{ d.resume.bilan }}</p>
        </section>

        <section>
          <h3>Récapitulatif du rapport {{ d.annee }}</h3>
          <table class="doc-table">
            <thead><tr><th>Rubrique</th><th class="num">{{ d.annee }}</th>@if (d.anneePrecedenteDisponible) { <th class="num">{{ d.annee - 1 }}</th> }</tr></thead>
            <tbody>
              @for (g of d.groupes; track g.numero) {
                <tr class="grp"><td [attr.colspan]="d.anneePrecedenteDisponible ? 3 : 2">{{ g.numero }} – {{ g.titre }}</td></tr>
                @for (s of g.sections; track s.code) {
                  <tr><td>{{ s.numero }} – {{ s.libelleRecap }}</td><td class="num"><strong>{{ s.valeur || '—' }}</strong></td>
                    @if (d.anneePrecedenteDisponible) { <td class="num">{{ s.valeurN1 || '—' }}</td> }</tr>
                }
              }
            </tbody>
          </table>
        </section>

        @for (g of d.groupes; track g.numero) {
          <section>
            <h3>{{ g.numero }} – {{ g.titre }}</h3>
            @for (s of g.sections; track s.code) {
              <h4>{{ s.numero }} – {{ s.titre }}</h4>
              <div class="table-wrap">
                <table class="doc-table">
                  <thead><tr>@for (c of s.colonnes; track $index) { <th>{{ c }}</th> }</tr></thead>
                  <tbody>
                    @for (l of s.lignes; track $index) { <tr>@for (v of l; track $index) { <td>{{ v }}</td> }</tr> }
                    @empty { <tr><td [attr.colspan]="s.colonnes.length" class="muted">Néant</td></tr> }
                    @if (s.ligneTotal) { <tr class="grp">@for (v of s.ligneTotal; track $index) { <td>{{ v }}</td> }</tr> }
                  </tbody>
                </table>
              </div>
              @if (!s.ligneTotal) { <p class="total">{{ s.libelleTotal }} : {{ s.valeur }}</p> }
            }
          </section>
        }
      </article>
    } @else if (!error()) {
      <div class="skeleton" style="height:400px;border-radius:14px"></div>
    }
  `,
  styles: [`
    .doc { background:#fbfcfe; color:#1c2333; border-radius:14px; padding:40px 44px; font-size:13px; line-height:1.55; }
    .doc h2 { font-size:24px; text-align:center; color:#1f3a5f; margin:28px 0 20px; }
    .doc h3 { font-size:16px; color:#1f3a5f; margin:28px 0 10px; border-bottom:2px solid #dbe4f0; padding-bottom:4px; }
    .doc h4 { font-size:13.5px; color:#1f3a5f; margin:16px 0 6px; }
    .doc p, .doc li { color:#2a3344; }
    .ent { text-align:center; font-weight:600; font-size:12px; letter-spacing:.3px; }
    .doc-table { width:100%; border-collapse:collapse; margin:6px 0; font-size:12px; }
    .doc-table th { background:#1f3a5f; color:#fff; text-align:left; padding:6px 8px; font-weight:600; }
    .doc-table td { border:1px solid #d5dce8; padding:5px 8px; vertical-align:top; }
    .ident th { background:#e8eef5; color:#1f3a5f; width:38%; border:1px solid #d5dce8; }
    .grp td { background:#e8eef5; font-weight:600; }
    .num { text-align:right; }
    .total { font-weight:600; margin:4px 0 14px; }
    @media (max-width: 720px) { .doc { padding:20px 16px; } }
  `],
})
export class ApercuComponent {
  svc = inject(RapportService);
  d = signal<RapportData | null>(null);
  error = signal('');
  soumis = false;

  constructor() {
    if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe();
    effect(() => { this.svc.annee(); untracked(() => this.charger()); });
  }

  charger() {
    this.d.set(null);
    this.svc.apercu(this.svc.annee(), this.soumis).subscribe({ next: d => { this.error.set(''); this.d.set(d); }, error: e => this.error.set(messageErreur(e)) });
  }

  nbBloquants(d: RapportData) { return d.controles.filter(c => c.niveau === 'BLOQUANT').length; }
}
