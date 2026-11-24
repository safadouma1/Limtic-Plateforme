import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../core/services/site.service';
import { Etudiant } from '../../core/models/site.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { SeoService } from '../../shared/seo.service';

/** Doctorants (§3.4) et étudiants en mastère (§3.5) : liste filtrable par encadrant, année / promotion et statut. */
@Component({
  selector: 'app-site-etudiants',
  imports: [FormsModule, RouterLink, DatePipe, AvatarComponent],
  template: `
    <div class="site-hero-mini"><div class="site-wrap">
      <h1>{{ doc() ? 'Doctorants' : 'Étudiants en mastère' }}</h1>
      <p>{{ doc() ? 'Thèses en cours et soutenues sous la direction des membres du laboratoire.' : 'Mémoires de mastère de recherche encadrés par les membres du laboratoire.' }}</p>
    </div></div>
    <div class="site-wrap site-page">
      <div class="filters" role="search">
        <div class="search-input"><span class="mi">search</span>
          <input class="form-control" type="search" placeholder="Nom ou sujet…" aria-label="Rechercher" [ngModel]="q()" (ngModelChange)="q.set($event)" /></div>
        <select class="form-control" [attr.aria-label]="doc() ? 'Directeur de thèse' : 'Encadrant'" [ngModel]="enc()" (ngModelChange)="enc.set($event)">
          <option [ngValue]="null">{{ doc() ? 'Tous les directeurs' : 'Tous les encadrants' }}</option>
          @for (e of encadrants(); track e.id) { <option [ngValue]="e.id">{{ e.nom }} {{ e.prenom }}</option> }
        </select>
        <select class="form-control" [attr.aria-label]="doc() ? 'Année d\\'inscription' : 'Promotion'" [ngModel]="an()" (ngModelChange)="an.set($event)">
          <option value="">{{ doc() ? 'Toutes les années' : 'Toutes les promotions' }}</option>
          @for (a of annees(); track a) { <option [value]="a">{{ a }}</option> }
        </select>
        <select class="form-control" aria-label="Statut" [ngModel]="st()" (ngModelChange)="st.set($event)">
          <option value="">Tous les statuts</option><option value="EN_COURS">En cours</option>
          <option [value]="doc() ? 'SOUTENU' : 'DIPLOME'">{{ doc() ? 'Soutenus' : 'Diplômés' }}</option>
        </select>
      </div>
      <p class="small muted mb-16">{{ filtres().length }} {{ doc() ? 'doctorant(s)' : 'étudiant(s)' }}</p>
      <div class="liste">
        @for (x of filtres(); track x.id) {
          <article class="card card-body et">
            <app-avatar [photo]="x.photoUrl" [nom]="x.prenom + ' ' + x.nom" />
            <div style="min-width:0;flex:1">
              <div class="row" style="justify-content:space-between;align-items:flex-start">
                <div><div class="pc-name" style="font-weight:600;font-size:15px">{{ x.prenom }} {{ x.nom }}</div>
                  <div class="small muted">@if (doc() ? x.anneeInscription : x.promotion) { {{ doc() ? 'Inscrit(e) en ' + x.anneeInscription : 'Promotion ' + x.promotion }} · }{{ x.etablissement }}</div></div>
                <span class="chip" [class.chip-gold]="x.statut === 'EN_COURS'" [class.chip-green]="x.statut !== 'EN_COURS'">{{ libelle(x.statut) }}</span>
              </div>
              @if (x.sujet) { <p class="sujet">{{ x.sujet }}</p> }
              @if (x.encadrants.length || x.encadrantsTexte) {
                <div class="small">{{ doc() ? 'Direction' : 'Encadrement' }} :
                  @for (e of x.encadrants; track e.id; let last = $last) { <a [routerLink]="['/chercheurs', e.id]" style="color:var(--accent)">{{ e.prenom }} {{ e.nom }}</a>@if (!last) {, } }
                  @if (!x.encadrants.length) { {{ x.encadrantsTexte }} }</div>
              }
              @if (x.dateSoutenance) { <div class="small muted" style="margin-top:4px">Soutenu le {{ x.dateSoutenance | date:'d MMMM y' }}@if (x.mention) { · mention {{ x.mention }} }</div> }
            </div>
          </article>
        } @empty { <p class="muted">Aucun résultat.</p> }
      </div>
    </div>
  `,
  styles: [`.liste { display:grid; grid-template-columns:repeat(auto-fill,minmax(420px,1fr)); gap:12px; } .et { display:flex; gap:14px; }
    .sujet { color:var(--text-soft); margin:8px 0; font-size:13.5px; line-height:1.55; }
    @media (max-width:560px) { .liste { grid-template-columns:1fr; } }`],
})
export class SiteEtudiantsComponent {
  private site = inject(SiteService);
  private seo = inject(SeoService);
  /** Donnée de route : 'doctorants' ou 'masteriens'. */
  type = input<string>('doctorants');
  doc = computed(() => this.type() === 'doctorants');
  tous = signal<Etudiant[]>([]);
  q = signal(''); enc = signal<number | null>(null); an = signal(''); st = signal('');
  encadrants = computed(() => {
    const m = new Map<number, { id: number; nom: string; prenom: string }>();
    for (const x of this.tous()) for (const e of x.encadrants) m.set(e.id, e);
    return [...m.values()].sort((a, b) => a.nom.localeCompare(b.nom));
  });
  annees = computed(() => [...new Set(this.tous().map(x => this.doc() ? String(x.anneeInscription ?? '') : x.promotion ?? '').filter(Boolean))].sort().reverse());
  filtres = computed(() => {
    const q = this.q().toLowerCase().trim();
    return this.tous().filter(x => (!q || `${x.nom} ${x.prenom} ${x.sujet ?? ''}`.toLowerCase().includes(q))
      && (this.enc() === null || x.encadrants.some(e => e.id === this.enc()))
      && (!this.an() || (this.doc() ? String(x.anneeInscription) : x.promotion) === this.an())
      && (!this.st() || x.statut === this.st()));
  });
  ngOnInit() {
    this.seo.definir(this.doc() ? 'Doctorants' : 'Étudiants en mastère');
    (this.doc() ? this.site.doctorants() : this.site.masteriens()).subscribe(l => this.tous.set(l));
  }
  libelle(s: string) { return ({ EN_COURS: 'En cours', SOUTENU: 'Soutenu', DIPLOME: 'Diplômé', ABANDON: 'Abandon' } as Record<string, string>)[s] ?? s; }
}
