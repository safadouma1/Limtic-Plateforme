import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicationService } from '../../core/services/publication.service';
import { SiteService } from '../../core/services/site.service';
import { AuthService } from '../../core/services/auth.service';
import { Publication, TYPES_PUBLICATION } from '../../core/models/chercheur.model';
import { ClassementBadgeComponent } from '../../shared/classement-badge.component';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Fiche publique d'une publication. */
@Component({
  selector: 'app-site-publication',
  imports: [RouterLink, ClassementBadgeComponent],
  template: `
    @if (p(); as p) {
      <div class="site-hero-mini"><div class="site-wrap">
        <div class="crumbs"><a routerLink="/publications">Publications</a> › <span>{{ p.annee }}</span></div>
        <h1 style="font-size:clamp(24px,3.4vw,34px)">{{ p.titre }}</h1>
        <p>{{ p.auteursTexte || auteurs(p) }}</p>
      </div></div>
      <div class="site-wrap site-page grid-main-side">
        <div class="stack">
          @if (p.resume) { <div class="card card-body"><div class="card-title mb-16">Résumé</div><p class="rich" style="white-space:pre-line">{{ p.resume }}</p></div> }
          @if (p.motsCles.length) { <div class="card card-body"><div class="card-title mb-16">Mots-clés</div>
            <div class="chips">@for (m of p.motsCles; track m) { <a class="chip chip-purple" [routerLink]="'/publications'" [queryParams]="{ q: m }">{{ m }}</a> }</div></div> }
          @if (p.chercheurs.length) { <div class="card card-body"><div class="card-title mb-16">Auteurs membres du laboratoire</div>
            <div class="chips">@for (c of p.chercheurs; track c.id) { <a class="chip chip-blue" [routerLink]="['/chercheurs', c.id]">{{ c.prenom }} {{ c.nom }}</a> }</div></div> }
        </div>
        <aside class="card card-body meta">
          <dl>
            @if (p.type) { <dt>Type</dt><dd>{{ types[p.type] }}</dd> }
            @if (p.venue) { <dt>{{ p.type === 'ARTICLE_JOURNAL' ? 'Journal' : p.type === 'CHAPITRE' ? 'Ouvrage' : 'Venue' }}</dt><dd>{{ p.venue }}</dd> }
            @if (p.volume || p.numero || p.pages) { <dt>Volume / numéro / pages</dt><dd>{{ p.volume ?? '—' }} / {{ p.numero ?? '—' }} / {{ p.pages ?? '—' }}</dd> }
            @if (p.lieu || p.pays) { <dt>Lieu</dt><dd>{{ p.lieu }}@if (p.pays) {, {{ p.pays }}}</dd> }
            @if (p.editeur) { <dt>Éditeur</dt><dd>{{ p.editeur }}</dd> }
            @if (p.isbn) { <dt>ISBN</dt><dd>{{ p.isbn }}</dd> }
            @if (p.institution) { <dt>Institution</dt><dd>{{ p.institution }}</dd> }
            @if (p.comiteOrganisation) { <dt>Comité d'organisation</dt><dd>{{ p.comiteOrganisation }}</dd> }
            <dt>Année</dt><dd>{{ p.annee ?? '—' }}</dd>
            @if (p.classement || p.facteurImpact) { <dt>Classement</dt><dd><app-classement [classement]="p.classement" [source]="p.sourceClassement" [facteurImpact]="p.facteurImpact" />
              @if (p.sourceClassement) { <span class="small muted"> source : {{ p.sourceClassement }}</span> }</dd> }
            @if (p.snip) { <dt>SNIP</dt><dd>{{ p.snip }}</dd> }
            @if (p.citations !== null && p.citations !== undefined) { <dt>Citations</dt><dd>{{ p.citations }}</dd> }
            @if (p.indexation) { <dt>Indexation</dt><dd>{{ p.indexation }}</dd> }
            @if (p.domaine) { <dt>Axe</dt><dd><a [routerLink]="['/axes', p.domaine.id]">{{ p.domaine.nom }}</a></dd> }
          </dl>
          <div class="stack" style="gap:8px;margin-top:14px">
            @if (p.pdfFichierUrl) { <a class="btn btn-primary" [href]="site.url(p.pdfFichierUrl)" target="_blank" rel="noopener"><span class="mi sm">picture_as_pdf</span> Lire le PDF</a> }
            @if (p.doi) { <a class="btn btn-ghost" [href]="'https://doi.org/' + p.doi" target="_blank" rel="noopener"><span class="mi sm">link</span> DOI : {{ p.doi }}</a> }
            @if (p.pdfUrl) { <a class="btn btn-ghost" [href]="p.pdfUrl" target="_blank" rel="noopener"><span class="mi sm">open_in_new</span> Lien externe</a> }
            @if (auth.isLoggedIn()) { <a class="btn btn-ia" [routerLink]="['/utilisateur/publications', p.id]"><span class="mi sm">auto_awesome</span> Résumé IA et publications similaires</a> }
          </div>
        </aside>
      </div>
    } @else if (erreur()) { <div class="site-wrap site-page"><div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div></div> }
  `,
  styles: [`.meta dl { display:grid; grid-template-columns:1fr; gap:2px; } .meta dt { font-size:11.5px; text-transform:uppercase; letter-spacing:.5px; color:var(--muted); margin-top:10px; }
    .meta dd { color:var(--text-soft); } .meta dd a { color:var(--accent); }`],
})
export class SitePublicationComponent {
  private svc = inject(PublicationService);
  site = inject(SiteService);
  auth = inject(AuthService);
  private seo = inject(SeoService);
  id = input.required<string>();
  p = signal<Publication | null>(null);
  erreur = signal('');
  types = TYPES_PUBLICATION;
  constructor() {
    effect(() => { const id = Number(this.id()); untracked(() => this.svc.getById(id).subscribe({
      next: p => { this.p.set(p); this.seo.definir(p.titre, p.resume ?? p.titre); },
      error: e => this.erreur.set(messageErreur(e)) })); });
  }
  auteurs(p: Publication) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
}
