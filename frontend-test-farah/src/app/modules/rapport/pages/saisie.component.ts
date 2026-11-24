import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { Fiche, MembreRef, Referentiels, STATUT_FICHE, StatutFiche } from '../../../core/models/rapport.model';
import { CATEGORIES, Categorie, Champ, categorie } from '../formulaires';
import { messageErreur } from '../../../shared/erreur';

/** Saisie générique des formulaires F3 à F17, avec le workflow brouillon → soumis → validé → verrouillé. */
@Component({
  selector: 'app-rapport-saisie',
  imports: [FormsModule, RouterLink, RouterLinkActive],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">{{ cat().titre }} <span class="chip chip-blue">{{ cat().f }}</span></h1>
        <p class="page-subtitle">Saisie des données du rapport d'activité · année {{ svc.annee() }}</p>
      </div>
      @if (peutCreer()) {
        <button class="btn btn-primary" (click)="nouveau()"><span class="mi sm">add</span> Nouvel enregistrement</button>
      }
    </div>

    <nav class="tabs-scroll" aria-label="Formulaires">
      @for (c of categoriesVisibles(); track c.code) {
        <a [routerLink]="['/rapport/saisie', c.code]" routerLinkActive="active" class="tab-link">
          <span class="mi sm">{{ c.icone }}</span>{{ c.titre }}
        </a>
      }
    </nav>

    @if (auth.hasRole(['CHERCHEUR']) && !saisieOuverte()) {
      <div class="alert alert-warn"><span class="mi sm">lock_clock</span>
        La saisie n'est pas ouverte pour {{ svc.annee() }} : vous pouvez consulter vos enregistrements, pas en ajouter.</div>
    }
    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (modele(); as m) {
      <div class="card card-body mb-16">
        <div class="row" style="justify-content:space-between;margin-bottom:14px">
          <div class="card-title">{{ editId() ? 'Modifier l\\'enregistrement' : 'Nouvel enregistrement' }} · {{ cat().f }}</div>
          @if (editFiche()?.motifRejet) {
            <span class="chip chip-red" title="Motif du rejet"><span class="mi sm">report</span> Rejeté : {{ editFiche()?.motifRejet }}</span>
          }
        </div>
        <form (ngSubmit)="enregistrer()" #f="ngForm" novalidate>
          <div class="form-grid">
            @for (c of champsVisibles(); track c.cle) {
              <div class="form-group" [class.span-2]="c.large || c.type === 'textarea' || c.type === 'membres'">
                @if (c.type === 'bool') {
                  <label class="check">
                    <input type="checkbox" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)" />
                    <span>{{ c.label }}</span>
                  </label>
                } @else {
                  <label class="form-label" [for]="'c-' + c.cle">{{ c.label }}@if (c.requis) { <span class="req"> *</span> }</label>
                }
                @switch (c.type) {
                  @case ('textarea') {
                    <textarea class="form-control" [id]="'c-' + c.cle" [name]="c.cle" rows="3" [(ngModel)]="m[c.cle]"
                              [attr.maxlength]="c.max ?? null" [disabled]="lectureSeule(c)"></textarea>
                  }
                  @case ('annee') {
                    <input class="form-control" type="number" min="1950" max="2100" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)" />
                  }
                  @case ('date') {
                    <input class="form-control" type="date" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)" />
                  }
                  @case ('email') {
                    <input class="form-control" type="email" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)" />
                  }
                  @case ('select') {
                    <select class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)">
                      <option [ngValue]="null">—</option>
                      @for (o of c.options; track o.v) { <option [ngValue]="o.v">{{ o.l }}</option> }
                    </select>
                  }
                  @case ('grade') {
                    <input class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" list="dl-grades" [disabled]="lectureSeule(c)" />
                    @if (m[c.cle]) { <div class="form-hint">Corps déduit : <strong>{{ corps(m[c.cle]) || 'aucune correspondance' }}</strong></div> }
                  }
                  @case ('etablissement') {
                    <input class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" list="dl-etab"
                           (change)="deduireUniversite(m)" [disabled]="lectureSeule(c)" />
                  }
                  @case ('universite') {
                    <input class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" list="dl-univ" [disabled]="lectureSeule(c)" />
                  }
                  @case ('cin') {
                    <div class="row" style="flex-wrap:nowrap">
                      <input class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" inputmode="numeric"
                             maxlength="8" pattern="\\d{8}" autocomplete="off"
                             [placeholder]="editFiche()?.cinRenseigne ? '•••••••• (inchangé)' : '8 chiffres'" [disabled]="lectureSeule(c)" />
                      @if (auth.estAdmin() && editFiche()?.cinRenseigne) {
                        <button type="button" class="btn btn-ghost btn-sm" (click)="voirCin()" title="Consultation journalisée"><span class="mi sm">visibility</span> Afficher</button>
                      }
                    </div>
                    @if (cinAffiche()) { <div class="form-hint">CIN actuel : <strong>{{ cinAffiche() }}</strong> (consultation inscrite au journal d'audit)</div> }
                  }
                  @case ('membre') {
                    <select class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)">
                      <option [ngValue]="null">—</option>
                      @for (r of membres(); track r.id) { <option [ngValue]="r.id">{{ r.nom }} {{ r.prenom }}</option> }
                    </select>
                  }
                  @case ('membres') {
                    <div class="chips mb-8">
                      @for (id of (m[c.cle] ?? []); track id) {
                        <span class="chip chip-cyan">{{ nom(id) }}
                          @if (!lectureSeule(c)) { <button type="button" class="chip-x" (click)="retirer(m, c.cle, id)" [attr.aria-label]="'Retirer ' + nom(id)">×</button> }
                        </span>
                      } @empty { <span class="small muted">Aucun membre sélectionné</span> }
                    </div>
                    @if (!lectureSeule(c)) {
                      <select class="form-control" [id]="'c-' + c.cle" [name]="c.cle + '_ajout'" [ngModel]="null" (ngModelChange)="ajouter(m, c.cle, $event)">
                        <option [ngValue]="null">Ajouter un membre…</option>
                        @for (r of membres(); track r.id) {
                          @if (!(m[c.cle] ?? []).includes(r.id)) { <option [ngValue]="r.id">{{ r.nom }} {{ r.prenom }}</option> }
                        }
                      </select>
                    }
                  }
                  @case ('bool') {}
                  @default {
                    <input class="form-control" [id]="'c-' + c.cle" [name]="c.cle" [(ngModel)]="m[c.cle]" [disabled]="lectureSeule(c)" />
                  }
                }
                @if (c.max && m[c.cle]) { <div class="form-hint">{{ (m[c.cle] ?? '').length }} / {{ c.max }} caractères</div> }
                @if (c.aide) { <div class="form-hint">{{ c.aide }}</div> }
                @if (erreurs()[c.cle]) { <div class="form-error">{{ erreurs()[c.cle] }}</div> }
              </div>
            }
          </div>
          <datalist id="dl-grades">@for (g of ref()?.grades ?? []; track g) { <option [value]="g"></option> }</datalist>
          <datalist id="dl-etab">@for (e of ref()?.etablissements ?? []; track e.nom) { <option [value]="e.nom"></option> }</datalist>
          <datalist id="dl-univ">@for (u of universites(); track u) { <option [value]="u"></option> }</datalist>
          <div class="row">
            <button type="submit" class="btn btn-primary" [disabled]="saving()">
              <span class="mi sm">save</span> {{ auth.estAdmin() ? 'Enregistrer' : 'Enregistrer le brouillon' }}
            </button>
            <button type="button" class="btn btn-ghost" (click)="fermer()">Annuler</button>
          </div>
        </form>
      </div>
    }

    <div class="card">
      <div class="card-header">
        <span class="card-title">{{ filtrees().length }} enregistrement(s)</span>
        <div class="row">
          <input class="form-control filter-input" type="search" placeholder="Filtrer…" [(ngModel)]="filtre" aria-label="Filtrer" />
          <select class="form-control filter-input" [(ngModel)]="filtreStatut" aria-label="Statut">
            <option value="">Tous les statuts</option>
            @for (s of statuts; track s) { <option [value]="s">{{ statutsInfo[s].label }}</option> }
          </select>
        </div>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr>@for (col of cat().colonnes; track col.label) { <th>{{ col.label }}</th> }<th>Statut</th><th></th></tr></thead>
          <tbody>
            @for (x of filtrees(); track x.id) {
              <tr>
                @for (col of cat().colonnes; track col.label; let i = $index) {
                  <td [class.cell-main]="i === 0">{{ col.valeur(x, noms) }}</td>
                }
                <td>
                  <span class="chip" [class]="statutsInfo[x.statut].chip">{{ statutsInfo[x.statut].label }}</span>
                  @if (x.motifRejet) { <div class="small" style="color:var(--red);margin-top:4px" [title]="x.motifRejet">Rejeté : {{ x.motifRejet }}</div> }
                  @if (cat().personne && !x['etranger'] && !x.cinRenseigne) { <div class="small" style="color:var(--gold);margin-top:4px">CIN manquant</div> }
                </td>
                <td>
                  <div class="actions">
                    @if (peutModifier(x)) {
                      <button class="btn btn-ghost btn-icon" title="Modifier" (click)="editer(x)"><span class="mi sm">edit</span></button>
                    } @else {
                      <button class="btn btn-ghost btn-icon" title="Consulter" (click)="editer(x)"><span class="mi sm">visibility</span></button>
                    }
                    @if (x.statut === 'BROUILLON' && (estAMoi(x) || auth.estAdmin())) {
                      <button class="btn btn-ghost btn-sm" (click)="soumettre(x)" title="Soumettre pour validation"><span class="mi sm">send</span> Soumettre</button>
                    }
                    @if (auth.estAdmin() && (x.statut === 'SOUMIS' || x.statut === 'BROUILLON')) {
                      <button class="btn btn-primary btn-sm" (click)="valider(x)"><span class="mi sm">task_alt</span> Valider</button>
                      <button class="btn btn-ghost btn-sm" (click)="rejetId.set(rejetId() === x.id ? null : x.id)"><span class="mi sm">undo</span> Rejeter</button>
                    }
                    @if (peutModifier(x)) {
                      <button class="btn btn-danger btn-icon" title="Supprimer" (click)="supprimer(x)"><span class="mi sm">delete</span></button>
                    }
                  </div>
                  @if (rejetId() === x.id) {
                    <form class="rejet" (ngSubmit)="rejeter(x)">
                      <label class="form-label" [for]="'motif-' + x.id">Motif du rejet (obligatoire, visible du déclarant)</label>
                      <textarea class="form-control" [id]="'motif-' + x.id" name="motif" rows="2" [(ngModel)]="motif" required></textarea>
                      <button class="btn btn-danger btn-sm" type="submit" [disabled]="!motif.trim()">Renvoyer en brouillon</button>
                    </form>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td [attr.colspan]="cat().colonnes.length + 2"><div class="empty"><span class="mi">{{ cat().icone }}</span><p>Aucun enregistrement.</p></div></td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .tabs-scroll { display:flex; gap:6px; overflow-x:auto; padding-bottom:8px; margin-bottom:16px; }
    .tab-link { display:inline-flex; align-items:center; gap:6px; padding:7px 12px; border-radius:999px; border:1px solid var(--border);
      font-size:13px; color:var(--text-soft); white-space:nowrap; }
    .tab-link.active { background:var(--accent); border-color:var(--accent); color:#fff; }
    .form-grid { display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:0 16px; }
    .span-2 { grid-column: span 2; }
    .req { color: var(--red); }
    .check { display:flex; gap:8px; align-items:center; font-size:13.5px; cursor:pointer; padding-top:22px; }
    .chip-x { background:none; border:0; color:inherit; cursor:pointer; font-size:15px; line-height:1; margin-left:4px; padding:0; }
    .filter-input { width:auto; min-width:150px; padding:7px 10px; }
    .cell-main { font-weight:500; max-width:420px; }
    .rejet { margin-top:10px; display:flex; flex-direction:column; gap:8px; align-items:flex-start; min-width:260px; }
    .mb-8 { margin-bottom:8px; }
    @media (max-width: 720px) { .form-grid { grid-template-columns: 1fr; } .span-2 { grid-column: span 1; } }
  `],
})
export class SaisieComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);

  /** Paramètre de route :categorie */
  categorie = input<string>('publications');
  cat = computed<Categorie>(() => categorie(this.categorie()) ?? CATEGORIES[3]);
  categoriesVisibles = computed(() => this.auth.estAdmin() ? CATEGORIES : CATEGORIES.filter(c => c.saisieChercheur));

  fiches = signal<Fiche[]>([]);
  ref = signal<Referentiels | null>(null);
  membres = computed<MembreRef[]>(() => this.ref()?.membres ?? []);
  universites = computed(() => [...new Set((this.ref()?.etablissements ?? []).map(e => e.universite).filter(Boolean))]);
  modele = signal<Record<string, any> | null>(null);
  editId = signal<number | null>(null);
  editFiche = signal<Fiche | null>(null);
  erreurs = signal<Record<string, string>>({});
  cinAffiche = signal('');
  rejetId = signal<number | null>(null);
  motif = '';
  saving = signal(false);
  message = signal('');
  error = signal('');
  filtre = '';
  filtreStatut = '';
  statuts: StatutFiche[] = ['BROUILLON', 'SOUMIS', 'VALIDE', 'VERROUILLE'];
  statutsInfo = STATUT_FICHE;

  saisieOuverte = computed(() => this.svc.campagne()?.statut === 'SAISIE_OUVERTE');
  peutCreer = computed(() => this.auth.estAdmin() || (this.auth.hasRole(['CHERCHEUR']) && this.cat().saisieChercheur && this.saisieOuverte()));

  constructor() {
    this.svc.referentiels().subscribe({ next: r => this.ref.set(r) });
    if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe();
    effect(() => {
      this.categorie(); this.svc.annee();
      untracked(() => { this.fermer(); this.message.set(''); this.error.set(''); this.charger(); });
    });
  }

  charger() {
    const c = this.cat();
    this.svc.lister(c.code, c.anneeCle ? this.svc.annee() : null).subscribe({
      next: l => this.fiches.set(l),
      error: e => { this.fiches.set([]); this.error.set(messageErreur(e)); },
    });
  }

  filtrees() {
    const q = this.filtre.trim().toLowerCase();
    return this.fiches().filter(x => (!this.filtreStatut || x.statut === this.filtreStatut)
      && (!q || this.cat().colonnes.some(col => String(col.valeur(x, this.noms)).toLowerCase().includes(q))));
  }

  champsVisibles() {
    const m = this.modele() ?? {};
    return this.cat().champs.filter(c => !c.si || c.si(m));
  }

  noms = (ids: number[]) => (ids ?? []).map(id => this.nom(id)).join(', ');
  nom(id: number) { const r = this.membres().find(x => x.id === id); return r ? `${r.nom} ${r.prenom}` : `#${id}`; }
  corps(grade: string) {
    const norm = (s: string) => (s ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
    const t = this.ref()?.corpsParGrade ?? {};
    const k = Object.keys(t).find(g => norm(g) === norm(grade));
    return k ? 'corps ' + t[k] : '';
  }

  estAMoi(x: Fiche) { return x.creeParId === this.auth.currentUser()?.id; }
  peutModifier(x: Fiche) {
    if (x.statut === 'VERROUILLE') return false;
    if (this.auth.estAdmin()) return true;
    return this.estAMoi(x) && x.statut === 'BROUILLON' && this.saisieOuverte();
  }
  lectureSeule(c: Champ) {
    const x = this.editFiche();
    if (c.adminSeul && !this.auth.estAdmin()) return true;
    return !!x && !this.peutModifier(x);
  }

  nouveau() {
    const c = this.cat();
    const m: Record<string, any> = { ...(c.defauts ?? {}) };
    if (c.anneeCle) m[c.anneeCle] = this.svc.annee();
    c.champs.filter(ch => ch.type === 'membres').forEach(ch => m[ch.cle] = []);
    if (!this.auth.estAdmin() && this.auth.currentUser()?.chercheurId) {
      const moi = this.auth.currentUser()!.chercheurId!;
      const lien = c.champs.find(ch => ch.type === 'membres' && ch.requis);
      if (lien) m[lien.cle] = [moi];
    }
    this.editId.set(null); this.editFiche.set(null); this.cinAffiche.set(''); this.erreurs.set({});
    this.modele.set(m);
    this.scrollHaut();
  }

  editer(x: Fiche) {
    const m: Record<string, any> = { ...x };
    delete m['cin'];
    this.cat().champs.filter(ch => ch.type === 'membres').forEach(ch => m[ch.cle] = [...(x[ch.cle] ?? [])]);
    this.editId.set(x.id); this.editFiche.set(x); this.cinAffiche.set(''); this.erreurs.set({});
    this.modele.set(m);
    this.scrollHaut();
  }

  fermer() { this.modele.set(null); this.editId.set(null); this.editFiche.set(null); this.cinAffiche.set(''); }

  deduireUniversite(m: Record<string, any>) {
    const e = (this.ref()?.etablissements ?? []).find(x => x.nom === m['etablissement']);
    if (e?.universite) m['universite'] = e.universite;
  }

  ajouter(m: Record<string, any>, cle: string, id: number | null) {
    if (id == null) return;
    m[cle] = [...(m[cle] ?? []), id];
    this.modele.set({ ...m });
  }
  retirer(m: Record<string, any>, cle: string, id: number) {
    m[cle] = (m[cle] ?? []).filter((x: number) => x !== id);
    this.modele.set({ ...m });
  }

  /** Contrôles côté client (le serveur refait les mêmes, §15.2). */
  private valider_(m: Record<string, any>): boolean {
    const e: Record<string, string> = {};
    for (const c of this.champsVisibles()) {
      const v = m[c.cle];
      if (c.type === 'cin') {
        if (v && !/^\d{8}$/.test(v)) e[c.cle] = 'Le N° CIN comporte exactement 8 chiffres.';
        continue;
      }
      if (c.type === 'annee' && v && (v < 1950 || v > new Date().getFullYear() + 5)) e[c.cle] = 'Année non plausible.';
      if (c.type === 'email' && v && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v)) e[c.cle] = 'Adresse e-mail invalide.';
      if (c.max && v && String(v).length > c.max) e[c.cle] = `${c.max} caractères maximum.`;
    }
    this.erreurs.set(e);
    return Object.keys(e).length === 0;
  }

  enregistrer() {
    const m = this.modele();
    if (!m || !this.valider_(m)) return;
    const corps: Record<string, any> = {};
    for (const c of this.cat().champs) if (c.cle in m) corps[c.cle] = m[c.cle] === '' ? null : m[c.cle];
    if (!corps['cin']) delete corps['cin'];
    if (this.cat().personne && corps['etranger']) corps['effacerCin'] = true;
    if (!this.auth.estAdmin()) delete corps['estImpactee'];
    this.saving.set(true); this.error.set(''); this.message.set('');
    const id = this.editId();
    (id ? this.svc.modifier(this.cat().code, id, corps) : this.svc.creer(this.cat().code, corps)).subscribe({
      next: r => { this.saving.set(false); this.message.set(r.message); this.fermer(); this.charger(); },
      error: e => { this.saving.set(false); this.error.set(messageErreur(e)); },
    });
  }

  voirCin() {
    const x = this.editFiche();
    if (!x) return;
    this.svc.cin(this.cat().code, x.id).subscribe({ next: r => this.cinAffiche.set(r.cin || '(vide)'), error: e => this.error.set(messageErreur(e)) });
  }

  private action(obs: ReturnType<RapportService['soumettre']>) {
    this.error.set(''); this.message.set('');
    obs.subscribe({ next: r => { this.message.set(r.message); this.rejetId.set(null); this.motif = ''; this.charger(); }, error: e => this.error.set(messageErreur(e)) });
  }
  soumettre(x: Fiche) { this.action(this.svc.soumettre(this.cat().code, x.id)); }
  valider(x: Fiche) { this.action(this.svc.valider(this.cat().code, x.id)); }
  rejeter(x: Fiche) { if (this.motif.trim()) this.action(this.svc.rejeter(this.cat().code, x.id, this.motif.trim())); }

  supprimer(x: Fiche) {
    if (!confirm('Supprimer définitivement cet enregistrement ?')) return;
    this.svc.supprimer(this.cat().code, x.id).subscribe({ next: () => { this.message.set('Enregistrement supprimé.'); this.charger(); }, error: e => this.error.set(messageErreur(e)) });
  }

  private scrollHaut() { if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' }); }
}
