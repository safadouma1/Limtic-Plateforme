import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Domaine } from '../models/chercheur.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class DomaineService {
  private http = inject(HttpClient);
  private API  = `${environment.apiUrl}/domaines`;

  getAll(): Observable<Domaine[]>               { return this.http.get<Domaine[]>(this.API); }
  getById(id: number): Observable<Domaine>      { return this.http.get<Domaine>(`${this.API}/${id}`); }
  create(data: { nom: string; description?: string | null; responsableIds?: number[] }): Observable<Domaine> {
    return this.http.post<Domaine>(this.API, data);
  }
  update(id: number, data: { nom: string; description?: string | null; responsableIds?: number[] }): Observable<Domaine> {
    return this.http.put<Domaine>(`${this.API}/${id}`, data);
  }
  delete(id: number): Observable<void>          { return this.http.delete<void>(`${this.API}/${id}`); }
}
