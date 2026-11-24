import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuditLog, Campagne, CompteRendu, Completude, Controle, Fiche, LigneBudget, Lien, RapportData, Recapitulatif,
  Referentiels, Regle, Resultat, ResumeRapport, StatutCampagne, StructureIdentite, Telechargement, VersionRapport,
} from '../models/rapport.model';

/** Accès à l'API du module « Rapport d'activité » (cahier des charges §17.7). */
@Injectable({ providedIn: 'root' })
export class RapportService {
  private http = inject(HttpClient);
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private R = `${environment.apiUrl}/report`;
  private FORMS = `${environment.apiUrl}/forms`;

  /** Année de référence affichée dans tout le module. */
  readonly annee = signal<number>(this.anneeMemorisee());
  readonly campagnes = signal<Campagne[]>([]);

  choisirAnnee(a: number) {
    this.annee.set(a);
    if (this.isBrowser) try { localStorage.setItem('limtic_annee', String(a)); } catch { /* stockage indisponible */ }
  }

  private anneeMemorisee(): number {
    try {
      const v = this.isBrowser ? Number(localStorage.getItem('limtic_annee')) : 0;
      return v > 2000 ? v : new Date().getFullYear() - 1;
    } catch { return new Date().getFullYear() - 1; }
  }

  campagne(): Campagne | undefined { return this.campagnes().find(c => c.annee === this.annee()); }

  chargerCampagnes(): Observable<Campagne[]> {
    return this.http.get<Campagne[]>(`${this.R}/campaigns`).pipe(tap(l => {
      this.campagnes.set(l);
      if (l.length && !l.some(c => c.annee === this.annee())) this.choisirAnnee(l[0].annee);
    }));
  }
  creerCampagne(c: Partial<Campagne>) { return this.http.post<Campagne>(`${this.R}/campaigns`, c); }
  changerStatut(annee: number, statut: StatutCampagne) { return this.http.patch<Campagne>(`${this.R}/campaigns/${annee}/status`, { statut }); }
  parametres(annee: number, c: Partial<Campagne>) { return this.http.put<Campagne>(`${this.R}/campaigns/${annee}/parametres`, c); }
  completude(annee: number) { return this.http.get<Completude>(`${this.R}/${annee}/completude`); }

  recapitulatif(annee: number) { return this.http.get<Recapitulatif>(`${this.R}/${annee}/summary`); }
  controles(annee: number) { return this.http.get<Controle[]>(`${this.R}/${annee}/checks`); }
  apercu(annee: number, inclureSoumis = false) {
    return this.http.get<RapportData>(`${this.R}/${annee}/preview`, { params: { inclureSoumis } });
  }

  generer(annee: number, finale: boolean, withCin: boolean, includeSoumis: boolean) {
    const params = new HttpParams().set('final', finale).set('withCin', withCin).set('includeSoumis', includeSoumis);
    return this.http.post<VersionRapport>(`${this.R}/${annee}/generate`, null, { params });
  }
  versions(annee: number) { return this.http.get<VersionRapport[]>(`${this.R}/${annee}/versions`); }
  lien(versionId: number, format: string, tableau?: string | null) {
    let params = new HttpParams().set('format', format);
    if (tableau) params = params.set('tableau', tableau);
    return this.http.get<Lien>(`${this.R}/files/${versionId}/download`, { params });
  }
  /** Ouvre un lien signé (valable quelques minutes) : le navigateur télécharge le fichier. */
  ouvrirLien(l: Lien) {
    if (!this.isBrowser) return;
    const a = document.createElement('a');
    a.href = environment.apiUrl.replace(/\/api$/, '') + l.url;
    a.download = l.nomFichier;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }
  telechargements() { return this.http.get<Telechargement[]>(`${this.R}/telechargements`); }
  journal() { return this.http.get<AuditLog[]>(`${this.R}/audit`); }

