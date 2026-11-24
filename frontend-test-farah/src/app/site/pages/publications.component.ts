import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { SiteService } from '../../core/services/site.service';
import { ChercheurService } from '../../core/services/chercheur.service';
import { DomaineService } from '../../core/services/domaine.service';
import { Chercheur, Domaine, Publication, TYPES_PUBLICATION, TypePublication } from '../../core/models/chercheur.model';
import { FiltresPublications, PagePublications } from '../../core/models/site.model';
import { ClassementBadgeComponent } from '../../shared/classement-badge.component';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Page publications (§3.7.3) : pagination serveur, recherche plein texte, tri, filtres, badges, export BibTeX/CSV. */
@Component({
  selector: 'app-site-publications',
  imports: [FormsModule, RouterLink, ClassementBadgeComponent],
  template: `
    <div class="site-hero-mini"><div class="site-wrap">
      <h1>Publications</h1><p>Articles de journaux, conférences, chapitres et rapports des membres du laboratoire, avec le classement de leur venue.</p>
    </div></div>
    <div class="site-wrap site-page">
      <div class="filters" role="search">
        <div class="search-input"><span class="mi">search</span>
          <input class="form-control" type="search" placeholder="Titre, auteur, mot-clé…" aria-label="Recherche plein texte" [ngModel]="f().q" (ngModelChange)="saisie$.next($event)" /></div>
        <select class="form-control" aria-label="Type" [ngModel]="f().type ?? ''" (ngModelChange)="maj({ type: $event })">
          <option value="">Tous les types</option>
          @for (t of typesListe; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }
        </select>
        <select class="form-control" aria-label="Année" [ngModel]="f().annee ?? null" (ngModelChange)="maj({ annee: $event })">
          <option [ngValue]="null">Toutes les années</option>
          @for (a of page()?.annees ?? []; track a) { <option [ngValue]="a">{{ a }}</option> }
        </select>
        <select class="form-control" aria-label="Auteur" [ngModel]="f().auteur ?? null" (ngModelChange)="maj({ auteur: $event })">
          <option [ngValue]="null">Tous les auteurs</option>
          @for (c of chercheurs(); track c.id) { <option [ngValue]="c.id">{{ c.nom }} {{ c.prenom }}</option> }
        </select>
        <select class="form-control" aria-label="Axe" [ngModel]="f().axe ?? null" (ngModelChange)="maj({ axe: $event })">
          <option [ngValue]="null">Tous les axes</option>
          @for (d of axes(); track d.id) { <option [ngValue]="d.id">{{ d.nom }}</option> }
        </select>
        <select class="form-control" aria-label="Classement" [ngModel]="f().classement ?? ''" (ngModelChange)="maj({ classement: $event })">
          <option value="">Tous les classements</option>
          @for (c of ['Q1', 'Q2', 'Q3', 'Q4', 'A*', 'A', 'B', 'C']; track c) { <option [value]="c">{{ c }}</option> }
        </select>
        <select class="form-control" aria-label="Trier par" [ngModel]="f().tri ?? 'annee'" (ngModelChange)="maj({ tri: $event })">
          <option value="annee">Année (récentes d'abord)</option><option value="annee_asc">Année (anciennes d'abord)</option>
          <option value="titre">Titre</option><option value="type">Type</option><option value="citations">Citations</option>
        </select>
      </div>
      <div class="row mb-16" style="justify-content:space-between">
        <span class="small muted">{{ page()?.total ?? 0 }} publication(s)</span>
        <div class="row">
          <a class="btn btn-ghost btn-sm" [href]="exportUrl('bibtex')" download><span class="mi sm">download</span> BibTeX</a>
          <a class="btn btn-ghost btn-sm" [href]="exportUrl('csv')" download><span class="mi sm">table_view</span> CSV</a>
        </div>
      </div>
      @if (erreur()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div> }
      <div class="card card-body" [attr.aria-busy]="charge()">
        @for (p of page()?.contenu ?? []; track p.id) {
          <article class="pub-item">
            <div class="pi-title"><a [routerLink]="['/publications', p.id]">{{ p.titre }}</a></div>
            <div class="pi-authors">{{ p.auteursTexte || auteurs(p) }}</div>
            @if (p.venue) { <div class="pi-venue">{{ p.venue }}@if (p.volume) {, vol. {{ p.volume }}}@if (p.numero) {({{ p.numero }})}@if (p.pages) {, p. {{ p.pages }}}</div> }
            <div class="pi-meta">
              <span class="chip chip-blue">{{ p.annee }}</span>
              @if (p.type) { <span class="chip">{{ types[p.type] }}</span> }
              <app-classement [classement]="p.classement" [source]="p.sourceClassement" [facteurImpact]="p.facteurImpact" />
              @if (p.citations) { <span class="small muted">Cité {{ p.citations }} fois</span> }
              @if (p.doi) { <a class="chip chip-cyan" [href]="'https://doi.org/' + p.doi" target="_blank" rel="noopener">DOI</a> }
              @if (p.pdfFichierUrl) { <a class="chip chip-red" [href]="site.url(p.pdfFichierUrl)" target="_blank" rel="noopener">PDF</a> }
            </div>
          </article>
        } @empty { <p class="muted">@if (charge()) { Chargement… } @else { Aucune publication ne correspond à la recherche. }</p> }
      </div>
      @if ((page()?.pages ?? 1) > 1) {
        <nav class="pager" aria-label="Pagination">
          <button class="btn btn-ghost btn-sm" [disabled]="(f().page ?? 0) === 0" (click)="aller((f().page ?? 0) - 1)" aria-label="Page précédente"><span class="mi sm">chevron_left</span></button>
          @for (n of numeros(); track n) {
            <button class="btn btn-ghost btn-sm" [class.on]="n === (f().page ?? 0)" [attr.aria-current]="n === (f().page ?? 0) ? 'page' : null" (click)="aller(n)">{{ n + 1 }}</button>
          }
          <button class="btn btn-ghost btn-sm" [disabled]="(f().page ?? 0) >= (page()?.pages ?? 1) - 1" (click)="aller((f().page ?? 0) + 1)" aria-label="Page suivante"><span class="mi sm">chevron_right</span></button>
        </nav>
      }
    </div>
  `,
})
export class SitePublicationsComponent {
  site = inject(SiteService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  types = TYPES_PUBLICATION;
  typesListe = Object.entries(TYPES_PUBLICATION) as [TypePublication, string][];
  f = signal<FiltresPublications>({ tri: 'annee', page: 0, taille: 15 });
  page = signal<PagePublications | null>(null);
  chercheurs = signal<Chercheur[]>([]);
  axes = signal<Domaine[]>([]);
  charge = signal(false);
  erreur = signal('');
  saisie$ = new Subject<string>();
  numeros = computed(() => Array.from({ length: this.page()?.pages ?? 0 }, (_, i) => i));

  constructor() {
    inject(SeoService).definir('Publications', 'Publications scientifiques du laboratoire LIMTIC : journaux, conférences, classements Scimago et CORE.');
    const qp = this.route.snapshot.queryParamMap;
    const n = (k: string) => qp.get(k) ? Number(qp.get(k)) : null;
    this.f.set({ q: qp.get('q') ?? '', type: (qp.get('type') ?? '') as TypePublication | '', annee: n('annee'), auteur: n('auteur'), axe: n('axe'), classement: qp.get('classement') ?? '',
      tri: qp.get('tri') ?? 'annee', page: n('page') ?? 0, taille: 15 });
    inject(ChercheurService).getAll().subscribe(c => this.chercheurs.set(c.filter(x => x.nbPublications > 0).sort((a, b) => a.nom.localeCompare(b.nom))));
    inject(DomaineService).getAll().subscribe(d => this.axes.set(d));
    this.saisie$.pipe(debounceTime(300)).subscribe(q => this.maj({ q }));
    effect(() => { const f = this.f(); untracked(() => this.charger(f)); });
  }

  private premier = true;
  private charger(f: FiltresPublications) {
    this.charge.set(true);
    this.site.publications(f).subscribe({
      next: p => { this.page.set(p); this.charge.set(false); this.erreur.set(''); },
      error: e => { this.charge.set(false); this.erreur.set(messageErreur(e)); },
    });
    if (this.premier) { this.premier = false; return; }  // l'URL d'arrivée contient déjà les filtres
    const { taille, ...qp } = f;
    this.router.navigate([], { queryParams: Object.fromEntries(Object.entries(qp).map(([k, v]) => [k, v === '' || v === 0 && k === 'page' ? null : v])), replaceUrl: true });
  }

  maj(m: Partial<FiltresPublications>) { this.f.update(f => ({ ...f, ...m, page: 0 })); }
  aller(p: number) { this.f.update(f => ({ ...f, page: p })); window.scrollTo({ top: 0, behavior: 'smooth' }); }
  exportUrl(format: 'bibtex' | 'csv') { return this.site.lienExport(format, this.f()); }
  auteurs(p: Publication) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
}
