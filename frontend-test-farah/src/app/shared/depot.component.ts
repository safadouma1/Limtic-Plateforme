import { Component, inject, input, output, signal } from '@angular/core';
import { SiteService } from '../core/services/site.service';
import { MediaDto } from '../core/models/site.model';
import { messageErreur } from './erreur';

/** Bouton de dépôt de fichiers (photos ou PDF) vers /api/media. */
@Component({
  selector: 'app-depot',
  template: `
    <label class="btn btn-ghost btn-sm depot" [class.disabled]="envoi()">
      <span class="mi sm">{{ envoi() ? 'autorenew' : icone() }}</span> {{ envoi() ? 'Envoi…' : libelle() }}
      <input type="file" [accept]="accept()" [multiple]="multiple()" (change)="choisir($event)" [disabled]="envoi()" />
    </label>
    @if (erreur()) { <div class="form-error">{{ erreur() }}</div> }
  `,
  styles: [`.depot { position:relative; cursor:pointer; } .depot input { position:absolute; inset:0; opacity:0; cursor:pointer; }
    .depot.disabled { opacity:.6; pointer-events:none; } :host { display:inline-flex; flex-direction:column; gap:4px; }`],
})
export class DepotComponent {
  private site = inject(SiteService);
  accept = input('image/*');
  multiple = input(false);
  libelle = input('Choisir un fichier');
  icone = input('upload');
  deposes = output<MediaDto[]>();
  envoi = signal(false);
  erreur = signal('');

  choisir(ev: Event) {
    const el = ev.target as HTMLInputElement;
    const fichiers = Array.from(el.files ?? []);
    el.value = '';
    if (!fichiers.length) return;
    const trop = fichiers.find(f => f.size > 10 * 1024 * 1024);
    if (trop) { this.erreur.set(`« ${trop.name} » dépasse 10 Mo.`); return; }
    this.envoi.set(true); this.erreur.set('');
    this.site.deposer(fichiers).subscribe({
      next: m => { this.envoi.set(false); this.deposes.emit(m); },
      error: e => { this.envoi.set(false); this.erreur.set(messageErreur(e)); },
    });
  }
}
