import { Component, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { filter } from 'rxjs/operators';
import { AuthService } from '../core/services/auth.service';
import { IaService } from '../core/services/ia.service';
import { ROLE_LABELS } from '../core/models/user.model';

@Component({
  selector: 'app-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent implements OnInit {
  auth = inject(AuthService);
  ia = inject(IaService);
  private router = inject(Router);

  menuOuvert = signal(false);
  recherche = '';
  roleLabels = ROLE_LABELS;

  ngOnInit() {
    this.ia.chargerStatus().subscribe();
    // Ferme le menu mobile après chaque navigation
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.menuOuvert.set(false));
  }

  /** La barre de recherche du haut lance la recherche sémantique IA. */
  rechercher() {
    const q = this.recherche.trim();
    if (!q) return;
    this.router.navigate(['/ia/recherche'], { queryParams: { q } });
    this.recherche = '';
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/auth/login']);
  }
}
