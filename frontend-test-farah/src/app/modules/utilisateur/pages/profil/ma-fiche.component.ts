import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../../../core/services/site.service';
import { DomaineService } from '../../../../core/services/domaine.service';
import { Chercheur, Domaine } from '../../../../core/models/chercheur.model';
import { AvatarComponent } from '../../../../shared/avatar.component';
import { DepotComponent } from '../../../../shared/depot.component';
import { messageErreur } from '../../../../shared/erreur';

/** Le chercheur modifie sa propre page publique (§3.3.2, parcours §7.3). */
@Component({
  selector: 'app-ma-fiche',
  imports: [FormsModule, RouterLink, AvatarComponent, DepotComponent],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Ma page chercheur</h1>
        <p class="page-subtitle">Ces informations apparaissent sur votre page publique</p></div>
      @if (f(); as f) { <a class="btn btn-ghost" [routerLink]="['/chercheurs', f.id]" target="_blank"><span class="mi sm">open_in_new</span> Voir ma page</a> }
    </div>
    @if (ok()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ ok() }}</div> }
    @if (erreur()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div> }
    @if (f(); as f) {
      <form class="grid-main-side" (ngSubmit)="enregistrer(f)">
        <div class="card card-body">
          <div class="form-row">
            <div class="form-group"><label class="form-label">Nom</label><input class="form-control" [value]="f.prenom + ' ' + f.nom" disabled /></div>
            <div class="form-group"><label class="form-label">Grade</label><input class="form-control" [value]="f.grade ?? ''" disabled />
              <span class="form-hint">Nom et grade sont gérés par l'administrateur.</span></div>
          </div>
          <div class="form-group"><label class="form-label" for="sp">Spécialité</label><input id="sp" class="form-control" name="sp" [(ngModel)]="f.specialite" /></div>
          <div class="form-group"><label class="form-label" for="ax">Axe de recherche principal</label>
            <select id="ax" class="form-control" name="ax" [(ngModel)]="domaineId"><option [ngValue]="null">—</option>
              @for (d of axes(); track d.id) { <option [ngValue]="d.id">{{ d.nom }}</option> }</select></div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="bu">Bureau</label><input id="bu" class="form-control" name="bu" [(ngModel)]="f.bureau" /></div>
            <div class="form-group"><label class="form-label" for="te">Téléphone</label><input id="te" class="form-control" name="te" [(ngModel)]="f.telephone" /></div>
          </div>
          <div class="form-group"><label class="form-label" for="bio">Biographie</label>
            <textarea id="bio" class="form-control" rows="6" maxlength="4000" name="bio" [(ngModel)]="f.biographie"></textarea>
            <span class="form-hint">{{ (f.biographie ?? '').length }} / 4000</span></div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="gs">Google Scholar</label><input id="gs" class="form-control" type="url" placeholder="https://scholar.google.com/…" name="gs" [(ngModel)]="f.googleScholarUrl" /></div>
            <div class="form-group"><label class="form-label" for="rg">ResearchGate</label><input id="rg" class="form-control" type="url" placeholder="https://www.researchgate.net/…" name="rg" [(ngModel)]="f.researchGateUrl" /></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="or">ORCID</label><input id="or" class="form-control" placeholder="0000-0002-1825-0097" pattern="\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]" name="or" [(ngModel)]="f.orcid" /></div>
            <div class="form-group"><label class="form-label" for="li">LinkedIn</label><input id="li" class="form-control" type="url" placeholder="https://www.linkedin.com/in/…" name="li" [(ngModel)]="f.linkedinUrl" /></div>
          </div>
          <button class="btn btn-primary" type="submit" [disabled]="envoi()"><span class="mi sm">save</span> Enregistrer</button>
        </div>
        <div class="card card-body" style="align-self:start;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px">
          <app-avatar [photo]="photoUrl()" [nom]="f.prenom + ' ' + f.nom" [grand]="true" />
          <app-depot libelle="Changer la photo" icone="photo_camera" accept="image/jpeg,image/png,image/webp" (deposes)="f.photoMediaId = $event[0].id; photoUrl.set($event[0].url)" />
          @if (f.photoMediaId) { <button type="button" class="btn btn-ghost btn-sm" (click)="f.photoMediaId = null; photoUrl.set(null)">Retirer la photo</button> }
          <p class="form-hint">JPEG, PNG ou WebP, 10 Mo au plus. Pensez à enregistrer.</p>
          <a routerLink="/rapport/mon-activite" class="btn btn-ghost btn-sm"><span class="mi sm">edit_note</span> Déclarer mes publications</a>
        </div>
      </form>
    }
  `,
})
export class MaFicheComponent {
  private site = inject(SiteService);
  f = signal<Chercheur | null>(null);
  axes = signal<Domaine[]>([]);
  photoUrl = signal<string | null>(null);
  domaineId: number | null = null;
  ok = signal(''); erreur = signal(''); envoi = signal(false);

  constructor() {
    this.site.maFiche().subscribe({ next: f => { this.f.set(f); this.photoUrl.set(f.photoUrl ?? null); this.domaineId = f.domaine?.id ?? null; },
      error: e => this.erreur.set(messageErreur(e)) });
    inject(DomaineService).getAll().subscribe(d => this.axes.set(d));
  }
  enregistrer(f: Chercheur) {
    this.envoi.set(true); this.ok.set(''); this.erreur.set('');
    this.site.enregistrerMaFiche({ ...f, domaineId: this.domaineId }).subscribe({
      next: r => { this.envoi.set(false); this.f.set(r); this.ok.set('Votre page a été mise à jour.'); },
      error: e => { this.envoi.set(false); this.erreur.set(messageErreur(e)); },
    });
  }
}
