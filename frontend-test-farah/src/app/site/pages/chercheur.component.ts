import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ChercheurService } from '../../core/services/chercheur.service';
import { SiteService } from '../../core/services/site.service';
import { AuthService } from '../../core/services/auth.service';
import { Chercheur, Publication, TYPES_PUBLICATION, TypePublication } from '../../core/models/chercheur.model';
import { Encadrements } from '../../core/models/site.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { ClassementBadgeComponent } from '../../shared/classement-badge.component';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Page personnelle d'un chercheur (§3.3.2) : biographie, onglets Publications (type Google Scholar) et Encadrements. */
@Component({
  selector: 'app-site-chercheur',
  imports: [FormsModule, RouterLink, AvatarComponent, ClassementBadgeComponent],
  template: `
    @if (c(); as c) {
      <div class="site-hero-mini"><div class="site-wrap prof">
        <app-avatar [photo]="c.photoUrl" [nom]="c.prenom + ' ' + c.nom" [grand]="true" />
        <div style="min-width:0">
          <div class="crumbs"><a routerLink="/chercheurs">Chercheurs</a> › <span>{{ c.prenom }} {{ c.nom }}</span></div>
          <h1>{{ c.prenom }} {{ c.nom }}</h1>
          <p>{{ c.grade }}@if (c.specialite) { · {{ c.specialite }} }</p>
          <div class="infos small">
            @if (c.etablissement) { <span><span class="mi sm">apartment</span> {{ c.etablissement }}</span> }
            @if (c.bureau) { <span><span class="mi sm">meeting_room</span> Bureau {{ c.bureau }}</span> }
            @if (c.email) { <span><span class="mi sm">mail</span> <a [href]="'mailto:' + c.email">{{ c.email }}</a></span> }
            @if (c.telephone) { <span><span class="mi sm">call</span> {{ c.telephone }}</span> }
          </div>
          <div class="ext-links" style="margin-top:12px">
            @if (c.googleScholarUrl) { <a [href]="c.googleScholarUrl" target="_blank" rel="noopener"><span class="mi sm">school</span> Google Scholar</a> }
            @if (c.researchGateUrl) { <a [href]="c.researchGateUrl" target="_blank" rel="noopener"><span class="mi sm">science</span> ResearchGate</a> }
            @if (c.orcid) { <a [href]="'https://orcid.org/' + c.orcid" target="_blank" rel="noopener"><span class="mi sm">badge</span> ORCID {{ c.orcid }}</a> }
            @if (c.linkedinUrl) { <a [href]="c.linkedinUrl" target="_blank" rel="noopener"><span class="mi sm">work</span> LinkedIn</a> }
            @if (estMoi()) { <a routerLink="/utilisateur/ma-fiche"><span class="mi sm">edit</span> Modifier ma page</a> }
          </div>
        </div>
      </div></div>

      <div class="site-wrap site-page">
        @if (c.biographie || c.domaine) {
          <div class="grid-bio mb-24">
            @if (c.biographie) { <div class="card card-body"><div class="card-title mb-16">Biographie</div><p class="bio">{{ c.biographie }}</p></div> }
            @if (c.domaine) {
              <div class="card card-body"><div class="card-title mb-16">Axe de recherche</div>
                <a [routerLink]="['/axes', c.domaine.id]" class="chip chip-blue">{{ c.domaine.nom }}</a>
                @if (motsCles().length) {
                  <div class="small muted" style="margin:14px 0 6px">Thématiques fréquentes</div>
                  <div class="chips">@for (m of motsCles(); track m) { <span class="chip chip-purple">{{ m }}</span> }</div>
                }
              </div>
            }
          </div>
        }

        <div class="tabs" role="tablist">
          <button role="tab" [class.on]="onglet() === 'pubs'" [attr.aria-selected]="onglet() === 'pubs'" (click)="onglet.set('pubs')">Publications ({{ pubs().length }})</button>
          <button role="tab" [class.on]="onglet() === 'enc'" [attr.aria-selected]="onglet() === 'enc'" (click)="onglet.set('enc')">Encadrements ({{ (enc()?.doctorants?.length ?? 0) + (enc()?.masteriens?.length ?? 0) }})</button>
        </div>

        @if (onglet() === 'pubs') {
          <div class="filters">
            <select class="form-control" aria-label="Type" [ngModel]="type()" (ngModelChange)="type.set($event)">
              <option value="">Tous les types</option>
              @for (t of typesListe; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }
            </select>
            <select class="form-control" aria-label="Année" [ngModel]="annee()" (ngModelChange)="annee.set($event)">
              <option [ngValue]="null">Toutes les années</option>
              @for (a of annees(); track a) { <option [ngValue]="a">{{ a }}</option> }
            </select>
            <select class="form-control" aria-label="Tri" [ngModel]="tri()" (ngModelChange)="tri.set($event)">
              <option value="annee">Plus récentes</option><option value="citations">Plus citées</option><option value="titre">Titre</option>
            </select>
          </div>
          <div class="card card-body">
            <div class="scholar-head small muted"><span>Titre</span><span>Citée par</span><span>Année</span></div>
            @for (p of pubsFiltrees(); track p.id) {
              <div class="pub-item scholar">
                <div>
                  <div class="pi-title"><a [routerLink]="['/publications', p.id]">{{ p.titre }}</a></div>
                  <div class="pi-authors">{{ p.auteursTexte || auteurs(p) }}</div>
                  @if (p.venue) { <div class="pi-venue">{{ p.venue }}</div> }
                  <div class="pi-meta">
                    @if (p.type) { <span class="chip">{{ types[p.type] }}</span> }
                    <app-classement [classement]="p.classement" [source]="p.sourceClassement" [facteurImpact]="p.facteurImpact" />
                    @if (p.doi) { <a class="chip chip-cyan" [href]="'https://doi.org/' + p.doi" target="_blank" rel="noopener">DOI</a> }
                    @if (p.pdfFichierUrl) { <a class="chip chip-red" [href]="site.url(p.pdfFichierUrl)" target="_blank" rel="noopener">PDF</a> }
                  </div>
                </div>
                <div class="num">{{ p.citations ?? '' }}</div>
                <div class="num">{{ p.annee }}</div>
              </div>
            } @empty { <p class="muted">Aucune publication pour ces filtres.</p> }
          </div>
        } @else {
          @if (enc(); as e) {
            @for (g of [{ t: 'Doctorants', l: e.doctorants }, { t: 'Étudiants en mastère', l: e.masteriens }]; track g.t) {
              <h2 class="h2">{{ g.t }} ({{ g.l.length }})</h2>
              <div class="card mb-24"><div class="table-wrap"><table class="data-table">
                <thead><tr><th>Nom</th><th>Sujet</th><th>Inscription</th><th>Statut</th></tr></thead>
                <tbody>
                  @for (x of g.l; track x.id) {
                    <tr><td style="white-space:nowrap">{{ x.nom }} {{ x.prenom }}</td><td class="small">{{ x.sujet }}</td><td>{{ x.promotion ?? x.anneeInscription }}</td>
                      <td><span class="chip" [class.chip-green]="x.statut !== 'EN_COURS'" [class.chip-gold]="x.statut === 'EN_COURS'">{{ statut(x.statut) }}</span></td></tr>
                  } @empty { <tr><td colspan="4" class="muted small">Aucun.</td></tr> }
                </tbody>
              </table></div></div>
            }
          }
        }
      </div>
    } @else if (erreur()) {
      <div class="site-wrap site-page"><div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div></div>
    } @else { <div class="site-wrap site-page"><div class="skeleton" style="height:260px;border-radius:14px"></div></div> }
  `,
  styles: [`
    .prof { display:flex; gap:24px; align-items:center; }
    .infos { display:flex; flex-wrap:wrap; gap:6px 18px; margin-top:10px; color:var(--text-soft); }
    .infos > span { display:inline-flex; gap:5px; align-items:center; }
    .grid-bio { display:grid; grid-template-columns:2fr 1fr; gap:16px; }
    .bio { white-space:pre-line; color:var(--text-soft); line-height:1.75; }
    .scholar-head, .scholar { display:grid; grid-template-columns:1fr 80px 60px; gap:12px; }
    .scholar-head { padding-bottom:8px; border-bottom:1px solid var(--border); text-transform:uppercase; font-size:11px; letter-spacing:.6px; }
    .num { text-align:right; color:var(--muted); font-variant-numeric:tabular-nums; }
    .h2 { font-size:17px; margin:6px 0 10px; }
    @media (max-width: 760px) { .prof { flex-direction:column; align-items:flex-start; } .grid-bio { grid-template-columns:1fr; }
      .scholar-head, .scholar { grid-template-columns:1fr 44px; } .scholar > .num:nth-of-type(2), .scholar-head span:nth-child(2) { display:none; } }
  `],
})
export class SiteChercheurComponent {
  private svc = inject(ChercheurService);
  site = inject(SiteService);
  private auth = inject(AuthService);
  private seo = inject(SeoService);
  id = input.required<string>();
  c = signal<Chercheur | null>(null);
  pubs = signal<Publication[]>([]);
  enc = signal<Encadrements | null>(null);
  erreur = signal('');
  onglet = signal<'pubs' | 'enc'>('pubs');
  type = signal(''); annee = signal<number | null>(null); tri = signal('annee');
  types = TYPES_PUBLICATION;
  typesListe = Object.entries(TYPES_PUBLICATION) as [TypePublication, string][];
  annees = computed(() => [...new Set(this.pubs().map(p => p.annee).filter((a): a is number => !!a))].sort((a, b) => b - a));
  motsCles = computed(() => {
    const n = new Map<string, number>();
    for (const p of this.pubs()) for (const m of p.motsCles) n.set(m, (n.get(m) ?? 0) + 1);
    return [...n.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(e => e[0]);
  });
  pubsFiltrees = computed(() => {
    const l = this.pubs().filter(p => (!this.type() || p.type === this.type()) && (this.annee() === null || p.annee === this.annee()));
    const t = this.tri();
    return [...l].sort((a, b) => t === 'titre' ? a.titre.localeCompare(b.titre) : t === 'citations' ? (b.citations ?? -1) - (a.citations ?? -1) : (b.annee ?? 0) - (a.annee ?? 0));
  });
  estMoi = computed(() => this.auth.currentUser()?.chercheurId === this.c()?.id);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => {
        this.c.set(null); this.erreur.set('');
        forkJoin({ c: this.svc.getById(id), p: this.svc.publications(id), e: this.site.encadrements(id) }).subscribe({
          next: r => { this.c.set(r.c); this.pubs.set(r.p); this.enc.set(r.e);
            this.seo.definir(`${r.c.prenom} ${r.c.nom}`, `${r.c.grade ?? ''} ${r.c.specialite ?? ''} – page personnelle au laboratoire LIMTIC.`); },
          error: e => this.erreur.set(messageErreur(e)),
        });
      });
    });
  }
  auteurs(p: Publication) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
  statut(s: string) { return ({ EN_COURS: 'En cours', SOUTENU: 'Soutenu', DIPLOME: 'Diplômé', ABANDON: 'Abandon' } as Record<string, string>)[s] ?? s; }
}
