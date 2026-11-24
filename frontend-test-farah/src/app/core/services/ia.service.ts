import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface SearchResult {
  id: number;
  titre: string;
  resume?: string | null;
  score: number;
  motsCles: string[];
  auteurs: string[];
  annee?: number | null;
}

/** mode "semantique" = embeddings Gemini ; "lexical" = TF-IDF local (si Gemini n'est pas configuré). */
export interface SearchResponse { mode: 'semantique' | 'lexical'; resultats: SearchResult[]; }

export interface ChatMessage { role: 'user' | 'assistant'; content: string; }

export interface IaStatus { geminiConfigure: boolean; modele: string; modeleEmbedding: string; }

/**
 * Module IA : tous les appels passent par le backend Spring Boot (/api/ia), qui appelle Google Gemini.
 * Aucune donnée simulée : en cas d'erreur, l'appelant reçoit l'erreur et l'affiche.
 */
@Injectable({ providedIn: 'root' })
export class IaService {
  private http = inject(HttpClient);
  private API  = `${environment.apiUrl}/ia`;

  /** État du module IA (null = inconnu / backend injoignable). */
  readonly status = signal<IaStatus | null>(null);

  chargerStatus(): Observable<IaStatus | null> {
    return this.http.get<IaStatus>(`${this.API}/status`).pipe(
      tap(s => this.status.set(s)),
      catchError(() => { this.status.set(null); return of(null); }),
    );
  }

  chat(message: string, historique: ChatMessage[]): Observable<string> {
    return this.http.post<{ reponse: string }>(`${this.API}/chat`, { message, historique }).pipe(map(r => r.reponse));
  }

  smartSearch(query: string): Observable<SearchResponse> {
    return this.http.post<SearchResponse>(`${this.API}/search`, { query });
  }

  recommander(publicationId: number): Observable<SearchResponse> {
    return this.http.get<SearchResponse>(`${this.API}/recommend/${publicationId}`);
  }

  extraireMotsCles(texte: string): Observable<string[]> {
    return this.http.post<{ motsCles: string[] }>(`${this.API}/keywords`, { texte }).pipe(map(r => r.motsCles));
  }

  resumer(texte: string): Observable<string> {
    return this.http.post<{ resume: string }>(`${this.API}/summarize`, { texte }).pipe(map(r => r.resume));
  }

  resumerPublication(id: number): Observable<string> {
    return this.http.post<{ resume: string }>(`${this.API}/summarize/${id}`, {}).pipe(map(r => r.resume));
  }

  tendances(): Observable<string> {
    return this.http.post<{ resume: string }>(`${this.API}/trends`, {}).pipe(map(r => r.resume));
  }
}
