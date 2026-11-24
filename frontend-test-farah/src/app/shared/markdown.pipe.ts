import { Pipe, PipeTransform } from '@angular/core';

/**
 * Mini-rendu Markdown (gras, italique, code, titres, listes, paragraphes) pour les réponses de Gemini.
 * Le texte est d'abord échappé : aucun HTML provenant du modèle n'est interprété.
 * Le résultat passe ensuite par l'assainissement Angular de [innerHTML].
 */
@Pipe({ name: 'markdown' })
export class MarkdownPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '';
    const esc = value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const inline = (s: string) => s
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>');

    const out: string[] = [];
    let liste: 'ul' | 'ol' | null = null;
    const fermer = () => { if (liste) { out.push(`</${liste}>`); liste = null; } };

    for (const ligne of esc.split('\n')) {
      const l = ligne.trimEnd();
      let m: RegExpMatchArray | null;
      if ((m = l.match(/^\s*[-*•]\s+(.*)$/))) {
        if (liste !== 'ul') { fermer(); out.push('<ul>'); liste = 'ul'; }
        out.push(`<li>${inline(m[1])}</li>`);
      } else if ((m = l.match(/^\s*\d+[.)]\s+(.*)$/))) {
        if (liste !== 'ol') { fermer(); out.push('<ol>'); liste = 'ol'; }
        out.push(`<li>${inline(m[1])}</li>`);
      } else if ((m = l.match(/^#{1,6}\s+(.*)$/))) {
        fermer(); out.push(`<h4>${inline(m[1])}</h4>`);
      } else if (l.trim() === '') {
        fermer();
      } else {
        fermer(); out.push(`<p>${inline(l)}</p>`);
      }
    }
    fermer();
    return out.join('');
  }
}
