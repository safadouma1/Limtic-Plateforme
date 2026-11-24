import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Chercheur } from '../models/chercheur.model';
import {
  Accueil, AxeDetail, AxeResume, Alertes, Captcha, ContactMessage, Directeur, Encadrements, Etudiant, EvenementDetail,
  EvenementResume, FiltresPublications, MediaDto, PagePublications, SiteParametres, SitePublic,
} from '../models/site.model';

/** Front-office public (Partie 1) et back-office du site. */
@Injectable({ providedIn: 'root' })
export class SiteService {
  private http = inject(HttpClient);
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private P = `${environment.apiUrl}/public`;
  /** Racine du serveur (les URL de fichiers renvoyées par l'API commencent par /api/…). */
  readonly serveur = environment.apiUrl.replace(/\/api$/, '');

  readonly site = signal<SitePublic | null>(null);
  private site$?: Observable<SitePublic>;

  chargerSite(): Observable<SitePublic> {
    this.site$ ??= this.http.get<SitePublic>(`${this.P}/site`).pipe(tap(s => this.site.set(s)), shareReplay(1));
    return this.site$;
  }

  /** URL absolue d'un fichier du site (photo, PDF). */
  url(chemin: string | null | undefined): string | null {
    if (!chemin) return null;
    return /^https?:/.test(chemin) ? chemin : this.serveur + chemin;
  }

  accueil() { return this.http.get<Accueil>(`${this.P}/accueil`); }
  directeur() { return this.http.get<Directeur>(`${this.P}/directeur`); }
  axes() { return this.http.get<AxeResume[]>(`${this.P}/axes`); }
  axe(id: number) { return this.http.get<AxeDetail>(`${this.P}/axes/${id}`); }
  evenements() { return this.http.get<EvenementResume[]>(`${this.P}/evenements`); }
  evenement(id: number) { return this.http.get<EvenementDetail>(`${this.P}/evenements/${id}`); }
  doctorants() { return this.http.get<Etudiant[]>(`${this.P}/doctorants`); }
  masteriens() { return this.http.get<Etudiant[]>(`${this.P}/masteriens`); }
  encadrements(chercheurId: number) { return this.http.get<Encadrements>(`${this.P}/chercheurs/${chercheurId}/encadrements`); }

  private params(f: FiltresPublications): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(f)) if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    return p;
  }
  publications(f: FiltresPublications) { return this.http.get<PagePublications>(`${this.P}/publications`, { params: this.params(f) }); }
  lienExport(format: 'bibtex' | 'csv', f: FiltresPublications): string {
    const { page, taille, ...reste } = f;
    return `${this.P}/publications/export?${this.params({ ...reste, format } as FiltresPublications).toString()}`;
  }

  captcha() { return this.http.get<Captcha>(`${this.P}/captcha`); }
  contact(m: { nom: string; email: string; sujet: string; message: string; captcha: string; jeton: string; siteWeb: string }) {
    return this.http.post<{ message: string }>(`${this.P}/contact`, m);
  }

  // ── Back-office ──
  parametres() { return this.http.get<SiteParametres>(`${environment.apiUrl}/site`); }
  enregistrerParametres(p: SiteParametres) {
    return this.http.put<SiteParametres>(`${environment.apiUrl}/site`, p).pipe(tap(() => { this.site$ = undefined; this.chargerSite().subscribe(); }));
  }
  messages() { return this.http.get<ContactMessage[]>(`${environment.apiUrl}/contact`); }
  marquerLu(id: number, lu: boolean) { return this.http.patch<ContactMessage>(`${environment.apiUrl}/contact/${id}`, { lu }); }
  supprimerMessage(id: number) { return this.http.delete<void>(`${environment.apiUrl}/contact/${id}`); }
  alertes() { return this.http.get<Alertes>(`${environment.apiUrl}/admin/alertes`); }
  exporterMembres(type: 'chercheurs' | 'doctorants' | 'masteriens') {
    return this.http.get(`${environment.apiUrl}/admin/export/${type}`, { responseType: 'blob' });
  }
  deposer(fichiers: File[]) {
    const fd = new FormData();
    for (const f of fichiers) fd.append('fichiers', f);
    return this.http.post<MediaDto[]>(`${environment.apiUrl}/media`, fd);
  }
  maFiche() { return this.http.get<Chercheur>(`${environment.apiUrl}/me/fiche`); }
  enregistrerMaFiche(f: Partial<Chercheur> & { domaineId?: number | null }) { return this.http.put<Chercheur>(`${environment.apiUrl}/me/fiche`, f); }

  motDePasseOublie(email: string) { return this.http.post<{ message: string }>(`${environment.apiUrl}/auth/forgot`, { email }); }
  reinitialiser(jeton: string, password: string) { return this.http.post<{ message: string }>(`${environment.apiUrl}/auth/reset`, { jeton, password }); }

  enregistrerBlob(b: Blob, nom: string) {
    if (!this.isBrowser) return;
    const url = URL.createObjectURL(b);
    const a = document.createElement('a');
    a.href = url; a.download = nom; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }
}
