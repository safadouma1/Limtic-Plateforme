import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { SiteService } from '../core/services/site.service';

/** Métadonnées SEO des pages publiques (§4.3.6) : titre, description et mots-clés paramétrés par l'administrateur. */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private title = inject(Title);
  private meta = inject(Meta);
  private site = inject(SiteService);

  definir(titre?: string | null, description?: string | null) {
    const s = this.site.site();
    const nom = s?.nomCourt ?? 'LIMTIC';
    this.title.setTitle(titre ? `${titre} · ${nom}` : (s?.seoTitre ?? nom));
    const d = (description ?? s?.seoDescription ?? '').replace(/\s+/g, ' ').trim().slice(0, 300);
    if (d) this.meta.updateTag({ name: 'description', content: d });
    if (s?.seoMotsCles) this.meta.updateTag({ name: 'keywords', content: s.seoMotsCles });
    this.meta.updateTag({ property: 'og:title', content: titre ?? s?.seoTitre ?? nom });
    if (d) this.meta.updateTag({ property: 'og:description', content: d });
  }
}
