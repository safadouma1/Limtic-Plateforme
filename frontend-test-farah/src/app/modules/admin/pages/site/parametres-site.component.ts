import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../../../core/services/site.service';
import { SiteParametres } from '../../../../core/models/site.model';
import { EditeurRicheComponent } from '../../../../shared/editeur-riche.component';
import { messageErreur } from '../../../../shared/erreur';

/** Paramètres généraux (§4.3.6) et page du directeur (§3.2). */
@Component({
  selector: 'app-parametres-site',
  imports: [FormsModule, RouterLink, EditeurRicheComponent],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Paramètres du site</h1><p class="page-subtitle">Identité, page du directeur, coordonnées, réseaux sociaux et référencement</p></div>
      <a routerLink="/" class="btn btn-ghost" target="_blank"><span class="mi sm">open_in_new</span> Voir le site</a>
    </div>
    @if (ok()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ ok() }}</div> }
    @if (erreur()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div> }
    @if (p(); as p) {
      <form (ngSubmit)="enregistrer(p)" class="stack">
        <section class="card card-body">
          <div class="card-title mb-16">Identité du laboratoire</div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="nc">Nom court</label><input id="nc" class="form-control" name="nc" [(ngModel)]="p.nomCourt" /></div>
            <div class="form-group"><label class="form-label" for="co">Couleur principale</label>
              <div class="row"><input id="co" type="color" name="co" [(ngModel)]="p.couleurPrincipale" style="width:48px;height:38px;border:0;background:none" />
                <input class="form-control" name="co2" [(ngModel)]="p.couleurPrincipale" aria-label="Code couleur" style="width:120px" /></div></div>
          </div>
          <div class="form-group"><label class="form-label" for="nl">Nom complet</label><input id="nl" class="form-control" name="nl" [(ngModel)]="p.nomComplet" /></div>
          <div class="form-group"><label class="form-label" for="pr">Présentation (page d'accueil)</label><textarea id="pr" class="form-control" rows="4" maxlength="4000" name="pr" [(ngModel)]="p.presentation"></textarea></div>
          <p class="form-hint">Le logo affiché provient de <code>public/logos/limtic.png</code> (remplacer le fichier pour le changer). L'université, l'établissement et le code se règlent dans Rapport d'activité → Identification.</p>
        </section>
        <section class="card card-body">
          <div class="card-title mb-16">Page du directeur</div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="td">Titre affiché</label><input id="td" class="form-control" name="td" [(ngModel)]="p.titreDirecteur" placeholder="Professeur de l'enseignement supérieur" /></div>
            <div class="form-group"><label class="form-label" for="ad">Axes prioritaires</label><input id="ad" class="form-control" name="ad" [(ngModel)]="p.axesPrioritairesDirecteur" /></div>
          </div>
          <div class="form-group"><label class="form-label">Mot du directeur</label><app-editeur-riche label="Mot du directeur" [(valeur)]="p.motDirecteur" /></div>
          <p class="form-hint">Le directeur est celui désigné dans l'identification du rapport ; sa photo et ses liens viennent de sa fiche chercheur.</p>
        </section>
        <section class="card card-body">
          <div class="card-title mb-16">Coordonnées et réseaux sociaux</div>
          <div class="form-group"><label class="form-label" for="adr">Adresse postale</label><input id="adr" class="form-control" name="adr" [(ngModel)]="p.adresse" /></div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="tel">Téléphone</label><input id="tel" class="form-control" name="tel" [(ngModel)]="p.telephone" /></div>
            <div class="form-group"><label class="form-label" for="fax">Fax</label><input id="fax" class="form-control" name="fax" [(ngModel)]="p.fax" /></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="em">E-mail de contact</label><input id="em" type="email" class="form-control" name="em" [(ngModel)]="p.email" /></div>
            <div class="form-group"><label class="form-label" for="ca">Carte Google Maps (adresse ou coordonnées)</label><input id="ca" class="form-control" name="ca" [(ngModel)]="p.carteRequete" /></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="fb">Facebook</label><input id="fb" type="url" class="form-control" name="fb" [(ngModel)]="p.facebookUrl" placeholder="https://…" /></div>
            <div class="form-group"><label class="form-label" for="ln">LinkedIn</label><input id="ln" type="url" class="form-control" name="ln" [(ngModel)]="p.linkedinUrl" placeholder="https://…" /></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="yt">YouTube</label><input id="yt" type="url" class="form-control" name="yt" [(ngModel)]="p.youtubeUrl" placeholder="https://…" /></div>
            <div class="form-group"><label class="form-label" for="isi">Site de l'ISI</label><input id="isi" type="url" class="form-control" name="isi" [(ngModel)]="p.siteIsiUrl" /></div>
          </div>
        </section>
        <section class="card card-body">
          <div class="card-title mb-16">Référencement (SEO)</div>
          <div class="form-group"><label class="form-label" for="st">Titre des pages</label><input id="st" class="form-control" maxlength="200" name="st" [(ngModel)]="p.seoTitre" /></div>
          <div class="form-group"><label class="form-label" for="sd">Description</label><textarea id="sd" class="form-control" rows="2" maxlength="500" name="sd" [(ngModel)]="p.seoDescription"></textarea>
            <span class="form-hint">{{ (p.seoDescription ?? '').length }} / 500 · idéalement 150 à 160 caractères</span></div>
          <div class="form-group"><label class="form-label" for="sk">Mots-clés</label><input id="sk" class="form-control" maxlength="500" name="sk" [(ngModel)]="p.seoMotsCles" /></div>
        </section>
        <section class="card card-body">
          <div class="card-title mb-8">Envoi des courriels (SMTP)</div>
          <p class="small muted">Pour des raisons de sécurité, le serveur SMTP se configure par variables d'environnement du backend
            (<code>SMTP_HOST</code>, <code>SMTP_PORT</code>, <code>SMTP_USERNAME</code>, <code>SMTP_PASSWORD</code>, <code>MAIL_FROM</code>) et non depuis cette page :
            le mot de passe ne transite ainsi jamais par le navigateur. Sans SMTP, les liens « mot de passe oublié » sont écrits dans le journal du serveur.</p>
        </section>
        <div><button class="btn btn-primary" type="submit" [disabled]="envoi()"><span class="mi sm">save</span> Enregistrer</button></div>
      </form>
    }
  `,
})
export class ParametresSiteComponent {
  private site = inject(SiteService);
  p = signal<SiteParametres | null>(null);
  ok = signal(''); erreur = signal(''); envoi = signal(false);
  constructor() { this.site.parametres().subscribe({ next: p => this.p.set(p), error: e => this.erreur.set(messageErreur(e)) }); }
  enregistrer(p: SiteParametres) {
    this.envoi.set(true); this.ok.set(''); this.erreur.set('');
    this.site.enregistrerParametres(p).subscribe({
      next: r => { this.envoi.set(false); this.p.set(r); this.ok.set('Paramètres enregistrés.'); window.scrollTo({ top: 0 }); },
      error: e => { this.envoi.set(false); this.erreur.set(messageErreur(e)); window.scrollTo({ top: 0 }); },
    });
  }
}
