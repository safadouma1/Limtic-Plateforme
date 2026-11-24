import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../core/services/site.service';
import { AxeDetail, AxeResume } from '../../core/models/site.model';
import { TYPES_PUBLICATION } from '../../core/models/chercheur.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { ClassementBadgeComponent } from '../../shared/classement-badge.component';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Axes de recherche du laboratoire (§3.6). */
@Component({
  selector: 'app-site-axes',
  imports: [RouterLink],
  template: `
    <div class="site-hero-mini"><div class="site-wrap">
      <h1>Axes de recherche</h1><p>Les thématiques officielles du laboratoire, leurs responsables, leurs membres et leurs publications.</p>
    </div></div>
    <div class="site-wrap site-page axes">
      @for (a of axes(); track a.id; let i = $index) {
        <a class="card card-body card-link axe" [routerLink]="['/axes', a.id]">
          <div class="num">{{ i + 1 }}</div>
          <div>
            <h2>{{ a.nom }}</h2>
            @if (a.description) { <p class="muted">{{ a.description }}</p> }
            <div class="chips" style="margin-top:10px">
              @for (r of a.responsables; track r.id) { <span class="chip chip-gold">Responsable : {{ r.prenom }} {{ r.nom }}</span> }
              <span class="chip chip-blue">{{ a.nbChercheurs }} chercheurs</span><span class="chip">{{ a.nbPublications }} publications</span>
            </div>
          </div>
        </a>
      }
    </div>
  `,
  styles: [`.axes { display:grid; gap:14px; } .axe { display:flex; gap:18px; align-items:flex-start; }
    .axe h2 { font-size:18px; margin-bottom:4px; } .num { font-family:var(--font-display); font-size:34px; color:#8cc63f; line-height:1; min-width:32px; }`],
})
export class SiteAxesComponent {
  axes = signal<AxeResume[]>([]);
  constructor() {
    inject(SeoService).definir('Axes de recherche');
    inject(SiteService).axes().subscribe(a => this.axes.set(a));
  }
}

@Component({
  selector: 'app-site-axe',
  imports: [RouterLink, DatePipe, AvatarComponent, ClassementBadgeComponent],
  template: `
    @if (d(); as d) {
      <div class="site-hero-mini"><div class="site-wrap">
        <div class="crumbs"><a routerLink="/axes">Axes de recherche</a> › <span>{{ d.axe.nom }}</span></div>
        <h1>{{ d.axe.nom }}</h1>
        @if (d.axe.description) { <p>{{ d.axe.description }}</p> }
        @if (d.axe.responsables.length) { <p class="small">Responsable(s) :
          @for (r of d.axe.responsables; track r.id; let last = $last) { <a [routerLink]="['/chercheurs', r.id]" style="color:var(--accent)">{{ r.prenom }} {{ r.nom }}</a>@if (!last) {, } }</p> }
      </div></div>
      <div class="site-wrap site-page">
        <h2 class="h2">Chercheurs membres ({{ d.membres.length }})</h2>
        <div class="person-grid mb-24">
          @for (c of d.membres; track c.id) {
            <a class="person-card" [routerLink]="['/chercheurs', c.id]"><app-avatar [photo]="c.photoUrl" [nom]="c.prenom + ' ' + c.nom" />
              <div><div class="pc-name">{{ c.prenom }} {{ c.nom }}</div><div class="pc-sub">{{ c.grade }}</div></div></a>
          } @empty { <p class="muted small">Aucun membre rattaché.</p> }
        </div>
        @if (d.projets.length) {
          <h2 class="h2">Projets de recherche associés</h2>
          <div class="card mb-24"><div class="table-wrap"><table class="data-table"><thead><tr><th>Projet</th><th>Type</th><th>Financement</th><th>Période</th></tr></thead><tbody>
            @for (p of d.projets; track p.id) { <tr><td>{{ p.intitule }}</td><td>{{ p.type }}</td><td class="small">{{ p.financeur }}</td><td>{{ p.dateDebut | date:'y' }} – {{ p.dateFin | date:'y' }}</td></tr> }
          </tbody></table></div></div>
        }
        <div class="row" style="justify-content:space-between"><h2 class="h2">Publications ({{ d.publications.length }})</h2>
          <a class="btn btn-ghost btn-sm" routerLink="/publications" [queryParams]="{ axe: d.axe.id }">Rechercher dans cet axe</a></div>
        <div class="card card-body">
          @for (p of d.publications.slice(0, 20); track p.id) {
            <div class="pub-item"><div class="pi-title"><a [routerLink]="['/publications', p.id]">{{ p.titre }}</a></div>
              <div class="pi-venue">{{ p.venue }}</div>
              <div class="pi-meta"><span class="chip chip-blue">{{ p.annee }}</span>@if (p.type) { <span class="chip">{{ types[p.type] }}</span> }
                <app-classement [classement]="p.classement" [source]="p.sourceClassement" [facteurImpact]="p.facteurImpact" /></div></div>
          } @empty { <p class="muted small">Aucune publication.</p> }
        </div>
      </div>
    } @else if (erreur()) { <div class="site-wrap site-page"><div class="alert alert-error">{{ erreur() }}</div></div> }
  `,
  styles: ['.h2 { font-size:18px; margin:8px 0 12px; }'],
})
export class SiteAxeComponent {
  private svc = inject(SiteService);
  private seo = inject(SeoService);
  id = input.required<string>();
  d = signal<AxeDetail | null>(null);
  erreur = signal('');
  types = TYPES_PUBLICATION;
  constructor() {
    effect(() => { const id = Number(this.id()); untracked(() => this.svc.axe(id).subscribe({
      next: d => { this.d.set(d); this.seo.definir(d.axe.nom, d.axe.description); }, error: e => this.erreur.set(messageErreur(e)) })); });
  }
}
