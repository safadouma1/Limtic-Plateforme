import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChercheurService } from '../../../../core/services/chercheur.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Chercheur, Publication } from '../../../../core/models/chercheur.model';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-chercheur-detail',
  imports: [RouterLink],
  template: `
    <a routerLink="/utilisateur/chercheurs" class="btn btn-ghost btn-sm mb-16"><span class="mi sm">arrow_back</span> Chercheurs</a>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    @if (chercheur(); as c) {
      <div class="card card-body mb-24">
        <div class="row" style="gap:18px">
          <div class="r-avatar lg brand">{{ c.prenom.charAt(0) }}{{ c.nom.charAt(0) }}</div>
          <div style="flex:1;min-width:200px">
            <h1 class="page-title" style="margin:0">{{ c.prenom }} {{ c.nom }}</h1>
            <div class="muted">{{ c.specialite || 'Spécialité non renseignée' }} · {{ c.etablissement || 'Établissement non renseigné' }}</div>
            <div class="row mt-8">
              @if (c.domaine) { <span class="chip chip-purple">{{ c.domaine.nom }}</span> }
              <a class="chip" [href]="'mailto:' + c.email"><span class="mi sm">mail</span> {{ c.email }}</a>
            </div>
          </div>
          @if (auth.hasRole(['ADMIN'])) {
            <a [routerLink]="['/admin/chercheurs', c.id, 'modifier']" class="btn btn-ghost"><span class="mi sm">edit</span> Modifier</a>
          }
        </div>
      </div>

      <div class="section-title">Publications ({{ publications().length }})</div>
      <div class="card">
        @for (p of publications(); track p.id) {
          <a [routerLink]="['/utilisateur/publications', p.id]" class="pub-line">
            <div class="pub-year">{{ p.annee ?? '—' }}</div>
            <div style="min-width:0">
              <div class="pub-line-title">{{ p.titre }}</div>
              <div class="chips mt-8">@for (k of p.motsCles.slice(0, 4); track k) { <span class="chip">{{ k }}</span> }</div>
            </div>
            <span class="mi muted">chevron_right</span>
          </a>
        } @empty {
          <div class="empty"><span class="mi">article</span><p>Aucune publication associée à ce chercheur.</p></div>
        }
      </div>
    } @else if (!error()) {
      <div class="skeleton" style="height:140px"></div>
    }
  `,
})
export class ChercheurDetailComponent implements OnInit {
  private svc = inject(ChercheurService);
  auth = inject(AuthService);
  id = input.required<string>();

  chercheur = signal<Chercheur | null>(null);
  publications = signal<Publication[]>([]);
  error = signal('');

  ngOnInit() {
    const id = Number(this.id());
    this.svc.getById(id).subscribe({ next: c => this.chercheur.set(c), error: e => this.error.set(messageErreur(e)) });
    this.svc.publications(id).subscribe({ next: p => this.publications.set(p), error: () => {} });
  }
}
