import { AuteurRef, Chercheur, Publication, TypePublication } from './chercheur.model';

export interface SitePublic {
  nomCourt: string; nomComplet: string | null; presentation: string | null; couleurPrincipale: string | null;
  adresse: string | null; telephone: string | null; fax: string | null; email: string | null; carteRequete: string | null;
  facebookUrl: string | null; linkedinUrl: string | null; youtubeUrl: string | null; siteIsiUrl: string | null;
  seoTitre: string | null; seoDescription: string | null; seoMotsCles: string | null;
  universite: string | null; etablissement: string | null; code: string | null;
}

export interface SiteParametres {
  id: number; nomCourt: string; nomComplet: string | null; presentation: string | null; couleurPrincipale: string | null;
  motDirecteur: string | null; titreDirecteur: string | null; axesPrioritairesDirecteur: string | null;
  adresse: string | null; telephone: string | null; fax: string | null; email: string | null; carteRequete: string | null;
  facebookUrl: string | null; linkedinUrl: string | null; youtubeUrl: string | null; siteIsiUrl: string | null;
  seoTitre: string | null; seoDescription: string | null; seoMotsCles: string | null;
}

export type EtatEvenement = 'A_VENIR' | 'EN_COURS' | 'TERMINE';
export type TypeEvenement = 'SEMINAIRE' | 'JOURNEE_RECHERCHE' | 'ATELIER' | 'CONFERENCE';
export const TYPES_EVENEMENT: Record<TypeEvenement, string> = {
  CONFERENCE: 'Conférence', JOURNEE_RECHERCHE: 'Journée de recherche', SEMINAIRE: 'Séminaire', ATELIER: 'Atelier',
};
export const ETATS_EVENEMENT: Record<EtatEvenement, { l: string; chip: string }> = {
  A_VENIR: { l: 'À venir', chip: 'chip-green' }, EN_COURS: { l: 'En cours', chip: 'chip-gold' }, TERMINE: { l: 'Passé', chip: 'chip-purple' },
};

export interface EvenementResume {
  id: number; titre: string; type: TypeEvenement; dateDebut: string | null; dateFin: string | null; annee: number | null;
  lieu: string | null; etat: EtatEvenement; photoUrl: string | null;
}
export interface Intervenant { nom: string; affiliation: string | null; titreIntervention: string | null; }
export interface EvenementDetail extends EvenementResume {
  organisateurs: string | null; description: string | null; programme: string | null; programmeUrl: string | null;
  intervenants: Intervenant[]; photos: { url: string; legende: string | null }[];
}

export interface Statistiques { chercheurs: number; publications: number; doctorants: number; masteriens: number; projetsEnCours: number; evenements: number; }
export interface AxeResume { id: number; nom: string; description: string | null; responsables: AuteurRef[]; nbChercheurs: number; nbPublications: number; }
export interface Accueil {
  site: SitePublic; stats: Statistiques; prochainsEvenements: EvenementResume[];
  dernieresPublications: Publication[]; axes: AxeResume[];
}
export interface ProjetDto { id: number; intitule: string; type: string; financeur: string | null; dateDebut: string | null; dateFin: string | null; }
export interface AxeDetail { axe: AxeResume; membres: Chercheur[]; publications: Publication[]; projets: ProjetDto[]; }

export interface Etudiant {
  id: number; nom: string; prenom: string; sujet: string | null; encadrants: AuteurRef[]; encadrantsTexte: string | null;
  anneeInscription: number | null; promotion: string | null; statut: string; dateSoutenance: string | null; mention: string | null;
  photoUrl: string | null; etablissement: string | null;
}
export interface Encadrements { doctorants: Etudiant[]; masteriens: Etudiant[]; }

export interface Directeur {
  chercheur: Chercheur; titre: string | null; fonction: string | null; motDirecteur: string | null; axesPrioritaires: string | null;
  email: string | null; telephone: string | null; publicationsRecentes: Publication[];
}

export interface PagePublications { contenu: Publication[]; page: number; taille: number; total: number; pages: number; annees: number[]; }
export interface FiltresPublications {
  q?: string; type?: TypePublication | ''; annee?: number | null; auteur?: number | null; axe?: number | null;
  classement?: string; tri?: string; page?: number; taille?: number;
}

export interface Captcha { question: string; jeton: string; }
export interface ContactMessage { id: number; nom: string; email: string; sujet: string; message: string; lu: boolean; creeLe: string; }
export interface MediaDto { id: number; url: string; nom: string; contentType: string; taille: number; }

export interface Alertes {
  enAttente: { categorie: string; libelle: string; nombre: number; lien: string }[];
  prochainsEvenements: EvenementResume[];
  messagesNonLus: number;
  publicationsParType: Record<string, number>;
  doctorants: number; masteriens: number; evenements: number;
}
