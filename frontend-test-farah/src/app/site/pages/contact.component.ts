import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DomSanitizer } from '@angular/platform-browser';
import { SiteService } from '../../core/services/site.service';
import { Captcha } from '../../core/models/site.model';
import { SeoService } from '../../shared/seo.service';
import { messageErreur } from '../../shared/erreur';

/** Page de contact (§3.9) : formulaire avec captcha, coordonnées, carte Google Maps, réseaux sociaux. */
@Component({
  selector: 'app-site-contact',
  imports: [ReactiveFormsModule],
  template: `
    <div class="site-hero-mini"><div class="site-wrap"><h1>Contact</h1><p>Une question, une collaboration, une candidature ? Écrivez-nous.</p></div></div>
    <div class="site-wrap site-page grid-main-side">
      <div class="card card-body">
        @if (ok()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ ok() }}</div> }
        @if (erreur()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div> }
        <form [formGroup]="form" (ngSubmit)="envoyer()" novalidate>
          <div class="form-row">
            <div class="form-group"><label class="form-label" for="nom">Nom *</label><input id="nom" class="form-control" formControlName="nom" autocomplete="name" />
              @if (inv('nom')) { <div class="form-error">Indiquez votre nom.</div> }</div>
            <div class="form-group"><label class="form-label" for="email">E-mail *</label><input id="email" type="email" class="form-control" formControlName="email" autocomplete="email" />
              @if (inv('email')) { <div class="form-error">Adresse e-mail invalide.</div> }</div>
          </div>
          <div class="form-group"><label class="form-label" for="sujet">Sujet *</label><input id="sujet" class="form-control" formControlName="sujet" />
            @if (inv('sujet')) { <div class="form-error">Indiquez un sujet.</div> }</div>
          <div class="form-group"><label class="form-label" for="message">Message *</label><textarea id="message" class="form-control" rows="6" formControlName="message"></textarea>
            @if (inv('message')) { <div class="form-error">10 caractères au moins.</div> }</div>
          <div class="hp" aria-hidden="true"><label for="siteWeb">Ne pas remplir</label><input id="siteWeb" tabindex="-1" autocomplete="off" formControlName="siteWeb" /></div>
          <div class="form-group" style="max-width:320px">
            <label class="form-label" for="captcha">Vérification anti-robot : {{ captcha()?.question ?? '…' }} *</label>
            <div class="row"><input id="captcha" class="form-control" inputmode="numeric" formControlName="captcha" style="width:120px" />
              <button type="button" class="btn btn-ghost btn-icon" (click)="nouveauCaptcha()" aria-label="Autre question"><span class="mi sm">refresh</span></button></div>
            @if (inv('captcha')) { <div class="form-error">Répondez à la question.</div> }
          </div>
          <button class="btn btn-primary" type="submit" [disabled]="envoi()"><span class="mi sm">send</span> Envoyer</button>
        </form>
      </div>
      <aside class="stack">
        @if (site.site(); as s) {
          <div class="card card-body coord">
            <div class="card-title mb-16">Coordonnées</div>
            @if (s.adresse) { <p><span class="mi sm">place</span> {{ s.adresse }}</p> }
            @if (s.telephone) { <p><span class="mi sm">call</span> {{ s.telephone }}</p> }
            @if (s.fax) { <p><span class="mi sm">print</span> Fax {{ s.fax }}</p> }
            @if (s.email) { <p><span class="mi sm">mail</span> <a [href]="'mailto:' + s.email">{{ s.email }}</a></p> }
            <div class="ext-links" style="margin-top:10px">
              @if (s.facebookUrl) { <a [href]="s.facebookUrl" target="_blank" rel="noopener">Facebook</a> }
              @if (s.linkedinUrl) { <a [href]="s.linkedinUrl" target="_blank" rel="noopener">LinkedIn</a> }
              @if (s.youtubeUrl) { <a [href]="s.youtubeUrl" target="_blank" rel="noopener">YouTube</a> }
              @if (s.siteIsiUrl) { <a [href]="s.siteIsiUrl" target="_blank" rel="noopener">Site de l'ISI</a> }
            </div>
          </div>
          @if (carte()) {
            <div class="card" style="overflow:hidden"><iframe [src]="carte()" title="Carte : emplacement du laboratoire" loading="lazy" referrerpolicy="no-referrer-when-downgrade"></iframe></div>
          }
        }
      </aside>
    </div>
  `,
  styles: [`.hp { position:absolute; left:-9999px; } .coord p { display:flex; gap:8px; align-items:flex-start; margin-bottom:8px; color:var(--text-soft); }
    .coord a { color:var(--accent); } iframe { width:100%; height:300px; border:0; display:block; }`],
})
export class SiteContactComponent {
  site = inject(SiteService);
  private fb = inject(FormBuilder);
  private san = inject(DomSanitizer);
  captcha = signal<Captcha | null>(null);
  ok = signal(''); erreur = signal(''); envoi = signal(false);
  carte = computed(() => {
    const q = this.site.site()?.carteRequete ?? this.site.site()?.adresse;
    return q ? this.san.bypassSecurityTrustResourceUrl('https://www.google.com/maps?output=embed&q=' + encodeURIComponent(q)) : null;
  });
  form = this.fb.nonNullable.group({
    nom: ['', [Validators.required, Validators.maxLength(120)]], email: ['', [Validators.required, Validators.email]],
    sujet: ['', [Validators.required, Validators.maxLength(200)]], message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(5000)]],
    captcha: ['', Validators.required], siteWeb: [''],
  });

  constructor() {
    inject(SeoService).definir('Contact');
    this.site.chargerSite().subscribe({ error: () => {} });
    this.nouveauCaptcha();
  }
  inv(c: string) { const x = this.form.get(c)!; return x.touched && x.invalid; }
  nouveauCaptcha() { this.form.controls.captcha.setValue(''); this.site.captcha().subscribe({ next: c => this.captcha.set(c), error: () => {} }); }
  envoyer() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.envoi.set(true); this.ok.set(''); this.erreur.set('');
    this.site.contact({ ...this.form.getRawValue(), jeton: this.captcha()?.jeton ?? '' }).subscribe({
      next: r => { this.envoi.set(false); this.ok.set(r.message); this.form.reset(); this.nouveauCaptcha(); },
      error: e => { this.envoi.set(false); this.erreur.set(messageErreur(e)); this.nouveauCaptcha(); },
    });
  }
}
