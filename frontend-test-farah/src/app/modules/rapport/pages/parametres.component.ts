import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RapportService } from '../../../core/services/rapport.service';
import { CompteRendu, Regle } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

const TABLES: { code: Regle['tableCode']; titre: string; aide: string; cle: string; valeur: string }[] = [
  { code: 'GRADE_CORPS', titre: 'Grade → corps', cle: 'Grade', valeur: 'Corps (A ou B)', aide: 'Répartit les enseignants-chercheurs entre les tableaux I-1 (corps A) et I-2 (corps B).' },
  { code: 'INDEXATION_IMPACTEE', titre: 'Indexation → revue impactée', cle: 'Indexation', valeur: 'Impactée (oui / non)', aide: 'Propose la qualification « impactée » des publications (II-1). L\'administrateur peut la corriger au cas par cas.' },
  { code: 'CONVENTION_RUBRIQUE', titre: 'Type de convention → rubrique', cle: 'Type', valeur: 'Rubrique (III-2 / III-3)', aide: 'Range chaque convention en III-2 (socio-économique) ou III-3 (coopération).' },
];

const IMPORTABLES = ['membres', 'doctorants', 'masteriens', 'publications', 'ouvrages', 'chapitres', 'theses', 'masters', 'evenements', 'conventions', 'projets'];

/** Paramètres du module : tables de correspondance, gabarit, import CSV/Excel. */
@Component({
  selector: 'app-rapport-parametres',
  imports: [FormsModule],
  template: `
    <div class="page-head"><div><h1 class="page-title">Paramètres du rapport</h1>
      <p class="page-subtitle">Tables de correspondance, gabarit et import de données</p></div></div>
    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <h2 class="section-title">Tables de correspondance</h2>
    <div class="tables">
      @for (t of tables; track t.code) {
        <div class="card">
          <div class="card-header"><span class="card-title">{{ t.titre }}</span></div>
          <div class="card-body">
            <p class="small muted" style="margin-top:0">{{ t.aide }}</p>
            <table class="data-table">
              <thead><tr><th>{{ t.cle }}</th><th>{{ t.valeur }}</th><th></th></tr></thead>
              <tbody>
                @for (l of edition()[t.code]; track $index; let i = $index) {
                  <tr>
                    <td><input class="form-control" [(ngModel)]="l.cle" [attr.aria-label]="t.cle" /></td>
                    <td><input class="form-control" [(ngModel)]="l.valeur" [attr.aria-label]="t.valeur" /></td>
                    <td><button class="btn btn-ghost btn-sm" type="button" (click)="retirer(t.code, i)" aria-label="Retirer"><span class="mi sm">close</span></button></td>
                  </tr>
                }
              </tbody>
            </table>
            <div class="row" style="margin-top:10px">
              <button class="btn btn-ghost btn-sm" type="button" (click)="ajouter(t.code)"><span class="mi sm">add</span> Ligne</button>
              <button class="btn btn-primary btn-sm" type="button" (click)="enregistrerTable(t.code)"><span class="mi sm">save</span> Enregistrer</button>
            </div>
          </div>
        </div>
      }
    </div>

    <h2 class="section-title" style="margin-top:24px">Import de données (CSV ou Excel)</h2>
    <div class="card card-body mb-16">
      <div class="row" style="align-items:flex-end;flex-wrap:wrap">
        <div class="form-group" style="margin:0"><label class="form-label" for="cat">Catégorie</label>
          <select id="cat" class="form-control" [(ngModel)]="cat">@for (c of importables; track c) { <option [value]="c">{{ c }}</option> }</select></div>
        <div class="form-group" style="margin:0"><label class="form-label" for="fic">Fichier (.csv, .xlsx)</label>
          <input id="fic" class="form-control" type="file" accept=".csv,.xlsx,.xls" (change)="fichier = $any($event.target).files?.[0] ?? null" /></div>
        <button class="btn btn-primary" type="button" (click)="importer()" [disabled]="!fichier || busy()"><span class="mi sm">upload</span> Importer</button>
        <button class="btn btn-ghost" type="button" (click)="jeuEssai()" [disabled]="busy()"><span class="mi sm">dataset</span> Recharger le jeu d'essai 2025</button>
      </div>
      <p class="form-hint">En-têtes attendus : ceux des fichiers du jeu d'essai (séparateur « ; » ou « , »). Les lignes importées sont validées ; les doublons sont ignorés.</p>
      @for (r of comptesRendus(); track r.categorie) {
        <details class="cr">
          <summary><strong>{{ r.categorie }}</strong> : {{ r.importees }} importée(s), {{ r.rejetees }} rejetée(s), {{ r.doublons }} doublon(s)</summary>
          <ul>@for (l of r.lignes; track $index) { @if (l.statut !== 'IMPORTEE') { <li>Ligne {{ l.ligne }} · {{ l.statut }} · {{ l.message }}</li> } }</ul>
        </details>
      }
    </div>

    <h2 class="section-title">Gabarit du rapport</h2>
    <div class="card card-body">
      <p class="small muted" style="margin-top:0">Structure JSON des chapitres et tableaux. Les numéros (I-1, II-3…) sont recalculés à partir de l'ordre. Versions : {{ versionsGabarit() }}</p>
      <textarea class="form-control mono" rows="14" [(ngModel)]="gabaritJson" aria-label="Gabarit JSON"></textarea>
      <button class="btn btn-primary" type="button" style="margin-top:10px" (click)="enregistrerGabarit()"><span class="mi sm">save</span> Enregistrer comme nouvelle version</button>
    </div>
  `,
  styles: [`.tables { display:grid; grid-template-columns:repeat(auto-fit,minmax(320px,1fr)); gap:14px; }
    .tables .form-control { padding:5px 8px; font-size:12.5px; }
    .mono { font-family:ui-monospace,Menlo,Consolas,monospace; font-size:12px; }
    .cr { margin-top:10px; font-size:13px; } .cr ul { color:var(--text-soft); }`],
})
export class ParametresComponent {
  private svc = inject(RapportService);
  tables = TABLES;
  importables = IMPORTABLES;
  edition = signal<Record<string, { cle: string; valeur: string }[]>>({});
  gabaritJson = '';
  gabaritVersions = signal<{ version: string; actif: boolean }[]>([]);
  versionsGabarit = computed(() => this.gabaritVersions().map(v => v.version + (v.actif ? ' (active)' : '')).join(', '));
  comptesRendus = signal<CompteRendu[]>([]);
  cat = 'publications';
  fichier: File | null = null;
  busy = signal(false); message = signal(''); error = signal('');

