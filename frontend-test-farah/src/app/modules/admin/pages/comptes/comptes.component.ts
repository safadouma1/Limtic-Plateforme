import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UtilisateurService } from '../../../../core/services/utilisateur.service';
import { AuthService } from '../../../../core/services/auth.service';
import { ROLE_LABELS, Role, User } from '../../../../core/models/user.model';
import { messageErreur } from '../../../../shared/erreur';
import { RapportService } from '../../../../core/services/rapport.service';
import { MembreRef } from '../../../../core/models/rapport.model';

@Component({
  selector: 'app-comptes',
  imports: [FormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Comptes & rôles</h1>
        <p class="page-subtitle">Attribuer les rôles et relier les comptes chercheurs à leur fiche membre</p>
      </div>
    </div>

    @if (error()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ error() }}</div> }

    <div class="card">
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr><th>Utilisateur</th><th>Email</th><th>Rôle</th><th>Fiche membre</th><th>Statut</th><th></th></tr></thead>
          <tbody>
            @for (u of users(); track u.id) {
              <tr>
                <td>
                  <div class="r-cell">
                    <div class="r-avatar">{{ u.prenom.charAt(0) }}{{ u.nom.charAt(0) }}</div>
                    <span style="font-weight:500">{{ u.prenom }} {{ u.nom }}</span>
                    @if (estMoi(u)) { <span class="chip chip-green">vous</span> }
                  </div>
                </td>
                <td class="muted">{{ u.email }}</td>
                <td>
                  <select class="form-control" style="width:auto;padding:6px 10px" [ngModel]="u.role"
                          (ngModelChange)="changerRole(u, $event)" [disabled]="estMoi(u)">
                    @for (r of rolesPour(u); track r) { <option [ngValue]="r">{{ labels[r] }}</option> }
                  </select>
                </td>
                <td>
                  <select class="form-control" style="width:auto;max-width:220px;padding:6px 10px" [ngModel]="u.chercheurId ?? null"
                          (ngModelChange)="lier(u, $event)" [attr.aria-label]="'Fiche membre de ' + u.prenom + ' ' + u.nom">
                    <option [ngValue]="null">—</option>
                    @for (m of membres(); track m.id) { <option [ngValue]="m.id">{{ m.nom }} {{ m.prenom }}</option> }
                  </select>
                </td>
                <td>
                  <label class="row small" style="gap:6px;cursor:pointer;flex-wrap:nowrap">
                    <input type="checkbox" [checked]="u.actif !== false" [disabled]="estMoi(u)" (change)="activer(u, $any($event.target).checked)"
                           [attr.aria-label]="'Compte actif : ' + u.prenom + ' ' + u.nom" />
                    <span class="chip" [class.chip-green]="u.actif !== false">{{ u.actif === false ? 'Désactivé' : 'Actif' }}</span>
                  </label>
                </td>
                <td>
                  <div class="actions">
                    <button class="btn btn-danger btn-icon" title="Supprimer" (click)="supprimer(u)" [disabled]="estMoi(u)">
                      <span class="mi sm">delete</span>
                    </button>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class ComptesComponent implements OnInit {
  private svc = inject(UtilisateurService);
  private auth = inject(AuthService);
  users = signal<User[]>([]);
  error = signal('');
  private rapport = inject(RapportService);
  membres = signal<MembreRef[]>([]);
  labels = ROLE_LABELS;

  ngOnInit() {
    this.charger();
    this.rapport.referentiels().subscribe({ next: r => this.membres.set(r.membres) });
  }

  /** Seul le directeur attribue (ou retire) le rôle Directeur. */
  rolesPour(u: User): Role[] {
    const base: Role[] = ['ADMIN', 'CHERCHEUR', 'MODERATEUR', 'UTILISATEUR'];
    return this.auth.estDirecteur() || u.role === 'DIRECTEUR' ? ['DIRECTEUR', ...base] : base;
  }

  lier(u: User, chercheurId: number | null) {
    this.error.set('');
    this.svc.lierChercheur(u.id, chercheurId).subscribe({
      next: maj => this.users.update(l => l.map(x => x.id === maj.id ? maj : x)),
      error: e => { this.error.set(messageErreur(e)); this.charger(); },
    });
  }

  charger() { this.svc.getAll().subscribe({ next: u => this.users.set(u), error: e => this.error.set(messageErreur(e)) }); }

  estMoi(u: User) { return u.email.toLowerCase() === this.auth.currentUser()?.email.toLowerCase(); }

  changerRole(u: User, role: Role) {
    this.error.set('');
    this.svc.changerRole(u.id, role).subscribe({
      next: maj => this.users.update(l => l.map(x => x.id === maj.id ? maj : x)),
      error: e => { this.error.set(messageErreur(e)); this.charger(); },
    });
  }

  activer(u: User, actif: boolean) {
    this.error.set('');
    this.svc.activer(u.id, actif).subscribe({
      next: maj => this.users.update(l => l.map(x => x.id === maj.id ? maj : x)),
      error: e => { this.error.set(messageErreur(e)); this.charger(); },
    });
  }

  supprimer(u: User) {
    if (!confirm(`Supprimer le compte de ${u.prenom} ${u.nom} ?`)) return;
    this.svc.delete(u.id).subscribe({
      next: () => this.users.update(l => l.filter(x => x.id !== u.id)),
      error: e => this.error.set(messageErreur(e)),
    });
  }
}
