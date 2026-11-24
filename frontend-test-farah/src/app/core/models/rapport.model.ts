export type StatutFiche = 'BROUILLON' | 'SOUMIS' | 'VALIDE' | 'VERROUILLE';
export type StatutCampagne = 'PREPARATION' | 'SAISIE_OUVERTE' | 'REVUE' | 'CLOTUREE';
export type RegleIII3 = 'CONVENTIONS_ET_PROJETS' | 'CONVENTIONS_ET_PROJETS_INTERNATIONAUX' | 'CONVENTIONS_SEULEMENT';

export const STATUT_FICHE: Record<StatutFiche, { label: string; chip: string }> = {
  BROUILLON: { label: 'Brouillon', chip: 'chip-gold' },
  SOUMIS: { label: 'Soumis', chip: 'chip-blue' },
  VALIDE: { label: 'Validé', chip: 'chip-green' },
  VERROUILLE: { label: 'Verrouillé', chip: 'chip-purple' },
};

export const STATUT_CAMPAGNE: Record<StatutCampagne, string> = {
  PREPARATION: 'Préparation',
  SAISIE_OUVERTE: 'Saisie ouverte',
  REVUE: 'Revue',
  CLOTUREE: 'Clôturée',
};

export interface Campagne {
  id: number;
  annee: number;
  statut: StatutCampagne;
  dateDebutSaisie: string | null;
  dateEcheance: string | null;
  dateArreteBudget: string | null;
  inclureDoctorantsSoutenus: boolean;
  bloquerNonValides: boolean;
  inclureNonImpacteesII3: boolean;
  regleIII3: RegleIII3;
  clotureLe: string | null;
  cloturePar: string | null;
  nbClotures: number;
  snapshotDisponible: boolean;
}

/** Enregistrement générique d'un formulaire (champs communs §14.1 + champs propres). */
export interface Fiche {
  id: number;
  statut: StatutFiche;
  motifRejet: string | null;
  creePar: string | null;
  creeParId: number | null;
  creeLe: string | null;
  validePar: string | null;
  anneeReference: number | null;
  cinRenseigne?: boolean;
  [cle: string]: any;
}

export interface Resultat { fiche: Fiche; message: string; }

export interface RubriqueRecap { numero: string; code: string; libelle: string; valeur: string; valeurN1: string | null; }
export interface Recapitulatif {
  annee: number;
  groupes: { numero: string; titre: string; rubriques: RubriqueRecap[] }[];
  anneePrecedenteDisponible: boolean;
  nbBloquants: number;
  nbAvertissements: number;
}

export interface Controle { niveau: 'BLOQUANT' | 'AVERTISSEMENT'; code: string; message: string; categorie: string; elements: string[]; }

export interface SectionRapport {
  code: string; numero: string; titre: string; libelleRecap: string; colonnes: string[];
  lignes: string[][]; colonneCin: number; libelleTotal: string; valeur: string; valeurN1: string | null; ligneTotal: string[] | null;
}
export interface RapportData {
  annee: number; gabaritVersion: string; entete: string[]; titre: string; dateArrete: string;
  identite: Record<string, string | null>;
  resume: { historique: string | null; thematiques: string[]; bilan: string | null };
  groupes: { numero: string; titre: string; sections: SectionRapport[] }[];
  controles: Controle[];
  calculeLe: string; inclureSoumis: boolean; anneePrecedenteDisponible: boolean;
}

export interface FichierRapport { id: number; format: 'DOCX' | 'PDF' | 'ZIP' | 'CSV'; tableau: string | null; nomFichier: string; taille: number; sha256: string; }
export interface VersionRapport {
  id: number; annee: number; version: number; nature: 'PROVISOIRE' | 'FINALE'; avecCin: boolean; inclureSoumis: boolean;
  remplacee: boolean; gabaritVersion: string; generePar: string; genereLe: string; nbBloquants: number; nbAvertissements: number;
  fichiers: FichierRapport[];
}
export interface Lien { url: string; expireLe: string; nomFichier: string; sha256: string; }
export interface Telechargement { id: number; annee: number; version: number; nomFichier: string; format: string; utilisateur: string; telechargeLe: string; }
export interface AuditLog { id: number; date: string; utilisateur: string; action: string; details: string; }

export interface StructureIdentite {
  universite: string | null; etablissement: string | null; denomination: string | null; code: string | null;
  chefId: number | null; chefNom: string | null; chefGrade: string | null; fonction: string | null;
  telephone: string | null; fax: string | null; email: string | null; siteWeb: string | null;
}
export interface ResumeRapport { annee: number; historique: string | null; thematiques: string | null; bilan: string | null; }
export interface LigneBudget { id: number; annee: number; ordre: number; rubrique: string; creditsDisponibles: number | null; creditsEngages: number | null; reliquat: number | null; }
export interface Regle { id: number; tableCode: 'GRADE_CORPS' | 'INDEXATION_IMPACTEE' | 'CONVENTION_RUBRIQUE'; cle: string; valeur: string; }

export interface MembreRef { id: number; nom: string; prenom: string; grade: string; categorie: string; corps: string; }
export interface Referentiels {
  membres: MembreRef[];
  etablissements: { nom: string; universite: string }[];
  grades: string[];
  corpsParGrade: Record<string, string>;
  rubriquesBudget: string[];
}

export interface LigneImport { ligne: number; statut: 'IMPORTEE' | 'REJETEE' | 'DOUBLON'; message: string; }
export interface CompteRendu { categorie: string; importees: number; rejetees: number; doublons: number; lignes: LigneImport[]; }

export interface Completude {
  formulaires: { code: string; formulaire: string; libelle: string; statuts: Record<StatutFiche, number> }[];
  declarants: Record<string, Partial<Record<StatutFiche, number>>>;
}
