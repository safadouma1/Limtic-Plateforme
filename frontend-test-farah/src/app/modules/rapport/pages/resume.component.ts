import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { ResumeRapport } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** F2 – Résumé des activités (3 blocs de 500 caractères au plus, propositions calculées). */
@Component({
  selector: 'app-rapport-resume',
  imports: [FormsModule],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Résumé des activités {{ svc.annee() }}</h1>
        <p class="page-subtitle">F2 · trois blocs de 500 caractères au plus</p></div>
      @if (edit) { <button class="btn btn-ghost" type="button" (click)="proposer()"><span class="mi sm">auto_awesome</span> Proposer à partir des données</button> }
    </div>
    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    @if (r(); as r) {
      <form class="card card-body" (ngSubmit)="enregistrer(r)">
        @for (b of blocs; track b.cle) {
          <div class="form-group">
            <label class="form-label" [for]="b.cle">{{ b.label }}</label>
            <textarea [id]="b.cle" class="form-control" rows="5" maxlength="500" [name]="b.cle" [(ngModel)]="$any(r)[b.cle]" [disabled]="!edit"></textarea>
            <div class="row small" style="justify-content:space-between">
              <span class="form-hint">{{ b.aide }}</span>
              <span [class.text-red]="long(r, b.cle) >= 500" class="muted">{{ long(r, b.cle) }} / 500</span>
            </div>
            @if (proposition()?.[b.cle]; as p) {
              <div class="prop small"><strong>Proposition :</strong> {{ p }}
                <button class="btn btn-ghost btn-sm" type="button" (click)="$any(r)[b.cle] = p.slice(0, 500)">Reprendre</button></div>
            }
          </div>
        }
        @if (edit) { <button class="btn btn-primary" type="submit"><span class="mi sm">save</span> Enregistrer</button> }
      </form>
    }
  `,
  styles: [`.prop { background:var(--surface-2, rgba(255,255,255,.04)); border-radius:10px; padding:10px 12px; margin-top:6px; color:var(--text-soft); }
    .text-red { color:var(--red) !important; }`],
})
export class ResumeComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);
  r = signal<ResumeRapport | null>(null);
  proposition = signal<Record<string, string> | null>(null);
  message = signal(''); error = signal('');
  edit = this.auth.estAdmin();
  blocs = [
    { cle: 'historique', label: 'Historique', aide: 'Création, renouvellements, évolutions de la structure.' },
    { cle: 'thematiques', label: 'Thématiques essentielles de recherche', aide: 'Une thématique par ligne.' },
    { cle: 'bilan', label: 'Bilan global des activités', aide: 'Les chiffres doivent concorder avec les tableaux (contrôle automatique).' },
  ];

  constructor() {
    if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe();
    effect(() => { const a = this.svc.annee(); untracked(() => {
      this.proposition.set(null);
      this.svc.resume(a).subscribe({ next: r => this.r.set(r), error: e => this.error.set(messageErreur(e)) });
    }); });
  }
  long(r: ResumeRapport, cle: string) { return ((r as any)[cle] ?? '').length; }
  proposer() { this.svc.proposition(this.svc.annee()).subscribe({ next: p => this.proposition.set(p), error: e => this.error.set(messageErreur(e)) }); }
  enregistrer(r: ResumeRapport) {
    this.error.set(''); this.message.set('');
    this.svc.enregistrerResume(this.svc.annee(), r).subscribe({ next: x => { this.r.set(x); this.message.set('Résumé enregistré.'); }, error: e => this.error.set(messageErreur(e)) });
  }
}
