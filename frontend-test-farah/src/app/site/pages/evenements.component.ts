import { Component, HostListener, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../core/services/site.service';
import { ETATS_EVENEMENT, EvenementDetail, EvenementResume, TYPES_EVENEMENT, TypeEvenement } from '../../core/models/site.model';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Liste des événements (§3.8) : à venir / passés (archivage automatique selon la date). */
@Component({
  selector: 'app-site-evenements',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="site-hero-mini"><div class="site-wrap">
      <h1>Événements</h1><p>Conférences, journées de recherche, séminaires et ateliers organisés par le laboratoire.</p>
    </div></div>
    <div class="site-wrap site-page">
      <div class="tabs" role="tablist">
        <button role="tab" [class.on]="vue() === 'avenir'" [attr.aria-selected]="vue() === 'avenir'" (click)="vue.set('avenir')">À venir ({{ avenir().length }})</button>
        <button role="tab" [class.on]="vue() === 'passes'" [attr.aria-selected]="vue() === 'passes'" (click)="vue.set('passes')">Archives ({{ passes().length }})</button>
        <span class="spacer"></span>
        <select class="form-control" style="width:auto;margin:4px 0" aria-label="Type d'événement" (change)="type.set($any($event.target).value)">
          <option value="">Tous les types</option>
          @for (t of typesListe; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }
        </select>
      </div>
      <div class="event-grid">
        @for (e of (vue() === 'avenir' ? avenir() : passes()); track e.id) {
          <a class="event-card" [routerLink]="['/evenements', e.id]">
            <div class="ec-img" [style.background-image]="e.photoUrl ? 'url(' + site.url(e.photoUrl) + ')' : null">
              @if (!e.photoUrl) { <span class="mi" style="font-size:42px">event</span> }
            </div>
            <div class="ec-body">
              <div class="chips"><span class="chip chip-blue">{{ types[e.type] }}</span><span class="chip" [class]="'chip ' + etats[e.etat].chip">{{ etats[e.etat].l }}</span></div>
              <div class="ec-title">{{ e.titre }}</div>
              <div class="small muted"><span class="mi sm">event</span>
                @if (e.dateDebut) { {{ e.dateDebut | date:'d MMMM y' }}@if (e.dateFin && e.dateFin !== e.dateDebut) { – {{ e.dateFin | date:'d MMMM y' }} } } @else { {{ e.annee }} }</div>
              @if (e.lieu) { <div class="small muted"><span class="mi sm">place</span> {{ e.lieu }}</div> }
            </div>
          </a>
        } @empty { <p class="muted">{{ vue() === 'avenir' ? 'Aucun événement à venir pour le moment.' : 'Aucun événement archivé.' }}</p> }
      </div>
    </div>
  `,
})
export class SiteEvenementsComponent {
  site = inject(SiteService);
  tous = signal<EvenementResume[]>([]);
  vue = signal<'avenir' | 'passes'>('avenir');
  type = signal('');
  types = TYPES_EVENEMENT; etats = ETATS_EVENEMENT;
  typesListe = Object.entries(TYPES_EVENEMENT) as [TypeEvenement, string][];
  private filtre = computed(() => this.tous().filter(e => !this.type() || e.type === this.type()));
  avenir = computed(() => this.filtre().filter(e => e.etat !== 'TERMINE').sort((a, b) => (a.dateDebut ?? '').localeCompare(b.dateDebut ?? '')));
  passes = computed(() => this.filtre().filter(e => e.etat === 'TERMINE'));
  constructor() {
    inject(SeoService).definir('Événements');
    this.site.evenements().subscribe(l => { this.tous.set(l); if (!l.some(e => e.etat !== 'TERMINE')) this.vue.set('passes'); });
  }
}

/** Détail d'un événement : description, programme, intervenants, galerie photos avec visionneuse. */
@Component({
  selector: 'app-site-evenement',
  imports: [RouterLink, DatePipe],
  template: `
    @if (e(); as e) {
      <div class="site-hero-mini"><div class="site-wrap">
        <div class="crumbs"><a routerLink="/evenements">Événements</a> › <span>{{ types[e.type] }}</span></div>
        <h1>{{ e.titre }}</h1>
        <div class="chips" style="margin-top:10px">
          <span class="chip" [class]="'chip ' + etats[e.etat].chip">{{ etats[e.etat].l }}</span>
          <span class="chip"><span class="mi sm">event</span>&nbsp;@if (e.dateDebut) { {{ e.dateDebut | date:'d MMMM y' }}@if (e.dateFin && e.dateFin !== e.dateDebut) { – {{ e.dateFin | date:'d MMMM y' }} } } @else { {{ e.annee }} }</span>
          @if (e.lieu) { <span class="chip"><span class="mi sm">place</span>&nbsp;{{ e.lieu }}</span> }
          @if (e.organisateurs) { <span class="chip">Organisation : {{ e.organisateurs }}</span> }
        </div>
      </div></div>
      <div class="site-wrap site-page grid-main-side">
        <div class="stack">
          @if (e.description) { <div class="card card-body"><div class="card-title mb-16">Présentation</div><p class="rich" style="white-space:pre-line">{{ e.description }}</p></div> }
          @if (e.programme || e.programmeUrl) {
            <div class="card card-body"><div class="row mb-16" style="justify-content:space-between"><div class="card-title">Programme scientifique</div>
              @if (e.programmeUrl) { <a class="btn btn-ghost btn-sm" [href]="site.url(e.programmeUrl)" target="_blank" rel="noopener"><span class="mi sm">picture_as_pdf</span> Programme (PDF)</a> }</div>
              @if (e.programme) { <div class="rich" [innerHTML]="e.programme"></div> }</div>
          }
          @if (e.photos.length) {
            <div class="card card-body"><div class="card-title mb-16">Galerie ({{ e.photos.length }} photos)</div>
              <div class="gal">
                @for (p of e.photos; track $index; let i = $index) {
                  <button class="g" (click)="ouvert.set(i)" [attr.aria-label]="'Agrandir : ' + (p.legende || 'photo ' + (i + 1))">
                    <img [src]="site.url(p.url)" [alt]="p.legende || 'Photo ' + (i + 1)" loading="lazy" />
                    @if (p.legende) { <span class="cap">{{ p.legende }}</span> }
                  </button>
                }
              </div></div>
          }
        </div>
        <aside class="card card-body" style="align-self:start">
          <div class="card-title mb-16">Intervenants invités</div>
          @for (i of e.intervenants; track $index) {
            <div class="int"><div style="font-weight:600">{{ i.nom }}</div>@if (i.affiliation) { <div class="small muted">{{ i.affiliation }}</div> }
              @if (i.titreIntervention) { <div class="small" style="font-style:italic;margin-top:2px">« {{ i.titreIntervention }} »</div> }</div>
          } @empty { <p class="small muted">Aucun intervenant renseigné.</p> }
        </aside>
      </div>
      @if (ouvert() !== null) {
        <div class="lb" role="dialog" aria-modal="true" aria-label="Visionneuse de photos" (click)="ouvert.set(null)">
          <button class="btn btn-ghost btn-icon lb-x" aria-label="Fermer" (click)="ouvert.set(null)"><span class="mi">close</span></button>
          <button class="btn btn-ghost btn-icon lb-p" aria-label="Photo précédente" (click)="$event.stopPropagation(); nav(-1)"><span class="mi">chevron_left</span></button>
          <figure (click)="$event.stopPropagation()">
            <img [src]="site.url(e.photos[ouvert()!].url)" [alt]="e.photos[ouvert()!].legende || 'Photo'" />
            <figcaption>{{ e.photos[ouvert()!].legende }} <span class="muted">({{ ouvert()! + 1 }} / {{ e.photos.length }})</span></figcaption>
          </figure>
          <button class="btn btn-ghost btn-icon lb-n" aria-label="Photo suivante" (click)="$event.stopPropagation(); nav(1)"><span class="mi">chevron_right</span></button>
        </div>
      }
    } @else if (erreur()) { <div class="site-wrap site-page"><div class="alert alert-error">{{ erreur() }}</div></div> }
  `,
  styles: [`
    .gal { display:grid; grid-template-columns:repeat(auto-fill,minmax(170px,1fr)); gap:10px; }
    .g { position:relative; border:0; padding:0; border-radius:10px; overflow:hidden; cursor:zoom-in; aspect-ratio:4/3; background:var(--surface3); }
    .g img { width:100%; height:100%; object-fit:cover; transition:transform .2s; } .g:hover img { transform:scale(1.04); }
    .cap { position:absolute; left:0; right:0; bottom:0; padding:6px 8px; font-size:11.5px; text-align:left; background:linear-gradient(transparent, rgba(0,0,0,.75)); color:#fff; }
    .int { padding:10px 0; border-bottom:1px solid var(--border); } .int:last-child { border-bottom:0; }
    .lb { position:fixed; inset:0; z-index:100; background:rgba(3,6,14,.92); display:flex; align-items:center; justify-content:center; padding:20px; }
    .lb figure { max-width:min(1100px,90vw); text-align:center; } .lb img { max-height:80vh; margin:0 auto; border-radius:8px; }
    .lb figcaption { margin-top:10px; color:var(--text-soft); }
    .lb-x { position:absolute; top:16px; right:16px; } .lb-p { position:absolute; left:12px; } .lb-n { position:absolute; right:12px; }
  `],
})
export class SiteEvenementComponent {
  site = inject(SiteService);
  private seo = inject(SeoService);
  id = input.required<string>();
  e = signal<EvenementDetail | null>(null);
  erreur = signal('');
  ouvert = signal<number | null>(null);
  types = TYPES_EVENEMENT; etats = ETATS_EVENEMENT;
  constructor() {
    effect(() => { const id = Number(this.id()); untracked(() => this.site.evenement(id).subscribe({
      next: e => { this.e.set(e); this.seo.definir(e.titre, e.description); }, error: x => this.erreur.set(messageErreur(x)) })); });
  }
  nav(d: number) { const n = this.e()?.photos.length ?? 0; if (n) this.ouvert.update(i => ((i ?? 0) + d + n) % n); }
  @HostListener('document:keydown', ['$event'])
  clavier(ev: KeyboardEvent) {
    if (this.ouvert() === null) return;
    if (ev.key === 'Escape') this.ouvert.set(null);
    if (ev.key === 'ArrowRight') this.nav(1);
    if (ev.key === 'ArrowLeft') this.nav(-1);
  }
}
