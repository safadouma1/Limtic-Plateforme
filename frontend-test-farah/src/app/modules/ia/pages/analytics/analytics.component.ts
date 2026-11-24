import { Component, OnInit, inject, signal } from '@angular/core';
import { StatsService } from '../../../../core/services/stats.service';
import { IaService } from '../../../../core/services/ia.service';
import { CountItem, Stats } from '../../../../core/models/chercheur.model';
import { MarkdownPipe } from '../../../../shared/markdown.pipe';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-analytics',
  imports: [MarkdownPipe],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title"><span class="spark">✦</span> Analytics & tendances</h1>
        <p class="page-subtitle">Statistiques calculées en temps réel depuis la base, et analyse rédigée par Gemini</p>
      </div>
      <button class="btn btn-ia" (click)="analyser()" [disabled]="iaLoading() || !stats()">
        @if (iaLoading()) { <span class="mi sm spin">autorenew</span> Analyse… } @else { <span class="mi sm">auto_awesome</span> Analyser les tendances }
      </button>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (analyse() || iaErr()) {
      <div class="ia-card mb-24">
        <div class="ia-card-title"><span class="mi sm">insights</span> Analyse de l'IA</div>
        @if (iaErr()) { <div class="alert alert-warn mt-8" style="margin-bottom:0">{{ iaErr() }}</div> }
        @else { <div class="prose mt-8" [innerHTML]="analyse() | markdown"></div> }
      </div>
    }

    @if (stats(); as s) {
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon si-blue"><span class="mi">groups</span></div><div class="stat-value">{{ s.totalChercheurs }}</div><div class="stat-label">Chercheurs</div></div>
        <div class="stat-card"><div class="stat-icon si-cyan"><span class="mi">article</span></div><div class="stat-value">{{ s.totalPublications }}</div><div class="stat-label">Publications</div></div>
        <div class="stat-card"><div class="stat-icon si-purple"><span class="mi">category</span></div><div class="stat-value">{{ s.totalDomaines }}</div><div class="stat-label">Domaines</div></div>
        <div class="stat-card"><div class="stat-icon si-gold"><span class="mi">sell</span></div><div class="stat-value">{{ s.motsClesFrequents.length }}</div><div class="stat-label">Mots-clés fréquents suivis</div></div>
      </div>

      <div class="grid-2">
        <div class="card card-body">
          <div class="section-title">Publications par domaine</div>
          @for (d of s.publicationsParDomaine; track d.nom) {
            <div class="bar-row">
              <div class="bar-head"><span>{{ d.nom }}</span><span class="muted">{{ d.count }}</span></div>
              <div class="bar-track"><div class="bar-fill" [style.width.%]="d.count / max(s.publicationsParDomaine) * 100"></div></div>
            </div>
          } @empty { <p class="muted small">Aucune donnée.</p> }
        </div>

        <div class="card card-body">
          <div class="section-title">Publications par année</div>
          @if (s.publicationsParAnnee.length) {
            <div class="years">
              @for (a of s.publicationsParAnnee; track a.nom) {
                <div class="year-col" [title]="a.count + ' publication(s) en ' + a.nom">
                  <span class="small">{{ a.count }}</span>
                  <div class="year-bar" [style.height.%]="a.count / max(s.publicationsParAnnee) * 100"></div>
                  <span class="small muted">{{ a.nom }}</span>
                </div>
              }
            </div>
          } @else { <p class="muted small">Aucune donnée.</p> }
        </div>
      </div>

      <div class="card card-body mt-16">
        <div class="section-title">Mots-clés les plus fréquents</div>
        <div class="chips">
          @for (m of s.motsClesFrequents; track m.nom) { <span class="chip chip-purple">{{ m.nom }} · {{ m.count }}</span> }
          @empty { <span class="muted small">Aucun mot-clé.</span> }
        </div>
      </div>
    } @else if (!error()) {
      <div class="stats-grid">@for (i of [1, 2, 3, 4]; track i) { <div class="stat-card skeleton" style="height:122px"></div> }</div>
    }
  `,
  styles: [`
    .years { display: flex; align-items: flex-end; gap: 12px; height: 200px; padding-top: 8px; }
    .year-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 4px; height: 100%; }
    .year-bar { width: 100%; max-width: 46px; min-height: 4px; border-radius: 6px 6px 2px 2px; background: var(--grad-brand); transition: height .6s ease; }
  `],
})
export class AnalyticsComponent implements OnInit {
  private statsSvc = inject(StatsService);
  private ia = inject(IaService);

  stats = signal<Stats | null>(null);
  error = signal('');
  analyse = signal('');
  iaErr = signal('');
  iaLoading = signal(false);

  max(items: CountItem[]) { return Math.max(1, ...items.map(i => i.count)); }

  ngOnInit() {
    this.statsSvc.get().subscribe({ next: s => this.stats.set(s), error: e => this.error.set(messageErreur(e)) });
  }

  analyser() {
    this.iaLoading.set(true); this.iaErr.set('');
    this.ia.tendances().subscribe({
      next: r => { this.analyse.set(r); this.iaLoading.set(false); },
      error: e => { this.iaErr.set(messageErreur(e)); this.iaLoading.set(false); },
    });
  }
}
