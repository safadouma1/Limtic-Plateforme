import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { SiteService } from '../../../../core/services/site.service';
import { ContactMessage } from '../../../../core/models/site.model';
import { messageErreur } from '../../../../shared/erreur';

/** Messages reçus par le formulaire de contact du site. */
@Component({
  selector: 'app-messages',
  imports: [DatePipe],
  template: `
    <div class="page-head"><div><h1 class="page-title">Messages de contact</h1>
      <p class="page-subtitle">{{ nonLus() }} non lu(s) sur {{ liste().length }}</p></div></div>
    @if (erreur()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div> }
    <div class="stack">
      @for (m of liste(); track m.id) {
        <article class="card card-body msg" [class.nl]="!m.lu">
          <div class="row" style="justify-content:space-between;align-items:flex-start">
            <div><div style="font-weight:600">{{ m.sujet }}</div>
              <div class="small muted">{{ m.nom }} · <a [href]="'mailto:' + m.email + '?subject=' + encode('Re: ' + m.sujet)" style="color:var(--accent)">{{ m.email }}</a> · {{ m.creeLe | date:'d/MM/y HH:mm' }}</div></div>
            <div class="actions">
              <button class="btn btn-ghost btn-sm" (click)="lu(m, !m.lu)">{{ m.lu ? 'Marquer non lu' : 'Marquer lu' }}</button>
              <button class="btn btn-danger btn-icon" (click)="supprimer(m)" aria-label="Supprimer"><span class="mi sm">delete</span></button>
            </div>
          </div>
          <p class="corps">{{ m.message }}</p>
        </article>
      } @empty { <div class="card card-body empty"><span class="mi">inbox</span><p>Aucun message.</p></div> }
    </div>
  `,
  styles: [`.msg.nl { border-left:3px solid var(--accent); } .corps { white-space:pre-line; color:var(--text-soft); margin-top:10px; }`],
})
export class MessagesComponent {
  private site = inject(SiteService);
  liste = signal<ContactMessage[]>([]);
  erreur = signal('');
  nonLus = () => this.liste().filter(m => !m.lu).length;
  constructor() { this.charger(); }
  charger() { this.site.messages().subscribe({ next: l => this.liste.set(l), error: e => this.erreur.set(messageErreur(e)) }); }
  lu(m: ContactMessage, v: boolean) { this.site.marquerLu(m.id, v).subscribe({ next: r => this.liste.update(l => l.map(x => x.id === r.id ? r : x)) }); }
  supprimer(m: ContactMessage) {
    if (!confirm('Supprimer ce message ?')) return;
    this.site.supprimerMessage(m.id).subscribe({ next: () => this.liste.update(l => l.filter(x => x.id !== m.id)), error: e => this.erreur.set(messageErreur(e)) });
  }
  encode(s: string) { return encodeURIComponent(s); }
}
