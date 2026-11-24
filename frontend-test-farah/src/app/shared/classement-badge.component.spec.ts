import { TestBed } from '@angular/core/testing';
import { ClassementBadgeComponent } from './classement-badge.component';

function rendre(classement: string | null, facteurImpact: number | null = null) {
  const f = TestBed.createComponent(ClassementBadgeComponent);
  f.componentRef.setInput('classement', classement);
  f.componentRef.setInput('facteurImpact', facteurImpact);
  f.detectChanges();
  return f.nativeElement as HTMLElement;
}

describe('ClassementBadgeComponent', () => {
  it('affiche un quartile Scimago avec sa couleur', () => {
    const el = rendre('q1');
    const badge = el.querySelector('.rank')!;
    expect(badge.textContent?.trim()).toBe('Q1');
    expect(badge.classList).toContain('q1');
  });

  it('préfixe les rangs CORE et met A* en évidence', () => {
    const el = rendre('A*');
    const badge = el.querySelector('.rank')!;
    expect(badge.textContent?.trim()).toBe('CORE A*');
    expect(badge.classList).toContain('astar');
  });

  it("affiche le facteur d'impact et rien quand le classement est vide", () => {
    const el = rendre(null, 4.2);
    const badges = el.querySelectorAll('.rank');
    expect(badges.length).toBe(1);
    expect(badges[0].textContent?.trim()).toBe('IF 4.2');
  });
});
