import { AfterViewInit, Component, ElementRef, effect, input, model, viewChild } from '@angular/core';

/**
 * Éditeur de texte riche léger (WYSIWYG) : gras, italique, titres, listes, liens, citation.
 * Le HTML produit est de nouveau assaini côté serveur (jsoup) avant stockage.
 */
@Component({
  selector: 'app-editeur-riche',
  template: `
    <div class="ed" [class.focus]="focus">
      <div class="ed-bar" role="toolbar" [attr.aria-label]="'Mise en forme : ' + label()">
        @for (b of boutons; track b.cmd + (b.arg ?? '')) {
          <button type="button" class="btn btn-ghost btn-sm" [title]="b.t" [attr.aria-label]="b.t" (mousedown)="$event.preventDefault()" (click)="exec(b.cmd, b.arg)">
            <span class="mi sm">{{ b.i }}</span>
          </button>
        }
        <button type="button" class="btn btn-ghost btn-sm" title="Insérer un lien" aria-label="Insérer un lien" (mousedown)="$event.preventDefault()" (click)="lien()"><span class="mi sm">link</span></button>
      </div>
      <div #zone class="ed-zone rich" contenteditable="true" role="textbox" aria-multiline="true" [attr.aria-label]="label()"
           (input)="sync()" (blur)="focus = false; sync()" (focus)="focus = true"></div>
    </div>
  `,
  styles: [`
    .ed { border:1px solid var(--border-strong); border-radius:var(--radius-sm); background:var(--bg-soft); }
    .ed.focus { box-shadow:var(--ring); border-color:var(--accent); }
    .ed-bar { display:flex; gap:2px; flex-wrap:wrap; padding:4px; border-bottom:1px solid var(--border); }
    .ed-bar .btn { padding:4px 6px; }
    .ed-zone { min-height:180px; padding:12px 14px; outline:none; }
    .ed-zone:empty::before { content:'Saisissez le texte…'; color:var(--faint); }
  `],
})
export class EditeurRicheComponent implements AfterViewInit {
  valeur = model<string | null>('');
  label = input('Texte');
  zone = viewChild.required<ElementRef<HTMLDivElement>>('zone');
  focus = false;
  private dernier: string | null = null;

  boutons = [
    { cmd: 'bold', i: 'format_bold', t: 'Gras' }, { cmd: 'italic', i: 'format_italic', t: 'Italique' },
    { cmd: 'underline', i: 'format_underlined', t: 'Souligné' }, { cmd: 'formatBlock', arg: 'h3', i: 'title', t: 'Intertitre' },
    { cmd: 'insertUnorderedList', i: 'format_list_bulleted', t: 'Liste à puces' },
    { cmd: 'insertOrderedList', i: 'format_list_numbered', t: 'Liste numérotée' },
    { cmd: 'formatBlock', arg: 'blockquote', i: 'format_quote', t: 'Citation' },
    { cmd: 'formatBlock', arg: 'p', i: 'notes', t: 'Paragraphe' },
    { cmd: 'removeFormat', i: 'format_clear', t: 'Effacer la mise en forme' },
  ] as { cmd: string; arg?: string; i: string; t: string }[];

  constructor() {
    effect(() => {
      const v = this.valeur() ?? '';
      const el = this.zone()?.nativeElement;
      if (el && v !== this.dernier) { el.innerHTML = v; this.dernier = v; }
    });
  }
  ngAfterViewInit() { this.zone().nativeElement.innerHTML = this.valeur() ?? ''; this.dernier = this.valeur() ?? ''; }

  exec(cmd: string, arg?: string) { document.execCommand(cmd, false, arg); this.sync(); }
  lien() {
    const url = prompt('Adresse du lien (https://…)');
    if (url && /^https?:\/\//i.test(url)) this.exec('createLink', url);
  }
  sync() {
    const html = this.zone().nativeElement.innerHTML;
    this.dernier = html;
    this.valeur.set(html === '<br>' ? '' : html);
  }
}
