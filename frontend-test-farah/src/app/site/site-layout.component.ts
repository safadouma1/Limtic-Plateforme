import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../core/services/auth.service';
import { SiteService } from '../core/services/site.service';

/** Gabarit du site public : en-tête institutionnel (logos LIMTIC et ISI), menu, pied de page. */
@Component({
  selector: 'app-site-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip" href="#contenu">Aller au contenu</a>
    <header class="sh">
      <div class="site-wrap sh-in">
        <a routerLink="/" class="brand" aria-label="Accueil du LIMTIC">
          <span class="plate"><img src="logos/limtic.png" alt="LIMTIC" height="34" /></span>
          <span class="plate hide-sm"><img src="logos/isi.png" alt="Institut Supérieur d'Informatique" height="34" /></span>
        </a>
        <button class="btn btn-ghost btn-icon burger" (click)="menu.set(!menu())" [attr.aria-expanded]="menu()" aria-controls="menu-site" aria-label="Menu">
          <span class="mi">{{ menu() ? 'close' : 'menu' }}</span>
        </button>
        <nav id="menu-site" class="nav" [class.open]="menu()" aria-label="Navigation principale">
          @for (l of liens; track l.url) {
            <a [routerLink]="l.url" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: l.url === '/' }">{{ l.t }}</a>
          }
          @if (auth.isLoggedIn()) {
            <a routerLink="/dashboard" class="btn btn-primary btn-sm cta"><span class="mi sm">space_dashboard</span> Mon espace</a>
          } @else {
            <a routerLink="/auth/login" class="btn btn-primary btn-sm cta"><span class="mi sm">login</span> Connexion</a>
          }
        </nav>
      </div>
    </header>

    <main id="contenu" tabindex="-1"><router-outlet /></main>

    <footer class="sf">
      <div class="site-wrap sf-grid">
        <div>
          <div class="row" style="gap:10px;margin-bottom:12px">
            <span class="plate"><img src="logos/limtic.png" alt="LIMTIC" height="30" /></span>
            <span class="plate"><img src="logos/utm.jpg" alt="Université de Tunis El Manar" height="30" /></span>
          </div>
          <p class="small muted">{{ site.site()?.nomComplet }}</p>
          @if (site.site()?.code) { <p class="small muted">Code {{ site.site()?.code }} · {{ site.site()?.etablissement }}</p> }
        </div>
        <div>
          <div class="ft">Nous trouver</div>
          @if (site.site(); as s) {
            @if (s.adresse) { <p class="small"><span class="mi sm">place</span> {{ s.adresse }}</p> }
            @if (s.telephone) { <p class="small"><span class="mi sm">call</span> {{ s.telephone }}</p> }
            @if (s.email) { <p class="small"><span class="mi sm">mail</span> <a [href]="'mailto:' + s.email">{{ s.email }}</a></p> }
            <div class="ext-links" style="margin-top:10px">
              @if (s.facebookUrl) { <a [href]="s.facebookUrl" target="_blank" rel="noopener">Facebook</a> }
              @if (s.linkedinUrl) { <a [href]="s.linkedinUrl" target="_blank" rel="noopener">LinkedIn</a> }
              @if (s.youtubeUrl) { <a [href]="s.youtubeUrl" target="_blank" rel="noopener">YouTube</a> }
              @if (s.siteIsiUrl) { <a [href]="s.siteIsiUrl" target="_blank" rel="noopener">Site de l'ISI</a> }
            </div>
          }
        </div>
        <div>
          <div class="ft">Accès rapide</div>
          <ul class="ql">
            <li><a routerLink="/publications">Publications</a></li>
            <li><a routerLink="/chercheurs">Chercheurs</a></li>
            <li><a routerLink="/evenements">Événements</a></li>
            <li><a routerLink="/contact">Contact</a></li>
          </ul>
        </div>
      </div>
      <div class="site-wrap small muted sf-bas">© {{ annee }} LIMTIC · Institut Supérieur d'Informatique · Université de Tunis El Manar</div>
    </footer>
  `,
  styles: [`
    :host { display:flex; flex-direction:column; min-height:100vh; }
    main { flex:1; outline:none; }
    .skip { position:absolute; left:-9999px; top:8px; z-index:100; background:var(--accent); color:#fff; padding:8px 14px; border-radius:8px; }
    .skip:focus { left:8px; }
    .sh { position:sticky; top:0; z-index:30; background:rgba(7,11,22,.88); backdrop-filter:blur(12px); border-bottom:1px solid var(--border); }
    .sh-in { display:flex; align-items:center; gap:16px; height:68px; }
    .brand { display:flex; gap:8px; align-items:center; flex:none; }
    .plate { background:#fff; border-radius:9px; padding:4px 8px; display:inline-flex; align-items:center; }
    .plate img { height:34px; width:auto; }
    .sf .plate img { height:30px; }
    .nav { display:flex; align-items:center; gap:2px; margin-left:auto; flex-wrap:nowrap; }
    .nav a:not(.cta) { padding:8px 10px; border-radius:8px; color:var(--text-soft); font-size:13.5px; font-weight:500; white-space:nowrap; }
    .nav a:not(.cta):hover { color:var(--text); background:var(--surface2); }
    .nav a.on { color:var(--text); background:var(--surface2); }
    .cta { margin-left:8px; }
    .burger { display:none; margin-left:auto; }
    .sf { border-top:1px solid var(--border); background:var(--surface); margin-top:auto; }
    .sf-grid { display:grid; grid-template-columns:1.4fr 1.2fr .8fr; gap:28px; padding-top:36px; padding-bottom:24px; }
    .sf p { margin-bottom:6px; display:flex; gap:6px; align-items:flex-start; }
    .ft { font-weight:600; margin-bottom:10px; }
    .ql { list-style:none; display:flex; flex-direction:column; gap:6px; font-size:13px; color:var(--text-soft); }
    .ql a:hover { color:var(--accent); }
    .sf-bas { border-top:1px solid var(--border); padding-top:14px; padding-bottom:18px; }
    @media (max-width: 1080px) {
      .burger { display:inline-flex; }
      .nav { display:none; position:absolute; top:68px; left:0; right:0; flex-direction:column; align-items:stretch; gap:2px;
             background:var(--surface); border-bottom:1px solid var(--border); padding:10px 16px 16px; }
      .nav.open { display:flex; }
      .cta { margin:8px 0 0; justify-content:center; }
    }
    @media (max-width: 760px) { .sf-grid { grid-template-columns:1fr; } .hide-sm { display:none; } }
  `],
})
export class SiteLayoutComponent {
  auth = inject(AuthService);
  site = inject(SiteService);
  menu = signal(false);
  annee = new Date().getFullYear();
  liens = [
    { url: '/', t: 'Accueil' }, { url: '/laboratoire', t: 'Le laboratoire' }, { url: '/axes', t: 'Axes de recherche' },
    { url: '/chercheurs', t: 'Chercheurs' }, { url: '/doctorants', t: 'Doctorants' }, { url: '/masteriens', t: 'Mastériens' },
    { url: '/publications', t: 'Publications' }, { url: '/evenements', t: 'Événements' }, { url: '/contact', t: 'Contact' },
  ];

  constructor() {
    this.site.chargerSite().subscribe({ error: () => {} });
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.menu.set(false));
  }
}
