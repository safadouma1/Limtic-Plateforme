import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ChercheurService } from '../../../../core/services/chercheur.service';
import { DomaineService } from '../../../../core/services/domaine.service';
import { Chercheur, Domaine } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-domaines-admin',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Gestion des domaines</h1>
        <p class="page-subtitle">Créer et gérer les domaines de recherche</p>
      </div>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <div class="grid-main-side">
      <div class="card">
        <div class="card-header"><span class="card-title">Domaines ({{ domaines().length }})</span></div>
        @for (d of domaines(); track d.id) {
          <div class="pub-line" style="cursor:default">
            <div style="min-width:0">
              <div style="font-weight:500">{{ d.nom }}</div>
              <div class="small muted">{{ d.description || '—' }}</div>
              @if (d.responsables?.length) { <div class="small" style="margin-top:4px">Responsable(s) : @for (r of d.responsables; track r.id; let last = $last) { {{ r.prenom }} {{ r.nom }}@if (!last) {, } }</div> }
              <div class="small muted">{{ d.nbChercheurs }} chercheur(s) · {{ d.nbPublications }} publication(s)</div>
            </div>
            <div class="actions">
              <button class="btn btn-ghost btn-icon" title="Modifier" (click)="editer(d)"><span class="mi sm">edit</span></button>
              <button class="btn btn-danger btn-icon" title="Supprimer" (click)="supprimer(d)"><span class="mi sm">delete</span></button>
            </div>
          </div>
        } @empty {
          <div class="empty"><span class="mi">category</span><p>Aucun domaine créé.</p></div>
        }
      </div>

      <div class="card card-body">
        <div class="card-title mb-16">{{ editId() ? 'Modifier le domaine' : 'Nouveau domaine' }}</div>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="form-group">
            <label class="form-label" for="nom">Nom du domaine *</label>
            <input id="nom" class="form-control" formControlName="nom" placeholder="NLP, Vision par ordinateur…" />
          </div>
          <div class="form-group">
            <label class="form-label" for="desc">Description</label>
            <textarea id="desc" class="form-control" formControlName="description" rows="5" placeholder="Description détaillée de l'axe…"></textarea>
          </div>
          <div class="form-group">
            <label class="form-label" for="resp">Responsable(s) de l'axe</label>
            <select id="resp" class="form-control" multiple size="6" formControlName="responsableIds">
              @for (c of chercheurs(); track c.id) { <option [ngValue]="c.id">{{ c.nom }} {{ c.prenom }}</option> }
            </select>
            <div class="form-hint">Ctrl/Cmd + clic pour en choisir plusieurs. Les membres d'un axe se rattachent depuis leur fiche chercheur.</div>
          </div>
          <div class="row">
            <button type="submit" class="btn btn-primary" [disabled]="form.invalid || saving()">{{ editId() ? 'Mettre à jour' : 'Créer' }}</button>
            @if (editId()) { <button type="button" class="btn btn-ghost" (click)="annuler()">Annuler</button> }
          </div>
        </form>
      </div>
    </div>
  `,
})
export class DomainesAdminComponent implements OnInit {
  private svc = inject(DomaineService);
  private fb = inject(FormBuilder);
  private chSvc = inject(ChercheurService);

  domaines = signal<Domaine[]>([]);
  editId = signal<number | null>(null);
  saving = signal(false);
  error = signal('');

  chercheurs = signal<Chercheur[]>([]);
  form = this.fb.nonNullable.group({
    nom: ['', Validators.required],
    description: [''],
    responsableIds: this.fb.nonNullable.control<number[]>([]),
  });

  ngOnInit() {
    this.charger();
    this.chSvc.getAll().subscribe({ next: c => this.chercheurs.set(c.filter(x => x.categorie !== 'CADRE_ASSIMILE')), error: () => {} });
  }

  charger() { this.svc.getAll().subscribe({ next: d => this.domaines.set(d), error: e => this.error.set(messageErreur(e)) }); }

  editer(d: Domaine) {
    this.editId.set(d.id);
    this.form.setValue({ nom: d.nom, description: d.description ?? '', responsableIds: (d.responsables ?? []).map(r => r.id) });
  }

  annuler() { this.editId.set(null); this.form.reset(); }

  submit() {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set('');
    const id = this.editId();
    const data = this.form.getRawValue();
    (id ? this.svc.update(id, data) : this.svc.create(data)).subscribe({
      next: () => { this.annuler(); this.charger(); this.saving.set(false); },
      error: e => { this.error.set(messageErreur(e)); this.saving.set(false); },
    });
  }

  supprimer(d: Domaine) {
    if (!confirm(`Supprimer le domaine « ${d.nom} » ? Les chercheurs et publications associés seront conservés sans domaine.`)) return;
    this.svc.delete(d.id).subscribe({ next: () => this.charger(), error: e => this.error.set(messageErreur(e)) });
  }
}
