import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { ROLE_LABELS } from '../../../../core/models/user.model';

@Component({
  selector: 'app-profil',
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Mon profil</h1>
        <p class="page-subtitle">Vos informations de compte</p>
      </div>
    </div>
    @if (auth.currentUser(); as u) {
      <div class="card card-body" style="max-width:520px">
        <div class="r-cell mb-16">
          <div class="r-avatar lg brand">{{ auth.initials() }}</div>
          <div>
            <div style="font-size:17px;font-weight:600">{{ u.prenom }} {{ u.nom }}</div>
            <div class="muted small">{{ u.email }}</div>
            <span class="chip chip-purple mt-8">{{ roles[u.role] }}</span>
          </div>
        </div>
        <p class="small muted">Pour changer de rôle, contactez un administrateur (menu « Comptes & rôles »).</p>
        <button class="btn btn-danger mt-16" (click)="logout()"><span class="mi sm">logout</span> Se déconnecter</button>
      </div>
    }
  `,
})
export class ProfilComponent {
  auth = inject(AuthService);
  private router = inject(Router);
  roles = ROLE_LABELS;
  logout() { this.auth.logout(); this.router.navigate(['/auth/login']); }
}
