import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Publication, PublicationPayload } from '../models/chercheur.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PublicationService {
  private http = inject(HttpClient);
  private API  = `${environment.apiUrl}/publications`;

  getAll(): Observable<Publication[]>            { return this.http.get<Publication[]>(this.API); }
  getById(id: number): Observable<Publication>   { return this.http.get<Publication>(`${this.API}/${id}`); }
  create(data: PublicationPayload): Observable<Publication>             { return this.http.post<Publication>(this.API, data); }
  update(id: number, data: PublicationPayload): Observable<Publication> { return this.http.put<Publication>(`${this.API}/${id}`, data); }
  delete(id: number): Observable<void>           { return this.http.delete<void>(`${this.API}/${id}`); }
}
