export interface DomaineRef { id: number; nom: string; }

export interface Domaine {
  id: number;
  nom: string;
  description?: string | null;
  nbChercheurs: number;
  nbPublications: number;
  responsables?: AuteurRef[];
}

export type StatutMembre = 'ACTIF' | 'RETRAITE' | 'MUTE' | 'DEMISSIONNAIRE' | 'DECEDE';

export interface Chercheur {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  specialite?: string | null;
  etablissement?: string | null;
  domaine?: DomaineRef | null;
  nbPublications: number;
  grade?: string | null;
  photoUrl?: string | null;
  photoMediaId?: number | null;
  bureau?: string | null;
  telephone?: string | null;
  orcid?: string | null;
  googleScholarUrl?: string | null;
  researchGateUrl?: string | null;
  linkedinUrl?: string | null;
  biographie?: string | null;
  statutMembre?: StatutMembre | null;
  categorie?: 'ENSEIGNANT_CHERCHEUR' | 'CADRE_ASSIMILE' | null;
}

export interface ChercheurPayload {
  nom: string; prenom: string; email: string;
  specialite?: string; etablissement?: string; domaineId?: number | null;
  grade?: string | null; bureau?: string | null; telephone?: string | null; orcid?: string | null;
  googleScholarUrl?: string | null; researchGateUrl?: string | null; linkedinUrl?: string | null;
  biographie?: string | null; photoMediaId?: number | null; statutMembre?: StatutMembre | null;
}

export interface AuteurRef { id: number; nom: string; prenom: string; }

export interface Publication {
  id: number;
  titre: string;
  resume?: string | null;
  annee?: number | null;
  doi?: string | null;
  pdfUrl?: string | null;
  motsCles: string[];
  chercheurs: AuteurRef[];
  domaine?: DomaineRef | null;
  type?: TypePublication | null;
  venue?: string | null;
  indexation?: 'WOS' | 'SCOPUS' | 'AUTRES' | 'AUCUNE' | null;
  estImpactee?: boolean | null;
  auteursTexte?: string | null;
  classement?: string | null;
  sourceClassement?: SourceClassement | null;
  facteurImpact?: number | null;
  snip?: number | null;
  citations?: number | null;
  volume?: string | null; numero?: string | null; pages?: string | null;
  lieu?: string | null; pays?: string | null; editeur?: string | null; isbn?: string | null;
  institution?: string | null; comiteOrganisation?: string | null;
  pdfMediaId?: number | null;
  pdfFichierUrl?: string | null;
}

export type TypePublication = 'ARTICLE_JOURNAL' | 'CONFERENCE_INTERNATIONALE' | 'CONFERENCE_NATIONALE' | 'CHAPITRE' | 'RAPPORT_TECHNIQUE' | 'AUTRE';
export type SourceClassement = 'SCIMAGO' | 'CORE' | 'JCR' | 'SNIP' | 'AUTRE';

export const TYPES_PUBLICATION: Record<TypePublication, string> = {
  ARTICLE_JOURNAL: 'Article de journal',
  CONFERENCE_INTERNATIONALE: 'Conférence internationale',
  CONFERENCE_NATIONALE: 'Conférence nationale',
  CHAPITRE: "Chapitre d'ouvrage",
  RAPPORT_TECHNIQUE: 'Rapport technique',
  AUTRE: 'Autre',
};

export interface PublicationPayload {
  titre: string; resume?: string; annee?: number | null; doi?: string; pdfUrl?: string;
  motsCles: string[]; chercheurIds: number[]; domaineId?: number | null;
  type?: TypePublication | null; venue?: string | null; auteursTexte?: string | null;
  indexation?: string | null;
  classement?: string | null; sourceClassement?: SourceClassement | null; facteurImpact?: number | null;
  snip?: number | null; citations?: number | null; volume?: string | null; numero?: string | null; pages?: string | null;
  lieu?: string | null; pays?: string | null; editeur?: string | null; isbn?: string | null;
  institution?: string | null; comiteOrganisation?: string | null; pdfMediaId?: number | null;
}

export interface CountItem { nom: string; count: number; }

export interface Stats {
  totalChercheurs: number;
  totalPublications: number;
  totalDomaines: number;
  totalUtilisateurs: number;
  publicationsParDomaine: CountItem[];
  publicationsParAnnee: CountItem[];
  motsClesFrequents: CountItem[];
}
