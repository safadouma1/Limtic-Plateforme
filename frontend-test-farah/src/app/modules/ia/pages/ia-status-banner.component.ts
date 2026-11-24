import { Component, inject } from '@angular/core';
import { IaService } from '../../../core/services/ia.service';

/** Bandeau affiché sur les pages IA quand Gemini n'est pas configuré côté backend. */
@Component({
  selector: 'app-ia-status-banner',
  template: `
    @if (ia.status(); as s) {
      @if (!s.geminiConfigure) {
        <div class="alert alert-warn">
          <span class="mi sm">key_off</span>
          <div>
            <strong>Gemini n'est pas configuré.</strong> Définissez la variable d'environnement
            <code>GEMINI_API_KEY</code> avant de lancer le backend (clé gratuite sur Google AI Studio).
            {{ note }}
          </div>
        </div>
      }
    } @else {
      <div class="alert alert-error"><span class="mi sm">cloud_off</span> Backend injoignable : démarrez l'API Spring Boot (port 8080).</div>
    }
  `,
})
export class IaStatusBannerComponent {
  ia = inject(IaService);
  note = '';
}
