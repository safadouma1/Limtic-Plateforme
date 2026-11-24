import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ChercheurService } from '../../../../../core/services/chercheur.service';
import { DomaineService } from '../../../../../core/services/domaine.service';
import { Domaine, StatutMembre } from '../../../../../core/models/chercheur.model';
import { AvatarComponent } from '../../../../../shared/avatar.component';
import { DepotComponent } from '../../../../../shared/depot.component';
import { messageErreur } from '../../../../../shared/erreur';

/** Fiche publique d'un chercheur (§3.3, §4.3.2). Les champs du rapport (CIN, catégorie…) se saisissent dans le module Rapport (F3/F5). */
@Component({
  selector: 'app-chercheur-form',
  imports: [ReactiveFormsModule, RouterLink, AvatarComponent, DepotComponent],
  template: `
    <div class="row mb-24">
      <a routerLink="/admin/chercheurs" class="btn btn-ghost btn-icon" aria-label="Retour"><span class="mi sm">arrow_back</span></a>
      <div>
        <h1 class="page-title" style="margin:0">{{ isEdit ? 'Modifier le' : 'Nouveau' }} chercheur</h1>
        <p class="page-subtitle">Page publique : identité, coordonnées, biographie, profils externes</p>
      </div>
    </div>
    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <form class="grid-main-side" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="card card-body">
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="prenom">Prénom *</label><input id="prenom" class="form-control" formControlName="prenom" /></div>
          <div class="form-group"><label class="form-label" for="nom">Nom *</label><input id="nom" class="form-control" formControlName="nom" /></div>
        </div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="email">E-mail institutionnel</label>
            <input id="email" class="form-control" type="email" formControlName="email" placeholder="prenom.nom@isi.utm.tn" />
            @if (form.controls.email.touched && form.controls.email.invalid) { <div class="form-error">E-mail invalide.</div> }</div>
          <div class="form-group"><label class="form-label" for="grade">Grade</label><input id="grade" class="form-control" formControlName="grade" list="grades" />
            <datalist id="grades">@for (g of grades; track g) { <option [value]="g"></option> }</datalist></div>
        </div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="specialite">Spécialité</label><input id="specialite" class="form-control" formControlName="specialite" /></div>
          <div class="form-group"><label class="form-label" for="domaine">Axe de recherche</label>
            <select id="domaine" class="form-control" formControlName="domaineId"><option [ngValue]="null">— Aucun —</option>
              @for (d of domaines(); track d.id) { <option [ngValue]="d.id">{{ d.nom }}</option> }</select></div>
        </div>
        <div class="form-group"><label class="form-label" for="etab">Établissement</label><input id="etab" class="form-control" formControlName="etablissement" /></div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="bureau">Bureau</label><input id="bureau" class="form-control" formControlName="bureau" /></div>
          <div class="form-group"><label class="form-label" for="tel">Téléphone</label><input id="tel" class="form-control" formControlName="telephone" /></div>
        </div>
        <div class="form-group"><label class="form-label" for="statut">Statut</label>
          <select id="statut" class="form-control" formControlName="statutMembre">
            @for (s of statuts; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }</select></div>
        <div class="form-group"><label class="form-label" for="bio">Biographie</label><textarea id="bio" class="form-control" rows="5" maxlength="4000" formControlName="biographie"></textarea></div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="gs">Google Scholar</label><input id="gs" class="form-control" type="url" formControlName="googleScholarUrl" placeholder="https://…" /></div>
          <div class="form-group"><label class="form-label" for="rg">ResearchGate</label><input id="rg" class="form-control" type="url" formControlName="researchGateUrl" placeholder="https://…" /></div>
        </div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="orcid">ORCID</label><input id="orcid" class="form-control" formControlName="orcid" placeholder="0000-0000-0000-0000" />
            @if (form.controls.orcid.touched && form.controls.orcid.invalid) { <div class="form-error">Format attendu : 0000-0000-0000-000X.</div> }</div>
          <div class="form-group"><label class="form-label" for="li">LinkedIn</label><input id="li" class="form-control" type="url" formControlName="linkedinUrl" placeholder="https://…" /></div>
        </div>
        <div class="row mt-8">
          <button type="submit" class="btn btn-primary" [disabled]="loading()">@if (loading()) { Enregistrement… } @else { {{ isEdit ? 'Mettre à jour' : 'Créer le chercheur' }} }</button>
          <a routerLink="/admin/chercheurs" class="btn btn-ghost">Annuler</a>
        </div>
      </div>
      <div class="card card-body photo">
        <app-avatar [photo]="photoUrl()" [nom]="form.value.prenom + ' ' + form.value.nom" [grand]="true" />
        <app-depot libelle="Photo de profil" icone="photo_camera" accept="image/jpeg,image/png,image/webp" (deposes)="photo($event[0].id, $event[0].url)" />
        @if (form.value.photoMediaId) { <button type="button" class="btn btn-ghost btn-sm" (click)="photo(null, null)">Retirer la photo</button> }
        <p class="form-hint">Le CIN, la catégorie (corps A/B, cadre) et les dates d'entrée se gèrent dans Rapport d'activité → Membres (F3/F5).</p>
      </div>
    </form>
  `,
  styles: ['.photo { align-self:start; display:flex; flex-direction:column; align-items:center; gap:12px; text-align:center; }'],
})
export class ChercheurFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private svc = inject(ChercheurService);
  private dsvc = inject(DomaineService);
  private router = inject(Router);

  id = input<string>();
  isEdit = false;
  loading = signal(false);
  error = signal('');
  domaines = signal<Domaine[]>([]);
  photoUrl = signal<string | null>(null);
  grades = ["Professeur d'Enseignement Supérieur", 'Maître de Conférences', 'Maître Assistant', 'Assistant', 'Assistant Contractuel', 'Post Doc', 'Technologue'];
  statuts: [StatutMembre, string][] = [['ACTIF', 'Actif'], ['RETRAITE', 'Retraité'], ['MUTE', 'Muté'], ['DEMISSIONNAIRE', 'Démissionnaire'], ['DECEDE', 'Décédé']];

  form = this.fb.group({
    prenom: this.fb.nonNullable.control('', Validators.required),
    nom: this.fb.nonNullable.control('', Validators.required),
    email: this.fb.nonNullable.control('', Validators.email),
    grade: this.fb.nonNullable.control(''),
    specialite: this.fb.nonNullable.control(''),
    etablissement: this.fb.nonNullable.control(''),
    domaineId: this.fb.control<number | null>(null),
    bureau: this.fb.nonNullable.control(''),
    telephone: this.fb.nonNullable.control(''),
    statutMembre: this.fb.nonNullable.control<StatutMembre>('ACTIF'),
    biographie: this.fb.nonNullable.control(''),
    googleScholarUrl: this.fb.nonNullable.control('', Validators.pattern(/^(https?:\/\/\S+)?$/)),
    researchGateUrl: this.fb.nonNullable.control('', Validators.pattern(/^(https?:\/\/\S+)?$/)),
    linkedinUrl: this.fb.nonNullable.control('', Validators.pattern(/^(https?:\/\/\S+)?$/)),
    orcid: this.fb.nonNullable.control('', Validators.pattern(/^(\d{4}-\d{4}-\d{4}-\d{3}[\dX])?$/)),
    photoMediaId: this.fb.control<number | null>(null),
  });

  ngOnInit() {
    this.dsvc.getAll().subscribe({ next: d => this.domaines.set(d), error: () => {} });
    const id = Number(this.id());
    if (id) {
      this.isEdit = true;
      this.svc.getById(id).subscribe({
        next: c => {
          this.form.patchValue({
            prenom: c.prenom, nom: c.nom, email: c.email ?? '', grade: c.grade ?? '', specialite: c.specialite ?? '',
            etablissement: c.etablissement ?? '', domaineId: c.domaine?.id ?? null, bureau: c.bureau ?? '', telephone: c.telephone ?? '',
            statutMembre: c.statutMembre ?? 'ACTIF', biographie: c.biographie ?? '', googleScholarUrl: c.googleScholarUrl ?? '',
            researchGateUrl: c.researchGateUrl ?? '', linkedinUrl: c.linkedinUrl ?? '', orcid: c.orcid ?? '', photoMediaId: c.photoMediaId ?? null,
          });
          this.photoUrl.set(c.photoUrl ?? null);
        },
        error: e => this.error.set(messageErreur(e)),
      });
    }
  }

  photo(id: number | null, url: string | null) { this.form.patchValue({ photoMediaId: id }); this.photoUrl.set(url); }

  submit() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');
    const payload = this.form.getRawValue();
    const obs = this.isEdit ? this.svc.update(Number(this.id()), payload) : this.svc.create(payload);
    obs.subscribe({
      next: () => this.router.navigate(['/admin/chercheurs']),
      error: e => { this.error.set(messageErreur(e, 'Erreur lors de la sauvegarde.')); this.loading.set(false); },
    });
  }
}