  identite() { return this.http.get<StructureIdentite>(`${this.R}/identite`); }
  enregistrerIdentite(i: StructureIdentite) { return this.http.put<StructureIdentite>(`${this.R}/identite`, i); }
  resume(annee: number) { return this.http.get<ResumeRapport>(`${this.R}/${annee}/resume`); }
  enregistrerResume(annee: number, r: ResumeRapport) { return this.http.put<ResumeRapport>(`${this.R}/${annee}/resume`, r); }
  proposition(annee: number) { return this.http.get<{ bilan: string; thematiques: string }>(`${this.R}/${annee}/resume/proposition`); }
  budget(annee: number) { return this.http.get<LigneBudget[]>(`${this.R}/${annee}/budget`); }
  enregistrerBudget(annee: number, l: LigneBudget[]) { return this.http.put<LigneBudget[]>(`${this.R}/${annee}/budget`, l); }

  correspondances() { return this.http.get<Regle[]>(`${this.R}/correspondances`); }
  enregistrerCorrespondances(table: string, valeurs: Record<string, string>) { return this.http.put<Regle[]>(`${this.R}/correspondances/${table}`, valeurs); }
  gabarit() { return this.http.get<{ actif: string; versions: { version: string; actif: boolean }[] }>(`${this.R}/gabarit`); }
  enregistrerGabarit(json: string) {
    return this.http.put<{ actif: string; versions: { version: string; actif: boolean }[] }>(`${this.R}/gabarit`, json, { headers: { 'Content-Type': 'application/json' } });
  }
  referentiels() { return this.http.get<Referentiels>(`${this.R}/referentiels`); }

  // ── Formulaires F3 à F17 ──
  lister(categorie: string, annee?: number | null) {
    let params = new HttpParams();
    if (annee) params = params.set('annee', annee);
    return this.http.get<Fiche[]>(`${this.FORMS}/${categorie}`, { params });
  }
  creer(categorie: string, f: Partial<Fiche>) { return this.http.post<Resultat>(`${this.FORMS}/${categorie}`, f); }
  modifier(categorie: string, id: number, f: Partial<Fiche>) { return this.http.put<Resultat>(`${this.FORMS}/${categorie}/${id}`, f); }
  supprimer(categorie: string, id: number) { return this.http.delete<void>(`${this.FORMS}/${categorie}/${id}`); }
  soumettre(categorie: string, id: number) { return this.http.post<Resultat>(`${this.FORMS}/${categorie}/${id}/soumettre`, null); }
  valider(categorie: string, id: number) { return this.http.post<Resultat>(`${this.FORMS}/${categorie}/${id}/valider`, null); }
  rejeter(categorie: string, id: number, motif: string) { return this.http.post<Resultat>(`${this.FORMS}/${categorie}/${id}/rejeter`, { motif }); }
  cin(categorie: string, id: number) { return this.http.get<{ cin: string }>(`${this.FORMS}/${categorie}/${id}/cin`); }

  // ── Import ──
  importer(categorie: string, fichier: File, annee?: number) {
    const fd = new FormData();
    fd.append('fichier', fichier);
    let params = new HttpParams();
    if (annee) params = params.set('annee', annee);
    return this.http.post<CompteRendu>(`${environment.apiUrl}/import/${categorie}`, fd, { params });
  }
  jeuEssai() { return this.http.post<CompteRendu[]>(`${environment.apiUrl}/import/jeu-essai-2025`, null); }

  // ── Fiche d'activité ──
  ficheActivite(annee: number, format: 'pdf' | 'docx') {
    return this.http.get(`${environment.apiUrl}/me/activity`, { params: { year: annee, format }, responseType: 'blob' });
  }
  enregistrerBlob(b: Blob, nom: string) {
    if (!this.isBrowser) return;
    const url = URL.createObjectURL(b);
    const a = document.createElement('a');
    a.href = url; a.download = nom;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }
}
