import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { SiteService } from '../../core/services/site.service';
import { Alertes } from '../../core/models/site.model';
import { TYPES_PUBLICATION, TypePublication } from '../../core/models/chercheur.model';
import { AuthService } from '../../core/services/auth.service';
import { StatsService } from '../../core/services/stats.service';
import { ChercheurService } from '../../core/services/chercheur.service';
import { PublicationService } from '../../core/services/publication.service';
import { IaService } from '../../core/services/ia.service';
import { Chercheur, Publication, Stats } from '../../core/models/chercheur.model';
import { messageErreur } from '../../shared/erreur';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  auth = inject(AuthService);
  ia = inject(IaService);
  private statsSvc = inject(StatsService);
  private chSvc = inject(ChercheurService);
  private pubSvc = inject(PublicationService);

  stats = signal<Stats | null>(null);
  chercheurs = signal<Chercheur[]>([]);
  publications = signal<Publication[]>([]);
  error = signal('');
  private site = inject(SiteService);
  alertes = signal<Alertes | null>(null);
  totalAttente = computed(() => (this.alertes()?.enAttente ?? []).reduce((s, a) => s + a.nombre, 0));
  parType = computed(() => Object.entries(this.alertes()?.publicationsParType ?? {}).map(([k, v]) => ({ libelle: TYPES_PUBLICATION[k as TypePublication] ?? k, nombre: v })));

  maxDomaine = computed(() => Math.max(1, ...(this.stats()?.publicationsParDomaine.map(d => d.count) ?? [1])));

  ngOnInit() {
    this.statsSvc.get().subscribe({ next: s => this.stats.set(s), error: e => this.error.set(messageErreur(e)) });
    this.chSvc.getAll().subscribe({
      next: c => this.chercheurs.set([...c].sort((a, b) => b.nbPublications - a.nbPublications).slice(0, 5)),
      error: () => {},
    });
    if (this.auth.estAdmin()) this.site.alertes().subscribe({ next: a => this.alertes.set(a), error: () => {} });
    this.pubSvc.getAll().subscribe({ next: p => this.publications.set(p.slice(0, 5)), error: () => {} });
  }

  auteurs(p: Publication) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
}
