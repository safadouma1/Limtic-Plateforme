import { Component, computed, input } from '@angular/core';

/** Badge coloré du classement de la venue (§3.7.3) : Q1 en vert, CORE A* en or… */
@Component({
  selector: 'app-classement',
  template: `
    @if (classement()) {
      <span class="rank" [class]="'rank ' + classe()" [title]="titre()">{{ libelle() }}</span>
    }
    @if (facteurImpact()) { <span class="rank autre" title="Facteur d'impact (JCR)">IF {{ facteurImpact() }}</span> }
  `,
  styles: [':host { display:inline-flex; gap:6px; flex-wrap:wrap; }'],
})
export class ClassementBadgeComponent {
  classement = input<string | null | undefined>();
  source = input<string | null | undefined>();
  facteurImpact = input<number | null | undefined>();

  private val = computed(() => (this.classement() ?? '').trim().toUpperCase());
  classe = computed(() => {
    const v = this.val();
    if (/^Q[1-4]$/.test(v)) return v.toLowerCase();
    if (v === 'A*' || v === 'A+') return 'astar';
    if (['A', 'B', 'C'].includes(v)) return v.toLowerCase();
    return 'autre';
  });
  libelle = computed(() => {
    const v = this.val();
    if (/^[ABC]\*?$/.test(v)) return 'CORE ' + v;
    return v;
  });
  titre = computed(() => 'Classement de la venue' + (this.source() ? ' (' + this.source() + ')' : ''));
}
