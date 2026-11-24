import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../core/services/site.service';
import { AuthService } from '../../core/services/auth.service';
import { Accueil, ETATS_EVENEMENT, TYPES_EVENEMENT } from '../../core/models/site.model';
import { TYPES_PUBLICATION } from '../../core/models/chercheur.model';
import { ClassementBadgeComponent } from '../../shared/classement-badge.component';
import { SeoService } from '../../shared/seo.service';

/** Page d'accueil (§3.1) : présentation, accès rapides, statistiques, fil d'actualités et agenda. */
@Component({
  selector: 'app-site-accueil',
  imports: [RouterLink, DatePipe, ClassementBadgeComponent],
  template: `
    <section class="hero">
      <div class="site-wrap hero-in">
        <div>
          <div class="hero-badge"><span class="mi sm">school</span> ISI · Université de Tunis El Manar</div>
          <h1 class="hero-title">Laboratoire <span>LIMTIC</span></h1>
          <p class="hero-sub">{{ a()?.site?.nomComplet ?? site.site()?.nomComplet }}</p>
          <p class="hero-desc">{{ a()?.site?.presentation }}</p>
          <div class="row">
            <a routerLink="/publications" class="btn btn-primary btn-lg"><span class="mi sm">article</span> Nos publications</a>
            <a routerLink="/laboratoire" class="btn btn-ghost btn-lg">Le mot du directeur</a>
          </div>
        </div>
        <div class="hero-logo"><img src="logos/limtic.png" alt="Logo du laboratoire LIMTIC" /></div>
      </div>
    </section>

    <div class="site-wrap">
      @if (erreur()) { <div class="alert alert-warn" style="margin-top:24px"><span class="mi sm">cloud_off</span> {{ erreur() }}</div> }

      @if (a(); as a) {
        <section class="kpi-row stats" aria-label="Le laboratoire en chiffres">
          @for (k of [
            { v: a.stats.chercheurs, l: 'Chercheurs permanents', u: '/chercheurs' },
            { v: a.stats.publications, l: 'Publications', u: '/publications' },
            { v: a.stats.doctorants, l: 'Doctorants en cours', u: '/doctorants' },
            { v: a.stats.projetsEnCours, l: 'Projets en cours', u: '/axes' }
          ]; track k.l) {
            <a class="kpi card-link" [routerLink]="k.u"><div class="v">{{ k.v }}</div><div class="l">{{ k.l }}</div></a>
          }
        </section>

        <section class="quick" aria-label="Accès rapide">
          @for (q of acces; track q.u) {
            <a class="q card-link" [routerLink]="q.u"><span class="mi">{{ q.i }}</span><div><strong>{{ q.t }}</strong><div class="small muted">{{ q.d }}</div></div></a>
          }
        </section>

        <div class="two">
          <section>
            <div class="sec-head"><h2>Dernières publications</h2><a routerLink="/publications" class="btn btn-ghost btn-sm">Tout voir</a></div>
            <div class="card card-body">
              @for (p of a.dernieresPublications; track p.id) {
                <div class="pub-item">
                  <div class="pi-title"><a [routerLink]="['/publications', p.id]">{{ p.titre }}</a></div>
                  <div class="pi-authors">{{ p.auteursTexte || noms(p) }}</div>
                  @if (p.venue) { <div class="pi-venue">{{ p.venue }}</div> }
                  <div class="pi-meta"><span class="chip chip-blue">{{ p.annee }}</span>
                    @if (p.type) { <span class="chip">{{ types[p.type] }}</span> }
                    <app-classement [classement]="p.classement" [source]="p.sourceClassement" [facteurImpact]="p.facteurImpact" /></div>
                </div>
              } @empty { <p class="muted">Aucune publication.</p> }
            </div>
          </section>
          <aside>
            <div class="sec-head"><h2>Agenda</h2><a routerLink="/evenements" class="btn btn-ghost btn-sm">Tous les événements</a></div>
            <div class="card card-body agenda">
              @for (e of a.prochainsEvenements; track e.id) {
                <a class="ag" [routerLink]="['/evenements', e.id]">
                  <div class="date-block">
                    @if (e.dateDebut) { <span class="d">{{ e.dateDebut | date:'d' }}</span><span class="m">{{ e.dateDebut | date:'MMM y' }}</span> }
                    @else { <span class="d">{{ e.annee }}</span> }
                  </div>
                  <div><div style="font-weight:600;line-height:1.35">{{ e.titre }}</div>
                    <div class="small muted">{{ typesEv[e.type] }} · {{ e.lieu }}</div>
                    <span class="chip" [class]="'chip ' + etats[e.etat].chip" style="margin-top:4px">{{ etats[e.etat].l }}</span></div>
                </a>
              } @empty { <p class="muted small">Aucun événement programmé.</p> }
            </div>
            <div class="sec-head" style="margin-top:22px"><h2>Axes de recherche</h2></div>
            <div class="stack">
              @for (x of a.axes; track x.id) {
                <a class="card card-body card-link axe" [routerLink]="['/axes', x.id]">
                  <div style="font-weight:600">{{ x.nom }}</div>
                  <div class="small muted">{{ x.nbChercheurs }} chercheurs · {{ x.nbPublications }} publications</div>
                </a>
              }
            </div>
          </aside>
        </div>
      } @else if (!erreur()) {
        <div class="skeleton" style="height:320px;border-radius:14px;margin-top:24px"></div>
      }
    </div>
  `,
  styles: [`
    .hero { padding:clamp(40px,7vw,76px) 0 40px; border-bottom:1px solid var(--border);
      background: radial-gradient(ellipse 60% 70% at 15% 0%, rgba(79,140,255,.16), transparent 65%),
                  radial-gradient(ellipse 40% 50% at 90% 20%, rgba(124,190,60,.10), transparent 60%); }
    .hero-in { display:grid; grid-template-columns:1.5fr 1fr; gap:36px; align-items:center; }
    .hero-badge { display:inline-flex; gap:6px; align-items:center; font-size:12.5px; color:#8ab4ff; background:rgba(79,140,255,.1);
      border:1px solid rgba(79,140,255,.25); border-radius:20px; padding:4px 12px; margin-bottom:16px; }
    .hero-title { font-family:var(--font-display); font-weight:400; font-size:clamp(36px,5.5vw,60px); line-height:1.05; }
    .hero-title span { color:#8cc63f; }
    .hero-sub { color:var(--text-soft); font-size:16px; margin:10px 0 14px; max-width:620px; }
    .hero-desc { color:var(--muted); line-height:1.75; margin-bottom:24px; max-width:660px; }
    .btn-lg { padding:11px 18px; font-size:14px; }
    .hero-logo { background:#fff; border-radius:20px; padding:28px; box-shadow:0 20px 60px -20px rgba(0,0,0,.7); }
    .stats { margin:28px 0 18px; }
    .quick { display:grid; grid-template-columns:repeat(auto-fit,minmax(210px,1fr)); gap:12px; margin-bottom:34px; }
    .q { display:flex; gap:12px; align-items:center; background:var(--surface); border:1px solid var(--border); border-radius:var(--radius); padding:14px 16px; }
    .q .mi { color:var(--accent); font-size:26px; }
    .two { display:grid; grid-template-columns:1.6fr 1fr; gap:24px; padding-bottom:56px; }
    .sec-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:10px; }
    .sec-head h2 { font-family:var(--font-display); font-weight:400; font-size:24px; }
    .agenda { display:flex; flex-direction:column; gap:14px; }
    .ag { display:flex; gap:12px; align-items:flex-start; }
    .ag:hover div > div:first-child { color:var(--accent); }
    @media (max-width: 900px) { .hero-in, .two { grid-template-columns:1fr; } .hero-logo { max-width:420px; } }
  `],
})
export class SiteAccueilComponent {
  site = inject(SiteService);
  auth = inject(AuthService);
  private seo = inject(SeoService);
  a = signal<Accueil | null>(null);
  erreur = signal('');
  types = TYPES_PUBLICATION;
  typesEv = TYPES_EVENEMENT;
  etats = ETATS_EVENEMENT;
  acces = [
    { u: '/chercheurs', i: 'groups', t: 'Chercheurs', d: 'Membres permanents et leurs travaux' },
    { u: '/publications', i: 'article', t: 'Publications', d: 'Journaux et conférences classés' },
    { u: '/evenements', i: 'event', t: 'Événements', d: 'Journées, séminaires, conférences' },
    { u: '/axes', i: 'hub', t: 'Axes de recherche', d: 'Thématiques et projets' },
  ];

  constructor() {
    this.site.accueil().subscribe({
      next: a => { this.a.set(a); this.seo.definir(null, a.site.seoDescription ?? a.site.presentation); },
      error: () => this.erreur.set('Le serveur ne répond pas : les données du laboratoire ne peuvent pas être affichées.'),
    });
  }
  noms(p: { chercheurs: { nom: string; prenom: string }[] }) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
}
