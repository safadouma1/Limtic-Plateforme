import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { LigneBudget } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** F18 – Situation financière (crédits disponibles, engagés, reliquat calculé). */
@Component({
  selector: 'app-rapport-budget',
  imports: [FormsModule, DecimalPipe],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Situation financière {{ svc.annee() }}</h1>
        <p class="page-subtitle">F18 · montants en dinars, le reliquat est calculé automatiquement</p></div>
    </div>
    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    <form class="card" (ngSubmit)="enregistrer()">
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr><th>Rubrique</th><th class="num">Crédits disponibles</th><th class="num">Crédits engagés</th><th class="num">Reliquat</th></tr></thead>
          <tbody>
            @for (l of lignes(); track l.id; let i = $index) {
              <tr>
                <td>{{ l.rubrique }}</td>
                <td class="num"><input class="form-control num" type="number" min="0" step="0.001" [name]="'d' + i" [(ngModel)]="l.creditsDisponibles" [disabled]="!edit" [attr.aria-label]="'Crédits disponibles ' + l.rubrique" /></td>
                <td class="num"><input class="form-control num" type="number" min="0" step="0.001" [name]="'e' + i" [(ngModel)]="l.creditsEngages" [disabled]="!edit" [attr.aria-label]="'Crédits engagés ' + l.rubrique" /></td>
                <td class="num" [class.neg]="reliquat(l) < 0">{{ reliquat(l) | number:'1.3-3' }}</td>
              </tr>
            }
            <tr class="tot"><td>Total</td><td class="num">{{ total('creditsDisponibles') | number:'1.3-3' }}</td><td class="num">{{ total('creditsEngages') | number:'1.3-3' }}</td>
              <td class="num">{{ total('creditsDisponibles') - total('creditsEngages') | number:'1.3-3' }}</td></tr>
          </tbody>
        </table>
      </div>
      @if (edit) { <div class="card-body"><button class="btn btn-primary" type="submit"><span class="mi sm">save</span> Enregistrer</button></div> }
    </form>
  `,
  styles: [`.num { text-align:right; } input.num { max-width:160px; margin-left:auto; } .neg { color:var(--red); } .tot td { font-weight:700; }`],
})
export class BudgetComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);
  lignes = signal<LigneBudget[]>([]);
  message = signal(''); error = signal('');
  edit = this.auth.estAdmin();

  constructor() {
    if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe();
    effect(() => { const a = this.svc.annee(); untracked(() =>
      this.svc.budget(a).subscribe({ next: l => this.lignes.set(l), error: e => this.error.set(messageErreur(e)) })); });
  }
  reliquat(l: LigneBudget) { return (Number(l.creditsDisponibles) || 0) - (Number(l.creditsEngages) || 0); }
  total(k: 'creditsDisponibles' | 'creditsEngages') { return this.lignes().reduce((s, l) => s + (Number(l[k]) || 0), 0); }
  enregistrer() {
    this.error.set(''); this.message.set('');
    this.svc.enregistrerBudget(this.svc.annee(), this.lignes()).subscribe({ next: l => { this.lignes.set(l); this.message.set('Situation financière enregistrée.'); }, error: e => this.error.set(messageErreur(e)) });
  }
}
