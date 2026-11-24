import { Component, inject, input, signal, effect } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicationService } from '../../../../core/services/publication.service';
import { IaService, SearchResponse } from '../../../../core/services/ia.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Publication } from '../../../../core/models/chercheur.model';
import { MarkdownPipe } from '../../../../shared/markdown.pipe';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-publication-detail',
  imports: [RouterLink, MarkdownPipe],
  template: `
    <a routerLink="/utilisateur/publications" class="btn btn-ghost btn-sm mb-16"><span class="mi sm">arrow_back</span> Publications</a>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (pub(); as p) {
      <div class="grid-main-side">
        <div class="stack">
          <div class="card card-body">
            <div class="row mb-16">
              @if (p.domaine) { <span class="chip chip-blue">{{ p.domaine.nom }}</span> }
              @if (p.annee) { <span class="chip">{{ p.annee }}</span> }
            </div>
            <h1 class="page-title">{{ p.titre }}</h1>
            <div class="row mt-8 mb-16">
              @for (a of p.chercheurs; track a.id) {
                <a [routerLink]="['/utilisateur/chercheurs', a.id]" class="chip chip-cyan"><span class="mi sm">person</span> {{ a.prenom }} {{ a.nom }}</a>
              } @empty { <span class="muted small">Auteurs non renseignés</span> }
            </div>
            <div class="section-title">Résumé</div>
            <p class="muted" style="line-height:1.75">{{ p.resume || 'Aucun résumé.' }}</p>
            @if (p.motsCles.length) {
              <div class="section-title mt-16">Mots-clés</div>
              <div class="chips">@for (k of p.motsCles; track k) { <span class="chip chip-purple">{{ k }}</span> }</div>
            }
            <div class="row mt-16">
              @if (p.doi) { <a class="btn btn-ghost btn-sm" [href]="'https://doi.org/' + p.doi" target="_blank" rel="noopener"><span class="mi sm">link</span> DOI {{ p.doi }}</a> }
              @if (p.pdfUrl) { <a class="btn btn-primary btn-sm" [href]="p.pdfUrl" target="_blank" rel="noopener"><span class="mi sm">picture_as_pdf</span> Télécharger le PDF</a> }
              @if (auth.hasRole(['ADMIN'])) { <a class="btn btn-ghost btn-sm" routerLink="/admin/publications" [queryParams]="{ edit: p.id }"><span class="mi sm">edit</span> Modifier</a> }
            </div>
          </div>

          <div class="ia-card">
            <div class="row" style="justify-content:space-between">
              <div class="ia-card-title"><span class="mi sm">summarize</span> Résumé simplifié par l'IA</div>
              <button class="btn btn-ia btn-sm" (click)="resumer()" [disabled]="resumeLoading()">
                @if (resumeLoading()) { <span class="mi sm spin">autorenew</span> Génération… } @else { <span class="mi sm">auto_awesome</span> {{ resumeIa() ? 'Régénérer' : 'Générer' }} }
              </button>
            </div>
            @if (resumeErr()) { <div class="alert alert-warn mt-8"><span class="mi sm">info</span> {{ resumeErr() }}</div> }
            @if (resumeIa()) { <div class="prose mt-8" [innerHTML]="resumeIa() | markdown"></div> }
            @else if (!resumeErr()) { <div class="ia-card-desc" style="margin:8px 0 0">Gemini reformule le résumé de cette publication en quelques phrases claires.</div> }
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <span class="card-title"><span class="mi sm" style="vertical-align:-3px;color:var(--accent3)">hub</span> Publications similaires</span>
            @if (reco(); as r) { <span class="chip" [title]="r.mode === 'semantique' ? 'Embeddings Gemini' : 'TF-IDF local'">{{ r.mode === 'semantique' ? 'sémantique' : 'lexical' }}</span> }
          </div>
          @if (recoErr()) { <div class="card-body"><div class="alert alert-warn" style="margin:0">{{ recoErr() }}</div></div> }
          @for (r of reco()?.resultats ?? []; track r.id) {
            <a [routerLink]="['/utilisateur/publications', r.id]" class="pub-line">
              <div style="min-width:0">
                <div class="pub-line-title" style="white-space:normal">{{ r.titre }}</div>
                <div class="small muted">{{ r.auteurs.join(', ') }}</div>
              </div>
              <span class="chip chip-blue">{{ (r.score * 100).toFixed(0) }}%</span>
            </a>
          } @empty {
            @if (reco()) { <div class="empty small"><p>Aucune publication similaire trouvée.</p></div> }
            @else if (!recoErr()) { <div class="card-body"><div class="skeleton" style="height:90px"></div></div> }
          }
        </div>
      </div>
    }
  `,
})
export class PublicationDetailComponent {
  private svc = inject(PublicationService);
  private ia = inject(IaService);
  auth = inject(AuthService);
  id = input.required<string>();

  pub = signal<Publication | null>(null);
  error = signal('');
  reco = signal<SearchResponse | null>(null);
  recoErr = signal('');
  resumeIa = signal('');
  resumeErr = signal('');
  resumeLoading = signal(false);

  constructor() {
    // Recharge quand on navigue d'une publication à une autre (même composant réutilisé)
    effect(() => this.charger(Number(this.id())));
  }

  private charger(id: number) {
    this.pub.set(null); this.reco.set(null); this.recoErr.set(''); this.resumeIa.set(''); this.resumeErr.set(''); this.error.set('');
    this.svc.getById(id).subscribe({ next: p => this.pub.set(p), error: e => this.error.set(messageErreur(e)) });
    this.ia.recommander(id).subscribe({ next: r => this.reco.set(r), error: e => this.recoErr.set(messageErreur(e)) });
  }

  resumer() {
    const p = this.pub();
    if (!p) return;
    this.resumeLoading.set(true);
    this.resumeErr.set('');
    this.ia.resumerPublication(p.id).subscribe({
      next: r => { this.resumeIa.set(r); this.resumeLoading.set(false); },
      error: e => { this.resumeErr.set(messageErreur(e)); this.resumeLoading.set(false); },
    });
  }
}
