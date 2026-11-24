import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IaService } from '../../../../core/services/ia.service';
import { MarkdownPipe } from '../../../../shared/markdown.pipe';
import { messageErreur } from '../../../../shared/erreur';
import { IaStatusBannerComponent } from '../ia-status-banner.component';

@Component({
  selector: 'app-ia-outils',
  imports: [FormsModule, MarkdownPipe, IaStatusBannerComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title"><span class="spark">✦</span> Résumé & mots-clés</h1>
        <p class="page-subtitle">Collez un résumé ou un extrait d'article : Gemini en produit une synthèse et des mots-clés d'indexation.</p>
      </div>
    </div>

    <app-ia-status-banner />

    <div class="grid-2">
      <div class="card card-body">
        <label class="form-label" for="texte">Texte scientifique</label>
        <textarea id="texte" class="form-control" rows="14" [(ngModel)]="texte" maxlength="20000"
                  placeholder="Collez ici l'abstract ou une partie de l'article…"></textarea>
        <div class="row mt-8" style="justify-content:space-between">
          <span class="small muted">{{ texte.length }} / 20 000 caractères</span>
          <div class="row">
            <button class="btn btn-ghost" (click)="motsCles()" [disabled]="!texte.trim() || kwLoading()">
              @if (kwLoading()) { <span class="mi sm spin">autorenew</span> } @else { <span class="mi sm">sell</span> } Mots-clés
            </button>
            <button class="btn btn-ia" (click)="resumer()" [disabled]="!texte.trim() || resLoading()">
              @if (resLoading()) { <span class="mi sm spin">autorenew</span> } @else { <span class="mi sm">summarize</span> } Résumer
            </button>
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="ia-card">
          <div class="ia-card-title"><span class="mi sm">summarize</span> Résumé</div>
          @if (resErr()) { <div class="alert alert-warn mt-8" style="margin-bottom:0">{{ resErr() }}</div> }
          @else if (resume()) { <div class="prose mt-8" [innerHTML]="resume() | markdown"></div> }
          @else { <div class="ia-card-desc" style="margin:0">Le résumé apparaîtra ici.</div> }
        </div>
        <div class="ia-card">
          <div class="ia-card-title"><span class="mi sm">sell</span> Mots-clés</div>
          @if (kwErr()) { <div class="alert alert-warn mt-8" style="margin-bottom:0">{{ kwErr() }}</div> }
          @else if (mots().length) {
            <div class="chips mt-8">@for (k of mots(); track k) { <span class="chip chip-purple">{{ k }}</span> }</div>
            <button class="btn btn-ghost btn-sm mt-16" (click)="copier()"><span class="mi sm">content_copy</span> {{ copie() ? 'Copié !' : 'Copier' }}</button>
          } @else { <div class="ia-card-desc" style="margin:0">Les mots-clés apparaîtront ici.</div> }
        </div>
      </div>
    </div>
  `,
})
export class IaOutilsComponent {
  private ia = inject(IaService);
  texte = '';
  resume = signal('');
  resErr = signal('');
  resLoading = signal(false);
  mots = signal<string[]>([]);
  kwErr = signal('');
  kwLoading = signal(false);
  copie = signal(false);

  resumer() {
    this.resLoading.set(true); this.resErr.set('');
    this.ia.resumer(this.texte).subscribe({
      next: r => { this.resume.set(r); this.resLoading.set(false); },
      error: e => { this.resErr.set(messageErreur(e)); this.resLoading.set(false); },
    });
  }

  motsCles() {
    this.kwLoading.set(true); this.kwErr.set('');
    this.ia.extraireMotsCles(this.texte).subscribe({
      next: r => { this.mots.set(r); this.kwLoading.set(false); },
      error: e => { this.kwErr.set(messageErreur(e)); this.kwLoading.set(false); },
    });
  }

  copier() {
    navigator.clipboard?.writeText(this.mots().join(', ')).then(() => {
      this.copie.set(true);
      setTimeout(() => this.copie.set(false), 1500);
    });
  }
}
