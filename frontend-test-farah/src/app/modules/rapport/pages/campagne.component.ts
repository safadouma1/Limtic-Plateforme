import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { Campagne, Completude, Recapitulatif, STATUT_CAMPAGNE, STATUT_FICHE, StatutCampagne, StatutFiche } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** Accueil du module : campagne de l'année, récapitulatif calculé, complétude, actions du directeur. */
@Component({
  selector: 'app-rapport-campagne',
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Rapport d'activité</h1>
        <p class="page-subtitle">Campagne annuelle, récapitulatif calculé et suivi de la saisie</p>
      </div>
      <div class="row">
        <label class="sr-only" for="annee">Année de référence</label>
        <select id="annee" class="form-control" style="width:auto" [ngModel]="svc.annee()" (ngModelChange)="svc.choisirAnnee($event)">
          @for (c of svc.campagnes(); track c.id) { <option [ngValue]="c.annee">Campagne {{ c.annee }}</option> }
        </select>
        @if (auth.estAdmin()) {
          <a routerLink="/rapport/rapports" class="btn btn-primary"><span class="mi sm">download</span> Télécharger le rapport</a>
        }
      </div>
    </div>

    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (camp(); as c) {
      <div class="card card-body mb-16">
        <div class="steps" role="list" aria-label="États de la campagne">
          @for (s of etats; track s; let i = $index) {
            <div class="step" role="listitem" [class.done]="rang(c.statut) > i" [class.current]="c.statut === s">
              <span class="step-dot">{{ rang(c.statut) > i ? '✓' : i + 1 }}</span>
              <span>{{ libelles[s] }}</span>
            </div>
          }
        </div>
        <div class="row small muted" style="margin-top:12px">
          @if (c.dateDebutSaisie) { <span>Saisie du {{ c.dateDebutSaisie | date:'d MMM y' }}</span> }
          @if (c.dateEcheance) { <span>· échéance {{ c.dateEcheance | date:'d MMM y' }}</span> }
          <span>· budget arrêté au {{ c.dateArreteBudget | date:'d MMMM y' }}</span>
          @if (c.clotureLe) { <span>· clôturée le {{ c.clotureLe | date:'d/MM/y HH:mm' }} par {{ c.cloturePar }}</span> }
        </div>
        @if (auth.estDirecteur()) {
          <div class="row" style="margin-top:16px">
            @switch (c.statut) {
              @case ('PREPARATION') { <button class="btn btn-primary" (click)="statut('SAISIE_OUVERTE')"><span class="mi sm">lock_open</span> Ouvrir la saisie</button> }
              @case ('SAISIE_OUVERTE') {
                <button class="btn btn-ghost" (click)="statut('REVUE')"><span class="mi sm">rate_review</span> Passer en revue</button>
                <button class="btn btn-primary" (click)="statut('CLOTUREE')"><span class="mi sm">lock</span> Clôturer et verrouiller</button>
              }
              @case ('REVUE') {
                <button class="btn btn-ghost" (click)="statut('SAISIE_OUVERTE')"><span class="mi sm">lock_open</span> Rouvrir la saisie</button>
                <button class="btn btn-primary" (click)="statut('CLOTUREE')"><span class="mi sm">lock</span> Clôturer et verrouiller</button>
              }
              @case ('CLOTUREE') { <button class="btn btn-danger" (click)="statut('REVUE')"><span class="mi sm">lock_reset</span> Rouvrir la campagne</button> }
            }
          </div>
          <p class="form-hint">La clôture exige qu'aucun contrôle bloquant ne reste actif ; elle verrouille les enregistrements de l'année et enregistre une copie immuable des données.</p>
        }
      </div>
    } @else if (chargee()) {
      <div class="alert alert-info"><span class="mi sm">info</span> Aucune campagne. @if (auth.estDirecteur()) { Ouvrez-en une ci-dessous. }</div>
    }

    @if (recap(); as r) {
      <div class="stats-grid">
        <a routerLink="/rapport/controles" class="stat-card card-link">
          <div class="stat-icon" [class.si-gold]="r.nbBloquants === 0" style="background:rgba(242,107,107,.13);color:var(--red)"><span class="mi">block</span></div>
          <div class="stat-value">{{ r.nbBloquants }}</div><div class="stat-label">Contrôles bloquants</div>
        </a>
        <a routerLink="/rapport/controles" class="stat-card card-link">
          <div class="stat-icon si-gold"><span class="mi">warning</span></div>
          <div class="stat-value">{{ r.nbAvertissements }}</div><div class="stat-label">Avertissements</div>
        </a>
        <a routerLink="/rapport/saisie/publications" class="stat-card card-link">
          <div class="stat-icon si-cyan"><span class="mi">article</span></div>
          <div class="stat-value">{{ valeur('PUBLICATIONS') }}</div><div class="stat-label">Publications impactées</div>
        </a>
        <a routerLink="/rapport/saisie/membres" class="stat-card card-link">
          <div class="stat-icon si-blue"><span class="mi">groups</span></div>
          <div class="stat-value">{{ +valeur('CORPS_A') + +valeur('CORPS_B') }}</div><div class="stat-label">Enseignants-chercheurs</div>
        </a>
      </div>
    }

    <div class="grid-main-side">
      <div class="stack">
        @if (recap(); as r) {
          <div class="card">
            <div class="card-header">
              <span class="card-title">Récapitulatif {{ r.annee }}</span>
              <span class="small muted">Calculé à partir des enregistrements validés · aucun total saisi à la main</span>
            </div>
            <div class="table-wrap">
              <table class="data-table recap">
                <thead><tr><th>Rubrique</th><th class="num">{{ r.annee }}</th>@if (r.anneePrecedenteDisponible) { <th class="num">{{ r.annee - 1 }}</th> }</tr></thead>
                <tbody>
                  @for (g of r.groupes; track g.numero) {
                    <tr class="groupe"><td [attr.colspan]="r.anneePrecedenteDisponible ? 3 : 2">{{ g.numero }} – {{ g.titre }}</td></tr>
                    @for (l of g.rubriques; track l.code) {
                      <tr>
                        <td><span class="muted">{{ l.numero }}</span> {{ l.libelle }}</td>
                        <td class="num"><strong>{{ l.valeur || '—' }}</strong></td>
                        @if (r.anneePrecedenteDisponible) { <td class="num muted">{{ l.valeurN1 || '—' }}</td> }
                      </tr>
                    }
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        @if (completude(); as cp) {
          <div class="card">
            <div class="card-header"><span class="card-title">Complétude de la saisie</span></div>
            <div class="table-wrap">
              <table class="data-table">
                <thead><tr><th>Formulaire</th>@for (s of statutsFiche; track s) { <th class="num">{{ statutsInfo[s].label }}</th> }</tr></thead>
                <tbody>
                  @for (f of cp.formulaires; track f.code) {
                    <tr>
                      <td><a [routerLink]="['/rapport/saisie', f.code]"><span class="muted">{{ f.formulaire }}</span> {{ f.libelle }}</a></td>
                      @for (s of statutsFiche; track s) {
                        <td class="num"><span [class.muted]="!f.statuts[s]" [style.color]="s === 'SOUMIS' && f.statuts[s] ? 'var(--accent)' : null">{{ f.statuts[s] }}</span></td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      </div>

      <div class="stack">
        @if (auth.estDirecteur() && camp(); as c) {
          <div class="card card-body">
            <div class="card-title mb-16">Paramètres de calcul {{ c.annee }}</div>
            <form (ngSubmit)="enregistrerParametres()">
              <div class="form-group">
                <label class="form-label" for="ds">Début de saisie</label>
                <input id="ds" class="form-control" type="date" name="ds" [(ngModel)]="params.dateDebutSaisie" />
              </div>
              <div class="form-group">
                <label class="form-label" for="de">Échéance de saisie</label>
                <input id="de" class="form-control" type="date" name="de" [(ngModel)]="params.dateEcheance" />
              </div>
              <div class="form-group">
                <label class="form-label" for="db">Date d'arrêté du budget</label>
                <input id="db" class="form-control" type="date" name="db" [(ngModel)]="params.dateArreteBudget" />
              </div>
              <label class="check"><input type="checkbox" name="p1" [(ngModel)]="params.inclureDoctorantsSoutenus" /> Compter en I-3 les doctorants soutenus dans l'année</label>
              <label class="check"><input type="checkbox" name="p2" [(ngModel)]="params.bloquerNonValides" /> Enregistrements « soumis » ou « brouillon » : contrôle bloquant</label>
              <label class="check"><input type="checkbox" name="p3" [(ngModel)]="params.inclureNonImpacteesII3" /> Ajouter en II-3 les publications non impactées</label>
              <div class="form-group" style="margin-top:12px">
                <label class="form-label" for="r3">Règle de la rubrique III-3 (point à valider, §15.6)</label>
                <select id="r3" class="form-control" name="r3" [(ngModel)]="params.regleIII3">
                  <option value="CONVENTIONS_ET_PROJETS">Conventions internationales + tous les projets en cours</option>
                  <option value="CONVENTIONS_ET_PROJETS_INTERNATIONAUX">Conventions internationales + projets internationaux</option>
                  <option value="CONVENTIONS_SEULEMENT">Conventions internationales seulement</option>
                </select>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="c.statut === 'CLOTUREE'">Enregistrer</button>
            </form>
          </div>
        }

        @if (auth.estDirecteur()) {
          <div class="card card-body">
            <div class="card-title mb-16">Ouvrir une nouvelle campagne</div>
            <form (ngSubmit)="creer()">
              <div class="form-group">
                <label class="form-label" for="na">Année de référence</label>
                <input id="na" class="form-control" type="number" name="na" [(ngModel)]="nouvelle.annee" min="2000" max="2100" required />
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label" for="nd">Début de saisie</label>
                  <input id="nd" class="form-control" type="date" name="nd" [(ngModel)]="nouvelle.dateDebutSaisie" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="ne">Échéance</label>
                  <input id="ne" class="form-control" type="date" name="ne" [(ngModel)]="nouvelle.dateEcheance" />
                </div>
              </div>
              <p class="form-hint">Les 10 rubriques budgétaires sont créées et le résumé de l'année précédente est reconduit.</p>
              <button class="btn btn-ghost" type="submit"><span class="mi sm">add</span> Créer la campagne</button>
            </form>
          </div>
        }

        <div class="card card-body">
          <div class="card-title mb-16">Formulaires de l'année</div>
          <div class="stack" style="gap:6px">
            @if (auth.estAdmin()) {
              <a routerLink="/rapport/identite" class="lien"><span class="mi sm">apartment</span> F1 – Identité de la structure</a>
              <a routerLink="/rapport/resume" class="lien"><span class="mi sm">notes</span> F2 – Résumé des activités</a>
            }
            <a routerLink="/rapport/saisie/publications" class="lien"><span class="mi sm">edit_note</span> F3 à F17 – Saisie des données</a>
            @if (auth.estAdmin()) {
              <a routerLink="/rapport/budget" class="lien"><span class="mi sm">account_balance</span> F18 – Reliquat du budget</a>
              <a routerLink="/rapport/apercu" class="lien"><span class="mi sm">preview</span> Aperçu du rapport</a>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .steps { display:flex; gap:8px; flex-wrap:wrap; }
    .step { display:flex; align-items:center; gap:8px; padding:8px 14px; border-radius:999px; border:1px solid var(--border); color:var(--muted); font-size:13px; }
    .step-dot { width:22px; height:22px; border-radius:50%; display:grid; place-items:center; background:var(--surface3); font-size:12px; font-weight:600; }
    .step.done { color:var(--text-soft); } .step.done .step-dot { background:var(--green); color:#062; }
    .step.current { border-color:var(--accent); color:var(--text); } .step.current .step-dot { background:var(--accent); color:#fff; }
    .recap .groupe td { background:var(--surface2); font-weight:600; font-size:12.5px; text-transform:uppercase; letter-spacing:.3px; }
    .num { text-align:right; white-space:nowrap; }
    .check { display:flex; gap:8px; align-items:flex-start; font-size:13px; margin-bottom:8px; cursor:pointer; }
    .lien { display:flex; gap:8px; align-items:center; font-size:13.5px; padding:6px 0; color:var(--text-soft); }
    .lien:hover { color:var(--accent); }
    .sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0 0 0 0); }
  `],
})
export class CampagneComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);

  etats: StatutCampagne[] = ['PREPARATION', 'SAISIE_OUVERTE', 'REVUE', 'CLOTUREE'];
  libelles = STATUT_CAMPAGNE;
  statutsFiche: StatutFiche[] = ['BROUILLON', 'SOUMIS', 'VALIDE', 'VERROUILLE'];
  statutsInfo = STATUT_FICHE;

  camp = computed(() => this.svc.campagne());
  chargee = signal(false);
  recap = signal<Recapitulatif | null>(null);
  completude = signal<Completude | null>(null);
  message = signal('');
  error = signal('');
  params: Partial<Campagne> = {};
  nouvelle: Partial<Campagne> = { annee: new Date().getFullYear() + 1 };

  constructor() {
    this.svc.chargerCampagnes().subscribe({ next: () => this.chargee.set(true), error: e => { this.chargee.set(true); this.error.set(messageErreur(e)); } });
    effect(() => {
      const a = this.svc.annee();
      const c = this.camp();
      untracked(() => {
        if (c) this.params = { ...c };
        if (!this.auth.estAdmin()) return;
        this.svc.recapitulatif(a).subscribe({ next: r => this.recap.set(r), error: () => this.recap.set(null) });
        this.svc.completude(a).subscribe({ next: r => this.completude.set(r), error: () => this.completude.set(null) });
      });
    });
  }

  rang(s: StatutCampagne) { return this.etats.indexOf(s); }
  valeur(code: string) {
    for (const g of this.recap()?.groupes ?? []) for (const l of g.rubriques) if (l.code === code) return l.valeur || '0';
    return '0';
  }

  private apres(msg: string) {
    return {
      next: () => { this.message.set(msg); this.error.set(''); this.svc.chargerCampagnes().subscribe(); },
      error: (e: unknown) => { this.message.set(''); this.error.set(messageErreur(e)); },
    };
  }

  statut(s: StatutCampagne) {
    const a = this.svc.annee();
    const txt: Record<StatutCampagne, string> = {
      PREPARATION: '', SAISIE_OUVERTE: `Ouvrir la saisie de la campagne ${a} ?`, REVUE: `Passer la campagne ${a} en revue ?`,
      CLOTUREE: `Clôturer la campagne ${a} ? Les enregistrements de l'année seront verrouillés et une copie immuable sera enregistrée.`,
    };
    if (this.camp()?.statut === 'CLOTUREE' && !confirm(`Rouvrir la campagne ${a} ? Cette réouverture est inscrite au journal d'audit ; une nouvelle clôture produira une nouvelle version.`)) return;
    if (this.camp()?.statut !== 'CLOTUREE' && !confirm(txt[s])) return;
    this.svc.changerStatut(a, s).subscribe(this.apres(`Campagne ${a} : ${this.libelles[s]}.`));
  }

  enregistrerParametres() { this.svc.parametres(this.svc.annee(), this.params).subscribe(this.apres('Paramètres enregistrés.')); }

  creer() {
    this.svc.creerCampagne(this.nouvelle).subscribe({
      next: c => { this.message.set(`Campagne ${c.annee} créée.`); this.svc.choisirAnnee(c.annee); this.svc.chargerCampagnes().subscribe(); },
      error: e => this.error.set(messageErreur(e)),
    });
  }
}
