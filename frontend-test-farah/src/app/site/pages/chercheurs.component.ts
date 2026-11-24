import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChercheurService } from '../../core/services/chercheur.service';
import { DomaineService } from '../../core/services/domaine.service';
import { Chercheur, Domaine } from '../../core/models/chercheur.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { SeoService } from '../../shared/seo.service';

/** Liste des chercheurs permanents (§3.3.1) : filtres par axe, grade et statut. */
@Component({
  selector: 'app-site-chercheurs',
  imports: [FormsModule, RouterLink, AvatarComponent],
  template: `
    <div class="site-hero-mini"><div class="site-wrap">
      <h1>Chercheurs</h1><p>Enseignants-chercheurs permanents et cadres du laboratoire.</p>
    </div></div>
    <div class="site-wrap site-page">
      <div class="filters" role="search">
        <div class="search-input"><span class="mi">search</span>
          <input class="form-control" type="search" placeholder="Nom, spécialité…" aria-label="Rechercher un chercheur" [ngModel]="q()" (ngModelChange)="q.set($event)" /></div>
        <select class="form-control" aria-label="Axe de recherche" [ngModel]="axe()" (ngModelChange)="axe.set($event)">
          <option [ngValue]="null">Tous les axes</option>
          @for (d of axes(); track d.id) { <option [ngValue]="d.id">{{ d.nom }}</option> }
        </select>
        <select class="form-control" aria-label="Grade" [ngModel]="grade()" (ngModelChange)="grade.set($event)">
          <option value="">Tous les grades</option>
          @for (g of grades(); track g) { <option [value]="g">{{ g }}</option> }
        </select>
        <select class="form-control" aria-label="Statut" [ngModel]="statut()" (ngModelChange)="statut.set($event)">
          <option value="ACTIF">Actifs</option><option value="RETRAITE">Retraités</option><option value="">Tous</option>
        </select>
        <select class="form-control" aria-label="Catégorie" [ngModel]="cat()" (ngModelChange)="cat.set($event)">
          <option value="ENSEIGNANT_CHERCHEUR">Permanents</option><option value="CADRE_ASSIMILE">Cadres équivalents assistants</option><option value="">Tous</option>
        </select>
      </div>
      <p class="small muted mb-16">{{ filtres().length }} chercheur(s)</p>
      <div class="person-grid">
        @for (c of filtres(); track c.id) {
          <a class="person-card" [routerLink]="['/chercheurs', c.id]">
            <app-avatar [photo]="c.photoUrl" [nom]="c.prenom + ' ' + c.nom" />
            <div style="min-width:0">
              <div class="pc-name">{{ c.prenom }} {{ c.nom }}</div>
              <div class="pc-sub">{{ c.grade }}</div>
              @if (c.specialite) { <div class="pc-sub">{{ c.specialite }}</div> }
              <div class="chips" style="margin-top:8px">
                @if (c.domaine) { <span class="chip chip-blue">{{ c.domaine.nom }}</span> }
                <span class="chip">{{ c.nbPublications }} publications</span>
              </div>
            </div>
          </a>
        } @empty { <p class="muted">Aucun chercheur ne correspond aux filtres.</p> }
      </div>
    </div>
  `,
})
export class SiteChercheursComponent {
  private svc = inject(ChercheurService);
  private dsvc = inject(DomaineService);
  /** ?axe=ID depuis la page d'un axe. */
  axeParam = input<string>(undefined, { alias: 'axe' });
  tous = signal<Chercheur[]>([]);
  axes = signal<Domaine[]>([]);
  q = signal(''); axe = signal<number | null>(null); grade = signal(''); statut = signal('ACTIF'); cat = signal('ENSEIGNANT_CHERCHEUR');
  grades = computed(() => [...new Set(this.tous().map(c => c.grade).filter((g): g is string => !!g))].sort());
  filtres = computed(() => {
    const q = this.q().toLowerCase().trim();
    return this.tous().filter(c =>
      (!q || `${c.nom} ${c.prenom} ${c.specialite ?? ''} ${c.grade ?? ''}`.toLowerCase().includes(q)) &&
      (this.axe() === null || c.domaine?.id === this.axe()) && (!this.grade() || c.grade === this.grade()) &&
      (!this.statut() || (c.statutMembre ?? 'ACTIF') === this.statut()) && (!this.cat() || c.categorie === this.cat()));
  });

  constructor() {
    inject(SeoService).definir('Chercheurs', 'Les chercheurs permanents du laboratoire LIMTIC, leurs grades, spécialités et axes de recherche.');
    this.svc.getAll().subscribe(c => this.tous.set(c));
    this.dsvc.getAll().subscribe(d => this.axes.set(d));
  }
  ngOnInit() { const a = Number(this.axeParam()); if (a) this.axe.set(a); }
}
