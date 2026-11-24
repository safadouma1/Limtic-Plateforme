import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Chercheur, ChercheurPayload, Publication } from '../models/chercheur.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ChercheurService {
  private http = inject(HttpClient);
  private API  = `${environment.apiUrl}/chercheurs`;

  getAll(): Observable<Chercheur[]>                 { return this.http.get<Chercheur[]>(this.API); }
  getById(id: number): Observable<Chercheur>        { return this.http.get<Chercheur>(`${this.API}/${id}`); }
  publications(id: number): Observable<Publication[]> {
    return this.http.get<Publication[]>(`${this.API}/${id}/publications`);
  }
  rechercher(nom: string): Observable<Chercheur[]>  {
    return this.http.get<Chercheur[]>(`${this.API}/rechercher`, { params: { nom } });
  }
  parDomaine(domaineId: number): Observable<Chercheur[]> {
    return this.http.get<Chercheur[]>(`${this.API}/domaine/${domaineId}`);
  }
  create(data: ChercheurPayload): Observable<Chercheur>             { return this.http.post<Chercheur>(this.API, data); }
  update(id: number, data: ChercheurPayload): Observable<Chercheur> { return this.http.put<Chercheur>(`${this.API}/${id}`, data); }
  delete(id: number): Observable<void>                              { return this.http.delete<void>(`${this.API}/${id}`); }
}
