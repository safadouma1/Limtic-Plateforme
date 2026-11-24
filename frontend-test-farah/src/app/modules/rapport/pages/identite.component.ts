import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RapportService } from '../../../core/services/rapport.service';
import { AuthService } from '../../../core/services/auth.service';
import { MembreRef, StructureIdentite } from '../../../core/models/rapport.model';
import { messageErreur } from '../../../shared/erreur';

/** F1 – Identification de la structure (page de garde). */
@Component({
  selector: 'app-rapport-identite',
  imports: [FormsModule],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Identification de la structure</h1>
        <p class="page-subtitle">F1 · page de garde du rapport, reprise d'une année sur l'autre</p></div>
    </div>
    @if (message()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ message() }}</div> }
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }
    @if (i(); as i) {
      <form class="card card-body form-grid" (ngSubmit)="enregistrer(i)">
        <div class="form-group large"><label class="form-label" for="u">Université / Centre / EPS *</label><input id="u" class="form-control" name="u" [(ngModel)]="i.universite" required [disabled]="!edit" /></div>
        <div class="form-group large"><label class="form-label" for="e">Établissement *</label><input id="e" class="form-control" name="e" [(ngModel)]="i.etablissement" required [disabled]="!edit" /></div>
        <div class="form-group large"><label class="form-label" for="d">Dénomination du LR/UR *</label><input id="d" class="form-control" name="d" [(ngModel)]="i.denomination" required [disabled]="!edit" /></div>
        <div class="form-group"><label class="form-label" for="c">Code de la structure *</label><input id="c" class="form-control" name="c" [(ngModel)]="i.code" required [disabled]="!edit" /></div>
        <div class="form-group"><label class="form-label" for="chef">Chef du LR/UR *</label>
          <select id="chef" class="form-control" name="chef" [(ngModel)]="i.chefId" [disabled]="!edit">
            @for (m of membres(); track m.id) { <option [ngValue]="m.id">{{ m.nom }} {{ m.prenom }}</option> }
          </select>
          <span class="form-hint">Grade affiché : {{ grade(i.chefId) || i.chefGrade }}</span></div>
        <div class="form-group"><label class="form-label" for="f">Fonction</label><input id="f" class="form-control" name="f" [(ngModel)]="i.fonction" [disabled]="!edit" /></div>
        <div class="form-group"><label class="form-label" for="t">Téléphone</label><input id="t" class="form-control" name="t" [(ngModel)]="i.telephone" [disabled]="!edit" /></div>
        <div class="form-group"><label class="form-label" for="fx">Fax</label><input id="fx" class="form-control" name="fx" [(ngModel)]="i.fax" [disabled]="!edit" /></div>
        <div class="form-group"><label class="form-label" for="m">E-mail *</label><input id="m" type="email" class="form-control" name="m" [(ngModel)]="i.email" required [disabled]="!edit" /></div>
        <div class="form-group"><label class="form-label" for="w">Site web</label><input id="w" class="form-control" name="w" [(ngModel)]="i.siteWeb" [disabled]="!edit" /></div>
        @if (edit) { <div class="large"><button class="btn btn-primary" type="submit"><span class="mi sm">save</span> Enregistrer</button></div> }
      </form>
    }
  `,
  styles: [`.form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:4px 18px; } .large { grid-column:1/-1; }
    @media (max-width:720px) { .form-grid { grid-template-columns:1fr; } }`],
})
export class IdentiteComponent {
  svc = inject(RapportService);
  auth = inject(AuthService);
  i = signal<StructureIdentite | null>(null);
  membres = signal<MembreRef[]>([]);
  message = signal(''); error = signal('');
  edit = this.auth.estAdmin();

  constructor() {
    this.svc.identite().subscribe({ next: i => this.i.set(i), error: e => this.error.set(messageErreur(e)) });
    this.svc.referentiels().subscribe(r => this.membres.set(r.membres.filter(m => m.categorie === 'ENSEIGNANT_CHERCHEUR')));
  }
  grade(id: number | null) { return this.membres().find(m => m.id === id)?.grade ?? ''; }
  enregistrer(i: StructureIdentite) {
    this.error.set(''); this.message.set('');
    this.svc.enregistrerIdentite(i).subscribe({ next: r => { this.i.set(r); this.message.set('Identification enregistrée.'); }, error: e => this.error.set(messageErreur(e)) });
  }
}
