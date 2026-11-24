import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RapportService } from '../../../core/services/rapport.service';
import { AuditLog } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** Journal d'audit (créations, validations, clôtures, consultations de CIN, générations). */
@Component({
  selector: 'app-rapport-journal',
  imports: [DatePipe],
  template: `
    <div class="page-head"><div><h1 class="page-title">Journal d'audit</h1>
      <p class="page-subtitle">Les 500 dernières opérations sensibles du module</p></div></div>
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    <div class="card"><div class="table-wrap">
      <table class="data-table">
        <thead><tr><th>Date</th><th>Utilisateur</th><th>Action</th><th>Détails</th></tr></thead>
        <tbody>
          @for (l of lignes(); track l.id) {
            <tr><td class="small" style="white-space:nowrap">{{ l.date | date:'d/MM/y HH:mm:ss' }}</td><td>{{ l.utilisateur }}</td>
              <td><span class="chip" [class.chip-red]="l.action.includes('CIN')" [class.chip-blue]="!l.action.includes('CIN')">{{ l.action }}</span></td><td class="small">{{ l.details }}</td></tr>
          } @empty { <tr><td colspan="4" class="muted small">Aucune opération.</td></tr> }
        </tbody>
      </table>
    </div></div>
  `,
})
export class JournalComponent {
  private svc = inject(RapportService);
  lignes = signal<AuditLog[]>([]);
  error = signal('');
  constructor() { this.svc.journal().subscribe({ next: l => this.lignes.set(l), error: e => this.error.set(messageErreur(e)) }); }
}
