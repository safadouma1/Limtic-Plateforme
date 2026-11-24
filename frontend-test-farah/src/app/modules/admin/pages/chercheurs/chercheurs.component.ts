import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChercheurService } from '../../../../core/services/chercheur.service';
import { Chercheur } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';
import { SiteService } from '../../../../core/services/site.service';
import { AvatarComponent } from '../../../../shared/avatar.component';

@Component({
  selector: 'app-chercheurs-admin',
  imports: [RouterLink, FormsModule, AvatarComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Gestion des chercheurs</h1>
        <p class="page-subtitle">Administration · ajouter, modifier, supprimer</p>
      </div>
      <div class="row">
        <button class="btn btn-ghost" (click)="exporter('chercheurs')"><span class="mi sm">download</span> Chercheurs (CSV)</button>
        <button class="btn btn-ghost" (click)="exporter('doctorants')"><span class="mi sm">download</span> Doctorants (CSV)</button>
        <button class="btn btn-ghost" (click)="exporter('masteriens')"><span class="mi sm">download</span> Mastériens (CSV)</button>
        <a routerLink="/rapport/parametres" class="btn btn-ghost" title="Import CSV ou Excel"><span class="mi sm">upload</span> Importer</a>
        <a routerLink="nouveau" class="btn btn-primary"><span class="mi sm">add</span> Nouveau chercheur</a>
      </div>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <div class="card">
      <div class="card-header">
        <span class="card-title">Chercheurs ({{ filtres().length }})</span>
        <div class="search-input" style="width:260px;max-width:100%">
          <span class="mi">search</span>
          <input class="form-control" type="search" placeholder="Rechercher…" [ngModel]="q()" (ngModelChange)="q.set($event)" />
        </div>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr><th>Chercheur</th><th>Email</th><th>Domaine</th><th>Établissement</th><th>Publ.</th><th></th></tr></thead>
          <tbody>
            @for (c of filtres(); track c.id) {
              <tr>
                <td>
                  <div class="r-cell">
                    <app-avatar [photo]="c.photoUrl" [nom]="c.prenom + ' ' + c.nom" />
                    <div>
                      <div style="font-weight:500">{{ c.prenom }} {{ c.nom }}</div>
                      <div class="small muted">{{ c.specialite }}</div>
                    </div>
                  </div>
                </td>
                <td class="muted">{{ c.email }}</td>
                <td>@if (c.domaine) { <span class="chip chip-purple">{{ c.domaine.nom }}</span> }</td>
                <td class="small">{{ c.etablissement }}</td>
                <td>{{ c.nbPublications }}</td>
                <td>
                  <div class="actions">
                    <a [routerLink]="[c.id, 'modifier']" class="btn btn-ghost btn-icon" title="Modifier"><span class="mi sm">edit</span></a>
                    <button class="btn btn-danger btn-icon" title="Supprimer" (click)="supprimer(c)"><span class="mi sm">delete</span></button>
                  </div>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="muted" style="text-align:center;padding:40px">Aucun chercheur trouvé.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class ChercheursAdminComponent implements OnInit {
  private svc = inject(ChercheurService);
  private site = inject(SiteService);
  chercheurs = signal<Chercheur[]>([]);
  q = signal('');
  error = signal('');

  // Liste filtrée calculée : reste synchronisée après une suppression
  filtres = computed(() => {
    const q = this.q().toLowerCase().trim();
    return this.chercheurs().filter(c => !q || `${c.nom} ${c.prenom} ${c.email} ${c.etablissement ?? ''}`.toLowerCase().includes(q));
  });

  ngOnInit() {
    this.svc.getAll().subscribe({ next: d => this.chercheurs.set(d), error: e => this.error.set(messageErreur(e)) });
  }

  exporter(type: 'chercheurs' | 'doctorants' | 'masteriens') {
    this.site.exporterMembres(type).subscribe({ next: b => this.site.enregistrerBlob(b, `LIMTIC_${type}.csv`), error: e => this.error.set(messageErreur(e)) });
  }

  supprimer(c: Chercheur) {
    if (!confirm(`Supprimer ${c.prenom} ${c.nom} ? Il sera retiré des publications associées.`)) return;
    this.svc.delete(c.id).subscribe({
      next: () => this.chercheurs.update(list => list.filter(x => x.id !== c.id)),
      error: e => this.error.set(messageErreur(e)),
    });
  }
}
