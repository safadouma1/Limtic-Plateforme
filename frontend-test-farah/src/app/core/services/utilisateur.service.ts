import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Role, User } from '../models/user.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UtilisateurService {
  private http = inject(HttpClient);
  private API  = `${environment.apiUrl}/utilisateurs`;

  getAll(): Observable<User[]>                         { return this.http.get<User[]>(this.API); }
  changerRole(id: number, role: Role): Observable<User> { return this.http.put<User>(`${this.API}/${id}/role`, { role }); }
  lierChercheur(id: number, chercheurId: number | null): Observable<User> {
    return this.http.put<User>(`${this.API}/${id}/chercheur`, { chercheurId });
  }
  activer(id: number, actif: boolean): Observable<User> { return this.http.put<User>(`${this.API}/${id}/actif`, { actif }); }
  delete(id: number): Observable<void>                  { return this.http.delete<void>(`${this.API}/${id}`); }
}
