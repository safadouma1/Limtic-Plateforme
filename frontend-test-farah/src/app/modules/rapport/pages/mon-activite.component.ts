import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { CATEGORIES } from '../formulaires';
import { messageErreur } from '../../../shared/erreur';

/** Espace du chercheur : ses déclarations et sa fiche d'activité individuelle. */
@Component({
  selector: 'app-rapport-mon-activite',
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Mon activité {{ svc.annee() }}</h1>
        <p class="page-subtitle">Déclarez vos productions : elles alimentent le rapport après validation par l'administrateur</p></div>
      @if (auth.currentUser()?.chercheurId) {
        <div class="row">
          <button class="btn btn-ghost" (click)="fiche('docx')" [disabled]="busy()"><span class="mi sm">description</span> Ma fiche (Word)</button>
          <button class="btn btn-primary" (click)="fiche('pdf')" [disabled]="busy()"><span class="mi sm">picture_as_pdf</span> Ma fiche (PDF)</button>
        </div>
      }
    </div>
    @if (!auth.currentUser()?.chercheurId) {
      <div class="alert alert-info"><span class="mi sm">info</span> Votre compte n'est pas encore rattaché à une fiche membre. Demandez à l'administrateur de le lier depuis « Comptes ».</div>
    }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    <div class="cats">
      @for (c of categories; track c.code) {
        <a class="card card-body card-link cat" [routerLink]="['/rapport/saisie', c.code]">
          <span class="mi">{{ c.icone }}</span>
          <div><div style="font-weight:600">{{ c.titre }}</div><div class="small muted">{{ c.f }}</div></div>
        </a>
      }
    </div>
    <p class="form-hint" style="margin-top:16px">Cycle : brouillon → soumis → validé. Un enregistrement rejeté revient en brouillon avec le motif du rejet.</p>
  `,
  styles: [`.cats { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:12px; }
    .cat { display:flex; gap:12px; align-items:center; } .cat .mi { font-size:26px; color:var(--accent, var(--blue)); }`],
})
export class MonActiviteComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);
  categories = CATEGORIES.filter(c => c.saisieChercheur);
  busy = signal(false); error = signal('');
  constructor() { if (!this.svc.campagnes().length) this.svc.chargerCampagnes().subscribe(); }
  fiche(format: 'pdf' | 'docx') {
    this.busy.set(true); this.error.set('');
    this.svc.ficheActivite(this.svc.annee(), format).subscribe({
      next: b => { this.busy.set(false); this.svc.enregistrerBlob(b, `Fiche_activite_${this.svc.annee()}.${format}`); },
      error: e => { this.busy.set(false); this.error.set(messageErreur(e)); },
    });
  }
}
