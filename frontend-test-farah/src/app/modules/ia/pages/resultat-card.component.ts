import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SearchResult } from '../../../core/services/ia.service';

/** Carte d'un résultat de recherche/recommandation avec son score de similarité. */
@Component({
  selector: 'app-resultat-card',
  imports: [RouterLink],
  template: `
    @let r = resultat();
    <div class="card card-body">
      <div class="row" style="align-items:flex-start;gap:18px">
        <div style="flex:1;min-width:220px">
          <a [routerLink]="['/utilisateur/publications', r.id]" style="font-size:15.5px;font-weight:600">{{ r.titre }}</a>
          @if (r.resume) { <p class="small muted clamp-3 mt-8">{{ r.resume }}</p> }
          <div class="chips mt-8">@for (k of r.motsCles; track k) { <span class="chip chip-purple">{{ k }}</span> }</div>
        </div>
        <div class="score" [title]="'Similarité cosinus : ' + r.score">
          <div class="score-value">{{ (r.score * 100).toFixed(0) }}%</div>
          <div class="small muted">similarité</div>
          <div class="bar-track" style="width:70px;margin-top:6px"><div class="bar-fill" [style.width.%]="r.score * 100"></div></div>
        </div>
      </div>
      <div class="row mt-16" style="justify-content:space-between;padding-top:12px;border-top:1px solid var(--border)">
        <span class="small muted">{{ r.auteurs.join(', ') || 'Auteurs non renseignés' }}@if (r.annee) { · {{ r.annee }} }</span>
        <div class="row">
          <a [routerLink]="['/utilisateur/publications', r.id]" class="btn btn-ghost btn-sm">Voir</a>
          <button class="btn btn-ghost btn-sm" (click)="similaires.emit(r.id)"><span class="mi sm">hub</span> Similaires</button>
        </div>
      </div>
    </div>
  `,
  styles: [`.score { text-align: right; flex-shrink: 0; } .score-value { font-size: 22px; font-weight: 600; color: #8ab4ff; line-height: 1.1; }`],
})
export class ResultatCardComponent {
  resultat = input.required<SearchResult>();
  similaires = output<number>();
}
