import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { FichierRapport, Telechargement, VersionRapport } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** Page « Rapports » (§17.4) : versions générées, téléchargements par lien signé, génération, historique. */
@Component({
  selector: 'app-rapport-rapports',
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Rapports {{ svc.annee() }}</h1>
        <p class="page-subtitle">Versions générées · Word modifiable, PDF, tableaux CSV et archive ZIP</p>
      </div>
    </div>
    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (auth.estDirecteur()) {
      <div class="card card-body mb-16">
        <div class="card-title mb-16">Générer un rapport</div>
        <form class="gen" (ngSubmit)="generer()">
          <fieldset>
            <legend class="form-label">Nature</legend>
            <label class="check"><input type="radio" name="nature" [value]="false" [(ngModel)]="finale" /> Provisoire (filigrane « PROVISOIRE – NON VALIDÉ »)</label>
            <label class="check"><input type="radio" name="nature" [value]="true" [(ngModel)]="finale" [disabled]="!cloturee()" /> Finale @if (!cloturee()) { <span class="muted">(campagne à clôturer)</span> }</label>
          </fieldset>
          <fieldset>
            <legend class="form-label">Options</legend>
            <label class="check"><input type="checkbox" name="cin" [(ngModel)]="avecCin" /> Inclure les numéros de CIN (version confidentielle)</label>
            <label class="check"><input type="checkbox" name="soumis" [(ngModel)]="inclureSoumis" [disabled]="finale" /> Inclure les enregistrements « soumis »</label>
          </fieldset>
          <button class="btn btn-primary" type="submit" [disabled]="generation()">
            @if (generation()) { <span class="mi sm spin">autorenew</span> Génération en cours… } @else { <span class="mi sm">picture_as_pdf</span> Générer }
          </button>
        </form>
        @if (generation()) { <div class="bar-track" style="margin-top:12px" role="progressbar" aria-label="Génération en cours"><div class="bar-fill indet"></div></div> }
      </div>
    }

    <div class="card mb-16">
      <div class="card-header"><span class="card-title">Versions ({{ versions().length }})</span>
        <span class="small muted">Les liens de téléchargement sont signés et valables quelques minutes</span></div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr><th>Version</th><th>Générée</th><th>Fichiers</th><th>Empreinte SHA-256 (Word)</th><th></th></tr></thead>
          <tbody>
            @for (v of versions(); track v.id) {
              <tr [class.remplacee]="v.remplacee">
                <td>
                  <div style="font-weight:600">v{{ v.version }}</div>
                  <div class="chips" style="margin-top:4px">
                    <span class="chip" [class.chip-green]="v.nature === 'FINALE'" [class.chip-gold]="v.nature !== 'FINALE'">{{ v.nature === 'FINALE' ? 'Finale' : 'Provisoire' }}</span>
                    @if (v.avecCin) { <span class="chip chip-red" title="Réservée au directeur">avec CIN</span> } @else { <span class="chip chip-blue">sans CIN</span> }
                    @if (v.remplacee) { <span class="chip chip-purple">remplacée</span> }
                  </div>
                </td>
                <td class="small">{{ v.genereLe | date:'d/MM/y HH:mm' }}<div class="muted">{{ v.generePar }}</div>
                  @if (v.nature === 'PROVISOIRE') { <div class="muted">{{ v.nbBloquants }} bloquant(s), {{ v.nbAvertissements }} avert.</div> }</td>
                <td class="small">
                  @for (f of principaux(v); track f.id) { <div>{{ f.format }} · {{ taille(f.taille) }}</div> }
                  <div class="muted">{{ csv(v).length }} tableaux CSV</div>
                </td>
                <td><code class="sha" [title]="docx(v)?.sha256 ?? ''">{{ docx(v)?.sha256 }}</code></td>
                <td>
                  @if (peut(v)) {
                    <div class="actions" style="flex-wrap:wrap">
                      <button class="btn btn-primary btn-sm" (click)="telecharger(v, 'docx')"><span class="mi sm">description</span> Word</button>
                      <button class="btn btn-ghost btn-sm" (click)="telecharger(v, 'pdf')"><span class="mi sm">picture_as_pdf</span> PDF</button>
                      <button class="btn btn-ghost btn-sm" (click)="telecharger(v, 'zip')"><span class="mi sm">folder_zip</span> ZIP</button>
                      <select class="form-control csv-sel" [ngModel]="null" (ngModelChange)="telecharger(v, 'csv', $event)" aria-label="Télécharger un tableau CSV">
                        <option [ngValue]="null">Tableau CSV…</option>
                        @for (f of csv(v); track f.id) { <option [ngValue]="f.tableau">{{ f.tableau }}</option> }
                      </select>
                    </div>
                  } @else { <span class="small muted">Réservée au directeur</span> }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5"><div class="empty"><span class="mi">description</span><p>Aucune version générée pour {{ svc.annee() }}.</p></div></td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    @if (auth.estDirecteur()) {
      <div class="card">
        <div class="card-header"><span class="card-title">Historique des téléchargements</span></div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Date</th><th>Utilisateur</th><th>Version</th><th>Format</th></tr></thead>
            <tbody>
              @for (t of historique(); track t.id) {
                <tr><td class="small">{{ t.telechargeLe | date:'d/MM/y HH:mm:ss' }}</td><td>{{ t.utilisateur }}</td><td>{{ t.annee }} v{{ t.version }}</td><td>{{ t.format }}</td></tr>
              } @empty { <tr><td colspan="4" class="muted small">Aucun téléchargement.</td></tr> }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
  styles: [`
    .gen { display:flex; gap:28px; flex-wrap:wrap; align-items:flex-end; }
    fieldset { border:0; padding:0; margin:0; }
    .check { display:flex; gap:8px; align-items:center; font-size:13px; margin-bottom:6px; cursor:pointer; }
    .sha { font-size:11px; color:var(--muted); display:inline-block; max-width:180px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; vertical-align:middle; }
    .csv-sel { width:auto; padding:5px 8px; font-size:12.5px; }
    .remplacee td { opacity:.6; }
    .indet { width:40%; animation: indet 1.2s ease-in-out infinite; }
    @keyframes indet { 0% { margin-left:-40%; } 100% { margin-left:100%; } }
  `],
})
export class RapportsComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);
  versions = signal<VersionRapport[]>([]);
  historique = signal<Telechargement[]>([]);
  generation = signal(false);
  message = signal('');
  error = signal('');
  finale = false;
  avecCin = false;
  inclureSoumis = false;
  cloturee = computed(() => this.svc.campagne()?.statut === 'CLOTUREE');

  constructor() {
    if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe();
    effect(() => { this.svc.annee(); untracked(() => this.charger()); });
  }

  charger() {
    this.svc.versions(this.svc.annee()).subscribe({ next: v => this.versions.set(v), error: e => this.error.set(messageErreur(e)) });
    if (this.auth.estDirecteur()) this.svc.telechargements().subscribe({ next: h => this.historique.set(h) });
  }

  principaux(v: VersionRapport) { return v.fichiers.filter(f => f.format !== 'CSV'); }
  csv(v: VersionRapport) { return v.fichiers.filter(f => f.format === 'CSV'); }
  docx(v: VersionRapport): FichierRapport | undefined { return v.fichiers.find(f => f.format === 'DOCX'); }
  taille(o: number) { return o > 1048576 ? (o / 1048576).toFixed(1) + ' Mo' : Math.max(1, Math.round(o / 1024)) + ' Ko'; }
  peut(v: VersionRapport) { return v.avecCin ? this.auth.estDirecteur() : this.auth.estAdmin(); }

  generer() {
    this.generation.set(true); this.error.set(''); this.message.set('');
    this.svc.generer(this.svc.annee(), this.finale, this.avecCin, this.inclureSoumis && !this.finale).subscribe({
      next: v => { this.generation.set(false); this.message.set(`Version v${v.version} générée.`); this.charger(); },
      error: e => { this.generation.set(false); this.error.set(messageErreur(e)); },
    });
  }

  telecharger(v: VersionRapport, format: string, tableau?: string | null) {
    if (format === 'csv' && !tableau) return;
    this.error.set('');
    this.svc.lien(v.id, format, tableau).subscribe({
      next: l => { this.svc.ouvrirLien(l); setTimeout(() => this.charger(), 1500); },
      error: e => this.error.set(messageErreur(e)),
    });
  }
}
