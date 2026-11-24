import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { LoginRequest, RegisterRequest, Role, User } from '../models/user.model';
import { environment } from '../../../environments/environment';

const STORAGE_KEY = 'limtic_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly API = `${environment.apiUrl}/auth`;

  private _user = signal<User | null>(this.loadFromStorage());

  readonly currentUser = this._user.asReadonly();
  readonly isLoggedIn  = computed(() => !!this._user()?.token);
  readonly userRole    = computed(() => this._user()?.role ?? null);
  readonly initials    = computed(() => {
    const u = this._user();
    return u ? ((u.prenom?.charAt(0) ?? '') + (u.nom?.charAt(0) ?? '')).toUpperCase() : '';
  });

  /** Le directeur (super-administrateur) a aussi tous les droits d'administrateur. */
  hasRole(roles: Role[]): boolean {
    const role = this._user()?.role;
    if (!role) return false;
    return roles.includes(role) || (role === 'DIRECTEUR' && roles.includes('ADMIN'));
  }

  /** Accès au module Rapport d'activité : directeur, administrateur, chercheur. */
  readonly accesRapport = computed(() => ['DIRECTEUR', 'ADMIN', 'CHERCHEUR'].includes(this._user()?.role ?? ''));
  readonly estDirecteur = computed(() => this._user()?.role === 'DIRECTEUR');
  readonly estAdmin = computed(() => ['DIRECTEUR', 'ADMIN'].includes(this._user()?.role ?? ''));

  login(data: LoginRequest): Observable<User> {
    return this.http.post<User>(`${this.API}/login`, data).pipe(tap(u => this.saveUser(u)));
  }

  register(data: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.API}/register`, data).pipe(tap(u => this.saveUser(u)));
  }

  logout(): void {
    if (this.isBrowser) localStorage.removeItem(STORAGE_KEY);
    this._user.set(null);
  }

  private saveUser(user: User): void {
    if (this.isBrowser) localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  private loadFromStorage(): User | null {
    if (!this.isBrowser) return null;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const user: User | null = raw ? JSON.parse(raw) : null;
      if (!user?.token || this.isExpired(user.token)) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return user;
    } catch {
      return null;
    }
  }

  /** Lecture (non vérifiée) de la date d'expiration du JWT ; la vraie vérification est faite par le backend. */
  private isExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}