  constructor() {
    this.svc.correspondances().subscribe({ next: r => this.remplir(r), error: e => this.error.set(messageErreur(e)) });
    this.svc.gabarit().subscribe({ next: g => this.gabarit(g) });
  }

  private remplir(r: Regle[]) {
    const e: Record<string, { cle: string; valeur: string }[]> = {};
    for (const t of TABLES) e[t.code] = r.filter(x => x.tableCode === t.code).map(x => ({ cle: x.cle, valeur: x.valeur }));
    this.edition.set(e);
  }
  private gabarit(g: { actif: string; versions: { version: string; actif: boolean }[] }) {
    try { this.gabaritJson = JSON.stringify(JSON.parse(g.actif), null, 2); } catch { this.gabaritJson = g.actif; }
    this.gabaritVersions.set(g.versions);
  }
  private ok(m: string) { this.busy.set(false); this.error.set(''); this.message.set(m); }
  private ko(e: unknown) { this.busy.set(false); this.message.set(''); this.error.set(messageErreur(e)); }

  ajouter(t: string) { this.edition.update(e => ({ ...e, [t]: [...e[t], { cle: '', valeur: '' }] })); }
  retirer(t: string, i: number) { this.edition.update(e => ({ ...e, [t]: e[t].filter((_, j) => j !== i) })); }
  enregistrerTable(t: string) {
    const v: Record<string, string> = {};
    for (const l of this.edition()[t]) if (l.cle.trim()) v[l.cle.trim()] = l.valeur.trim();
    this.svc.enregistrerCorrespondances(t, v).subscribe({ next: r => { this.remplir(r); this.ok('Table enregistrée.'); }, error: e => this.ko(e) });
  }
  enregistrerGabarit() {
    try { JSON.parse(this.gabaritJson); } catch { this.message.set(''); this.error.set('JSON invalide : vérifiez la syntaxe.'); return; }
    this.svc.enregistrerGabarit(this.gabaritJson).subscribe({ next: g => { this.gabarit(g); this.ok('Nouvelle version du gabarit enregistrée.'); }, error: e => this.ko(e) });
  }
  importer() {
    if (!this.fichier) return;
    this.busy.set(true);
    this.svc.importer(this.cat, this.fichier).subscribe({ next: r => { this.comptesRendus.set([r]); this.ok('Import terminé.'); }, error: e => this.ko(e) });
  }
  jeuEssai() {
    this.busy.set(true);
    this.svc.jeuEssai().subscribe({ next: r => { this.comptesRendus.set(r); this.ok('Jeu d\'essai 2025 rechargé.'); this.svc.chargerCampagnes().subscribe(); }, error: e => this.ko(e) });
  }
}
