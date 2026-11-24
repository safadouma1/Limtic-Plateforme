import { Component, computed, inject, input } from '@angular/core';
import { SiteService } from '../core/services/site.service';

/** Photo de profil ou initiales. */
@Component({
  selector: 'app-avatar',
  template: `
    @if (src()) { <img class="avatar" [class.lg]="grand()" [src]="src()" [alt]="'Photo de ' + nom()" loading="lazy" /> }
    @else { <div class="avatar" [class.lg]="grand()" aria-hidden="true">{{ initiales() }}</div> }
  `,
  styles: [':host { display:contents; }'],
})
export class AvatarComponent {
  private site = inject(SiteService);
  photo = input<string | null | undefined>();
  nom = input<string>('');
  grand = input(false);
  src = computed(() => this.site.url(this.photo()));
  initiales = computed(() => this.nom().split(/\s+/).filter(Boolean).slice(0, 2).map(m => m[0]).join('').toUpperCase());
}
