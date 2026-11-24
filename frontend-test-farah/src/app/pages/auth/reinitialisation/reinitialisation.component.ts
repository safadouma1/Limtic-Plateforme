import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SiteService } from '../../../core/services/site.service';
import { messageErreur } from '../../../shared/erreur';

/** Mot de passe oublié (§4.1) : demande d'un lien de réinitialisation par e-mail. */
@Component({
  selector: 'app-oubli',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page"><div class="auth-card fade-up">
      <a routerLink="/" class="auth-logo logo"><span class="plate-auth"><img src="logos/limtic.png" alt="LIMTIC" /></span>
        <h1 class="auth-title">Mot de passe oublié</h1>
        <p class="auth-sub">Recevez un lien de réinitialisation valable 30 minutes</p></a>
      @if (ok()) { <div class="alert alert-success" role="status"><span class="mi sm">mark_email_read</span> {{ ok() }}</div> }
      @if (erreur()) { <div class="alert alert-error" role="alert"><span class="mi sm">error</span> {{ erreur() }}</div> }
      <form [formGroup]="form" (ngSubmit)="envoyer()" novalidate>
        <div class="form-group"><label class="form-label" for="email">Adresse e-mail du compte</label>
          <input id="email" class="form-control" type="email" formControlName="email" autocomplete="email" />
          @if (form.controls.email.touched && form.controls.email.invalid) { <div class="form-error">Adresse e-mail invalide.</div> }</div>
        <button class="btn btn-primary btn-block" type="submit" [disabled]="envoi()">Envoyer le lien</button>
      </form>
      <p class="auth-foot"><a routerLink="/auth/login">Retour à la connexion</a></p>
    </div></div>
  `,
})
export class OubliComponent {
  private site = inject(SiteService);
  form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  ok = signal(''); erreur = signal(''); envoi = signal(false);
  envoyer() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.envoi.set(true); this.erreur.set('');
    this.site.motDePasseOublie(this.form.getRawValue().email).subscribe({
      next: r => { this.envoi.set(false); this.ok.set(r.message); },
      error: e => { this.envoi.set(false); this.erreur.set(messageErreur(e)); },
    });
  }
}

/** Choix d'un nouveau mot de passe à partir du lien reçu. */
@Component({
  selector: 'app-reinitialisation',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page"><div class="auth-card fade-up">
      <a routerLink="/" class="auth-logo logo"><span class="plate-auth"><img src="logos/limtic.png" alt="LIMTIC" /></span>
        <h1 class="auth-title">Nouveau mot de passe</h1></a>
      @if (ok()) { <div class="alert alert-success" role="status"><span class="mi sm">check_circle</span> {{ ok() }}</div>
        <a routerLink="/auth/login" class="btn btn-primary btn-block">Se connecter</a> }
      @else {
        @if (!jeton()) { <div class="alert alert-error">Lien incomplet : utilisez le lien reçu par e-mail.</div> }
        @if (erreur()) { <div class="alert alert-error" role="alert"><span class="mi sm">error</span> {{ erreur() }}</div> }
        <form [formGroup]="form" (ngSubmit)="valider()" novalidate>
          <div class="form-group"><label class="form-label" for="p1">Nouveau mot de passe (8 caractères minimum)</label>
            <input id="p1" class="form-control" type="password" formControlName="p1" autocomplete="new-password" /></div>
          <div class="form-group"><label class="form-label" for="p2">Confirmation</label>
            <input id="p2" class="form-control" type="password" formControlName="p2" autocomplete="new-password" />
            @if (form.touched && form.value.p1 !== form.value.p2) { <div class="form-error">Les deux mots de passe diffèrent.</div> }</div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="envoi() || !jeton()">Enregistrer</button>
        </form>
      }
    </div></div>
  `,
})
export class ReinitialisationComponent {
  private site = inject(SiteService);
  jeton = input<string>('');
  form = inject(FormBuilder).nonNullable.group({ p1: ['', [Validators.required, Validators.minLength(8)]], p2: ['', Validators.required] });
  ok = signal(''); erreur = signal(''); envoi = signal(false);
  valider() {
    const v = this.form.getRawValue();
    if (this.form.invalid || v.p1 !== v.p2) { this.form.markAllAsTouched(); return; }
    this.envoi.set(true); this.erreur.set('');
    this.site.reinitialiser(this.jeton(), v.p1).subscribe({
      next: r => { this.envoi.set(false); this.ok.set(r.message); },
      error: e => { this.envoi.set(false); this.erreur.set(messageErreur(e)); },
    });
  }
}
