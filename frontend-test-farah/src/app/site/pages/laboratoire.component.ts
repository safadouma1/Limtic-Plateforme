import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../core/services/site.service';
import { Directeur } from '../../core/models/site.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { ClassementBadgeComponent } from '../../shared/classement-badge.component';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Page du directeur du laboratoire (§3.2) : photo, titre, coordonnées, mot du directeur, profils, publications récentes. */
@Component({
  selector: 'app-site-laboratoire',
  imports: [RouterLink, AvatarComponent, ClassementBadgeComponent],
  template: `
    <div class="site-hero-mini"><div class="site-wrap">
      <h1>Le laboratoire</h1><p>{{ site.site()?.nomComplet }}</p>
    </div></div>
    <div class="site-wrap site-page">
      @if (erreur()) { <div class="alert alert-info"><span class="mi sm">info</span> {{ erreur() }}</div> }
      @if (d(); as d) {
        <div class="dir">
          <div class="card card-body carte">
            <app-avatar [photo]="d.chercheur.photoUrl" [nom]="d.chercheur.prenom + ' ' + d.chercheur.nom" [grand]="true" />
            <div style="font-weight:700;font-size:18px;margin-top:12px">{{ d.chercheur.prenom }} {{ d.chercheur.nom }}</div>
            <div class="muted">{{ d.titre }}</div>
            <div class="chip chip-gold" style="margin:10px 0">{{ d.fonction ?? 'Directeur du laboratoire' }}</div>
            @if (d.email) { <div class="small"><span class="mi sm">mail</span> <a [href]="'mailto:' + d.email">{{ d.email }}</a></div> }
            @if (d.telephone) { <div class="small"><span class="mi sm">call</span> {{ d.telephone }}</div> }
            <div class="ext-links" style="margin-top:14px;justify-content:center">
              @if (d.chercheur.googleScholarUrl) { <a [href]="d.chercheur.googleScholarUrl" target="_blank" rel="noopener">Google Scholar</a> }
              @if (d.chercheur.researchGateUrl) { <a [href]="d.chercheur.researchGateUrl" target="_blank" rel="noopener">ResearchGate</a> }
              @if (d.chercheur.orcid) { <a [href]="'https://orcid.org/' + d.chercheur.orcid" target="_blank" rel="noopener">ORCID</a> }
              <a [routerLink]="['/chercheurs', d.chercheur.id]">Page personnelle</a>
            </div>
          </div>
          <div class="stack">
            <div class="card card-body">
              <div class="card-title mb-16">Mot du directeur</div>
              @if (d.motDirecteur) { <div class="rich" [innerHTML]="d.motDirecteur"></div> }
              @else { <p class="rich">{{ site.site()?.presentation }}</p> }
            </div>
            @if (d.axesPrioritaires || d.chercheur.domaine) {
              <div class="card card-body"><div class="card-title mb-16">Axes de recherche prioritaires</div>
                @if (d.axesPrioritaires) { <p class="rich" style="white-space:pre-line">{{ d.axesPrioritaires }}</p> }
                @else { <a class="chip chip-blue" [routerLink]="['/axes', d.chercheur.domaine!.id]">{{ d.chercheur.domaine!.nom }}</a> }
              </div>
            }
            <div class="card card-body"><div class="card-title mb-16">Publications récentes</div>
              @for (p of d.publicationsRecentes; track p.id) {
                <div class="pub-item"><div class="pi-title"><a [routerLink]="['/publications', p.id]">{{ p.titre }}</a></div>
                  <div class="pi-venue">{{ p.venue }}</div>
                  <div class="pi-meta"><span class="chip chip-blue">{{ p.annee }}</span><app-classement [classement]="p.classement" [source]="p.sourceClassement" [facteurImpact]="p.facteurImpact" /></div></div>
              } @empty { <p class="muted small">Aucune publication.</p> }
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`.dir { display:grid; grid-template-columns:300px 1fr; gap:20px; align-items:start; }
    .carte { text-align:center; display:flex; flex-direction:column; align-items:center; position:sticky; top:84px; }
    .carte .small { display:flex; gap:6px; align-items:center; justify-content:center; margin-top:4px; }
    @media (max-width: 860px) { .dir { grid-template-columns:1fr; } .carte { position:static; } }`],
})
export class SiteLaboratoireComponent {
  site = inject(SiteService);
  d = signal<Directeur | null>(null);
  erreur = signal('');
  constructor() {
    inject(SeoService).definir('Le laboratoire et son directeur');
    this.site.directeur().subscribe({ next: d => this.d.set(d), error: e => this.erreur.set(messageErreur(e)) });
  }
}
