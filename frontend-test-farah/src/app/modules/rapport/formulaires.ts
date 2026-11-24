import { Fiche } from '../../core/models/rapport.model';

/** Description des formulaires F3 à F17 (cahier des charges §14.2) pour le formulaire générique. */
export type TypeChamp = 'text' | 'textarea' | 'annee' | 'date' | 'select' | 'membres' | 'membre' | 'bool' | 'cin' | 'etablissement' | 'universite' | 'email' | 'grade';

export interface Champ {
  cle: string;
  label: string;
  type: TypeChamp;
  requis?: boolean;
  options?: { v: string; l: string }[];
  max?: number;
  aide?: string;
  /** Champ modifiable par l'administrateur uniquement. */
  adminSeul?: boolean;
  large?: boolean;
  /** Affiché seulement si la condition est vraie. */
  si?: (m: Record<string, any>) => boolean;
}

export interface Colonne { label: string; valeur: (f: Fiche, noms: (ids: number[]) => string) => string; }

export interface Categorie {
  code: string;
  f: string;
  titre: string;
  icone: string;
  champs: Champ[];
  colonnes: Colonne[];
  saisieChercheur: boolean;
  /** Clé de l'année de rattachement (filtre « année »). */
  anneeCle?: string;
  personne?: boolean;
  defauts?: Record<string, any>;
}

const opt = (o: Record<string, string>) => Object.entries(o).map(([v, l]) => ({ v, l }));

const PERSONNE: Champ[] = [
  { cle: 'nom', label: 'Nom', type: 'text', requis: true },
  { cle: 'prenom', label: 'Prénom', type: 'text', requis: true },
  { cle: 'etranger', label: 'Pièce d\'identité étrangère (passeport, pas de CIN)', type: 'bool' },
  { cle: 'cin', label: 'N° CIN', type: 'cin', requis: true, si: m => !m['etranger'], aide: '8 chiffres. Chiffré en base, jamais affiché sur le site public.' },
  { cle: 'etablissement', label: 'Établissement', type: 'etablissement', requis: true, large: true },
  { cle: 'universite', label: 'Université', type: 'universite', requis: true, large: true, aide: 'Déduite de l\'établissement quand il est connu.' },
];

const STATUTS_MEMBRE = opt({ ACTIF: 'Actif', RETRAITE: 'Retraité', MUTE: 'Muté', DEMISSIONNAIRE: 'Démissionnaire', DECEDE: 'Décédé' });
const nomComplet = (f: Fiche) => `${f['nom']} ${f['prenom']}`;
const titre = (cle: string) => (f: Fiche) => f[cle] ?? '';
const auteursOuvrage: Champ[] = [
  { cle: 'auteursTexte', label: 'Auteurs (liste ordonnée, tels qu\'ils figurent dans la publication)', type: 'textarea', requis: true, large: true },
  { cle: 'auteurIds', label: 'Auteurs membres du laboratoire', type: 'membres', requis: true, large: true, aide: 'Au moins un auteur interne.' },
];

export const CATEGORIES: Categorie[] = [
  {
    code: 'membres', f: 'F3 / F5', titre: 'Membres', icone: 'badge', saisieChercheur: false, personne: true,
    defauts: { categorie: 'ENSEIGNANT_CHERCHEUR', statutMembre: 'ACTIF' },
    champs: [
      { cle: 'categorie', label: 'Catégorie', type: 'select', requis: true, options: opt({ ENSEIGNANT_CHERCHEUR: 'Enseignant-chercheur permanent (F3)', CADRE_ASSIMILE: 'Cadre équivalent assistant (F5)' }) },
      { cle: 'grade', label: 'Grade', type: 'grade', requis: true, aide: 'Le corps A ou B est déduit du grade (table de correspondance).' },
      ...PERSONNE,
      { cle: 'email', label: 'E-mail', type: 'email' },
      { cle: 'dateEntree', label: 'Date d\'entrée au laboratoire', type: 'date', requis: true },
      { cle: 'dateSortie', label: 'Date de sortie', type: 'date' },
      { cle: 'statutMembre', label: 'Statut', type: 'select', requis: true, options: STATUTS_MEMBRE },
      { cle: 'specialite', label: 'Spécialité', type: 'text', large: true },
    ],
    colonnes: [
      { label: 'Nom et prénom', valeur: nomComplet },
      { label: 'Grade', valeur: f => f['grade'] ?? '' },
      { label: 'Catégorie', valeur: f => f['categorie'] === 'CADRE_ASSIMILE' ? 'Cadre' : 'Enseignant-chercheur' },
      { label: 'Établissement', valeur: f => f['etablissement'] ?? '' },
    ],
  },
  {
    code: 'doctorants', f: 'F4', titre: 'Doctorants', icone: 'school', saisieChercheur: true, personne: true,
    defauts: { statutThese: 'EN_COURS' },
    champs: [
      ...PERSONNE,
      { cle: 'sujet', label: 'Sujet de thèse', type: 'textarea', requis: true, large: true },
      { cle: 'directeurIds', label: 'Directeur(s) de thèse', type: 'membres', requis: true, large: true },
      { cle: 'directeursTexte', label: 'Directeurs extérieurs au laboratoire', type: 'text', large: true },
      { cle: 'anneePremiereInscription', label: 'Année de première inscription', type: 'annee', requis: true },
      { cle: 'statutThese', label: 'Statut', type: 'select', requis: true, options: opt({ EN_COURS: 'En cours', SOUTENU: 'Soutenu', ABANDON: 'Abandon' }),
        aide: 'Le passage à « soutenu » crée une fiche thèse (F13) pré-remplie.' },
      { cle: 'dateSoutenance', label: 'Date de soutenance', type: 'date', requis: true, si: m => m['statutThese'] === 'SOUTENU' },
      { cle: 'mention', label: 'Mention', type: 'text', si: m => m['statutThese'] === 'SOUTENU' },
    ],
    colonnes: [
      { label: 'Nom et prénom', valeur: nomComplet },
      { label: 'Directeur(s)', valeur: (f, n) => n(f['directeurIds'] ?? []) || f['directeursTexte'] || '' },
      { label: '1re inscription', valeur: f => f['anneePremiereInscription'] ?? '' },
      { label: 'Statut thèse', valeur: f => ({ EN_COURS: 'En cours', SOUTENU: 'Soutenu', ABANDON: 'Abandon' } as any)[f['statutThese']] ?? '' },
    ],
  },
  {
    code: 'masteriens', f: 'F6', titre: 'Étudiants en mastère', icone: 'local_library', saisieChercheur: true, personne: true,
    defauts: { statutMastere: 'EN_COURS' },
    champs: [
      ...PERSONNE,
      { cle: 'sujet', label: 'Sujet de mémoire', type: 'textarea', requis: true, large: true },
      { cle: 'encadrantIds', label: 'Encadrant(s)', type: 'membres', requis: true, large: true },
      { cle: 'anneePremiereInscription', label: 'Année de première inscription', type: 'annee', requis: true },
      { cle: 'promotion', label: 'Promotion', type: 'text' },
      { cle: 'statutMastere', label: 'Statut', type: 'select', requis: true, options: opt({ EN_COURS: 'En cours', DIPLOME: 'Diplômé' }),
        aide: 'Le passage à « diplômé » crée une fiche mastère soutenu (F14).' },
      { cle: 'dateSoutenance', label: 'Date de soutenance', type: 'date', requis: true, si: m => m['statutMastere'] === 'DIPLOME' },
    ],
    colonnes: [
      { label: 'Nom et prénom', valeur: nomComplet },
      { label: 'Encadrant(s)', valeur: (f, n) => n(f['encadrantIds'] ?? []) },
      { label: 'Établissement', valeur: f => f['etablissement'] ?? '' },
    ],
  },
  {
    code: 'publications', f: 'F7', titre: 'Publications', icone: 'article', saisieChercheur: true, anneeCle: 'annee',
    defauts: { type: 'ARTICLE_JOURNAL' },
    champs: [
      { cle: 'type', label: 'Type de publication', type: 'select', requis: true,
        options: opt({ ARTICLE_JOURNAL: 'Article de journal', CONFERENCE_INTERNATIONALE: 'Conférence internationale', CONFERENCE_NATIONALE: 'Conférence nationale', AUTRE: 'Autre' }) },
      { cle: 'titre', label: 'Titre', type: 'textarea', requis: true, large: true, max: 500 },
      { cle: 'venue', label: 'Titre du journal ou de la conférence', type: 'text', requis: true, large: true, aide: 'Restitué sans troncature dans le rapport.' },
      { cle: 'annee', label: 'Année', type: 'annee', requis: true },
      { cle: 'indexation', label: 'Indexation', type: 'select', requis: true, options: opt({ WOS: 'WOS / JCR', SCOPUS: 'Scopus', AUTRES: 'Autres', AUCUNE: 'Aucune' }) },
      ...auteursOuvrage,
      { cle: 'doi', label: 'DOI', type: 'text', aide: 'Sert à détecter les doublons.' },
      { cle: 'estImpactee', label: 'Comptée comme publication impactée (II-1)', type: 'bool', adminSeul: true,
        aide: 'Proposée selon la table de correspondance des indexations ; modifiable par l\'administrateur.' },
    ],
    colonnes: [
      { label: 'Titre', valeur: titre('titre') },
      { label: 'Journal / conférence', valeur: f => f['venue'] ?? '' },
      { label: 'Année', valeur: f => f['annee'] ?? '' },
      { label: 'Indexation', valeur: f => (f['indexation'] ?? '') + (f['estImpactee'] ? ' · impactée' : '') },
    ],
  },
  {
    code: 'ouvrages', f: 'F8', titre: 'Ouvrages', icone: 'menu_book', saisieChercheur: true, anneeCle: 'annee',
    champs: [
      { cle: 'titre', label: 'Titre', type: 'text', requis: true, large: true },
      { cle: 'annee', label: 'Année', type: 'annee', requis: true },
      { cle: 'editeur', label: 'Éditeur', type: 'text', requis: true },
      { cle: 'edition', label: 'Édition (collection, numéro)', type: 'text', large: true },
      ...auteursOuvrage,
      { cle: 'isbn', label: 'ISBN', type: 'text' },
    ],
    colonnes: [{ label: 'Titre', valeur: titre('titre') }, { label: 'Année', valeur: f => f['annee'] ?? '' }, { label: 'Éditeur', valeur: f => f['editeur'] ?? '' }],
  },
  {
    code: 'chapitres', f: 'F9', titre: 'Chapitres d\'ouvrage', icone: 'auto_stories', saisieChercheur: true, anneeCle: 'annee',
    champs: [
      { cle: 'titre', label: 'Titre du chapitre', type: 'text', requis: true, large: true },
      { cle: 'annee', label: 'Année', type: 'annee', requis: true },
      { cle: 'editeur', label: 'Éditeur', type: 'text', requis: true },
      { cle: 'edition', label: 'Ouvrage / collection (colonne « Édition »)', type: 'text', requis: true, large: true },
      ...auteursOuvrage,
      { cle: 'isbn', label: 'ISBN', type: 'text' },
      { cle: 'pages', label: 'Pages', type: 'text' },
    ],
    colonnes: [{ label: 'Titre', valeur: titre('titre') }, { label: 'Année', valeur: f => f['annee'] ?? '' }, { label: 'Ouvrage', valeur: f => f['edition'] ?? '' }],
  },
  {
    code: 'brevets', f: 'F10', titre: 'Brevets', icone: 'workspace_premium', saisieChercheur: true, anneeCle: 'annee',
    champs: [
      { cle: 'typeBrevet', label: 'Type', type: 'select', requis: true, options: opt({ INNORPI: 'INNORPI', PCT: 'PCT', AUTRE: 'Autre' }) },
      { cle: 'annee', label: 'Année de dépôt', type: 'annee', requis: true },
      { cle: 'reference', label: 'Référence (n° de dépôt)', type: 'text', requis: true },
      { cle: 'titre', label: 'Intitulé', type: 'text', requis: true, large: true },
      { cle: 'auteursTexte', label: 'Inventeurs (liste ordonnée)', type: 'textarea', requis: true, large: true },
      { cle: 'auteurIds', label: 'Inventeurs membres du laboratoire', type: 'membres', large: true },
      { cle: 'etat', label: 'Statut', type: 'select', options: opt({ DEPOSE: 'Déposé', DELIVRE: 'Délivré' }) },
    ],
    colonnes: [{ label: 'Intitulé', valeur: titre('titre') }, { label: 'Type', valeur: f => f['typeBrevet'] ?? '' }, { label: 'Référence', valeur: f => f['reference'] ?? '' }],
  },
  {
    code: 'obtentions', f: 'F11', titre: 'Obtentions végétales', icone: 'eco', saisieChercheur: true, anneeCle: 'annee',
    champs: [
      { cle: 'titre', label: 'Titre', type: 'text', requis: true, large: true },
      { cle: 'annee', label: 'Année', type: 'annee', requis: true },
      { cle: 'reference', label: 'Référence', type: 'text', requis: true },
      { cle: 'auteursTexte', label: 'Obtenteurs', type: 'textarea', large: true },
      { cle: 'auteurIds', label: 'Obtenteurs membres du laboratoire', type: 'membres', large: true },
    ],
    colonnes: [{ label: 'Titre', valeur: titre('titre') }, { label: 'Année', valeur: f => f['annee'] ?? '' }, { label: 'Référence', valeur: f => f['reference'] ?? '' }],
  },
  {
    code: 'habilitations', f: 'F12', titre: 'Habilitations', icone: 'military_tech', saisieChercheur: true, anneeCle: 'annee',
    defauts: { nature: 'HABILITATION' },
    champs: [
      { cle: 'nature', label: 'Nature', type: 'select', requis: true, options: opt({ HABILITATION: 'Habilitation universitaire', AGREGATION_HU: 'Agrégation hospitalo-universitaire' }) },
      { cle: 'titre', label: 'Titre', type: 'text', requis: true, large: true },
      { cle: 'annee', label: 'Année de soutenance', type: 'annee', requis: true },
      { cle: 'titulaireId', label: 'Titulaire (membre)', type: 'membre' },
      { cle: 'titulaireNom', label: 'Titulaire (si extérieur)', type: 'text' },
      { cle: 'encadreurId', label: 'Encadreur (membre)', type: 'membre' },
      { cle: 'encadreurNom', label: 'Encadreur (si extérieur)', type: 'text' },
    ],
    colonnes: [{ label: 'Titre', valeur: titre('titre') }, { label: 'Année', valeur: f => f['annee'] ?? '' },
      { label: 'Titulaire', valeur: (f, n) => f['titulaireId'] ? n([f['titulaireId']]) : f['titulaireNom'] ?? '' }],
  },
  {
    code: 'theses', f: 'F13', titre: 'Thèses soutenues', icone: 'workspace_premium', saisieChercheur: true, anneeCle: 'anneeSoutenance',
    champs: [
      { cle: 'titre', label: 'Titre de la thèse', type: 'textarea', requis: true, large: true },
      { cle: 'anneeSoutenance', label: 'Année de soutenance', type: 'annee', requis: true },
      { cle: 'anneePremiereInscription', label: 'Année de première inscription', type: 'annee', requis: true },
      { cle: 'sujet', label: 'Sujet (résumé)', type: 'textarea', requis: true, large: true, aide: 'Affiché sans troncature dans le rapport.' },
      { cle: 'encadrantIds', label: 'Encadrant(s) membres', type: 'membres', requis: true, large: true },
      { cle: 'encadrantsTexte', label: 'Encadrants extérieurs', type: 'text', large: true },
      { cle: 'doctorantNom', label: 'Doctorant', type: 'text', aide: 'Rempli automatiquement quand la thèse vient d\'une fiche doctorant (F4).' },
      { cle: 'dateSoutenance', label: 'Date de soutenance', type: 'date' },
      { cle: 'mention', label: 'Mention', type: 'text' },
    ],
    colonnes: [{ label: 'Titre', valeur: titre('titre') }, { label: 'Soutenance', valeur: f => f['anneeSoutenance'] ?? '' },
      { label: 'Encadrant(s)', valeur: (f, n) => n(f['encadrantIds'] ?? []) || f['encadrantsTexte'] || '' }],
  },
  {
    code: 'masters', f: 'F14', titre: 'Mastères soutenus', icone: 'history_edu', saisieChercheur: true, anneeCle: 'anneeSoutenance',
    champs: [
      { cle: 'anneeSoutenance', label: 'Année de soutenance', type: 'annee', requis: true },
      { cle: 'anneePremiereInscription', label: 'Année de première inscription', type: 'annee', requis: true },
      { cle: 'sujet', label: 'Sujet', type: 'textarea', requis: true, large: true },
      { cle: 'encadrantIds', label: 'Encadrant(s) membres', type: 'membres', requis: true, large: true },
      { cle: 'encadrantsTexte', label: 'Encadrants extérieurs', type: 'text', large: true },
      { cle: 'etudiantNom', label: 'Étudiant', type: 'text' },
      { cle: 'mention', label: 'Mention', type: 'text' },
    ],
    colonnes: [{ label: 'Sujet', valeur: titre('sujet') }, { label: 'Soutenance', valeur: f => f['anneeSoutenance'] ?? '' },
      { label: 'Encadrant(s)', valeur: (f, n) => n(f['encadrantIds'] ?? []) || f['encadrantsTexte'] || '' }],
  },
  {
    code: 'evenements', f: 'F15', titre: 'Séminaires et journées', icone: 'event', saisieChercheur: false, anneeCle: 'annee',
    defauts: { type: 'SEMINAIRE', organisateurs: 'LIMTIC', organiseParLabo: true },
    champs: [
      { cle: 'titre', label: 'Titre', type: 'text', requis: true, large: true },
      { cle: 'type', label: 'Type', type: 'select', requis: true, options: opt({ SEMINAIRE: 'Séminaire', JOURNEE_RECHERCHE: 'Journée de recherche', ATELIER: 'Atelier', CONFERENCE: 'Conférence' }) },
      { cle: 'dateDebut', label: 'Date de début', type: 'date' },
      { cle: 'dateFin', label: 'Date de fin', type: 'date' },
      { cle: 'annee', label: 'Année (si pas de date)', type: 'annee' },
      { cle: 'lieu', label: 'Lieu', type: 'text', requis: true },
      { cle: 'organisateurs', label: 'Organisateurs', type: 'text', requis: true },
      { cle: 'organiseParLabo', label: 'Organisé par le laboratoire (alimente III-1)', type: 'bool' },
      { cle: 'description', label: 'Description', type: 'textarea', large: true },
    ],
    colonnes: [{ label: 'Titre', valeur: titre('titre') }, { label: 'Année', valeur: f => f['annee'] ?? '' }, { label: 'Lieu', valeur: f => f['lieu'] ?? '' }],
  },
  {
    code: 'conventions', f: 'F16', titre: 'Conventions', icone: 'handshake', saisieChercheur: false, anneeCle: 'annee',
    defauts: { type: 'SOCIO_ECONOMIQUE' },
    champs: [
      { cle: 'annee', label: 'Année de signature', type: 'annee', requis: true },
      { cle: 'reference', label: 'Référence (partenaire ou intitulé)', type: 'text', requis: true, large: true },
      { cle: 'type', label: 'Type', type: 'select', requis: true, aide: 'Détermine la rubrique du rapport (III-2 ou III-3).',
        options: opt({ SOCIO_ECONOMIQUE: 'Milieu socio-économique', COOPERATION_INTERNATIONALE: 'Coopération scientifique internationale', AUTRE: 'Autre' }) },
      { cle: 'partenaire', label: 'Partenaire', type: 'text' },
      { cle: 'pays', label: 'Pays', type: 'text' },
      { cle: 'secteur', label: 'Secteur', type: 'text' },
      { cle: 'objet', label: 'Objet', type: 'textarea', large: true },
      { cle: 'dateDebut', label: 'Date de début', type: 'date' },
      { cle: 'dateFin', label: 'Date de fin', type: 'date' },
      { cle: 'responsableId', label: 'Responsable scientifique', type: 'membre' },
    ],
    colonnes: [{ label: 'Référence', valeur: titre('reference') }, { label: 'Année', valeur: f => f['annee'] ?? '' },
      { label: 'Type', valeur: f => ({ SOCIO_ECONOMIQUE: 'Socio-économique', COOPERATION_INTERNATIONALE: 'Coopération internationale', AUTRE: 'Autre' } as any)[f['type']] ?? '' }],
  },
  {
    code: 'projets', f: 'F17', titre: 'Projets', icone: 'rocket_launch', saisieChercheur: true,
    defauts: { type: 'NATIONAL' },
    champs: [
      { cle: 'intitule', label: 'Intitulé', type: 'text', requis: true, large: true },
      { cle: 'coordinateurId', label: 'Coordinateur (membre)', type: 'membre' },
      { cle: 'coordinateurExterne', label: 'Coordinateur extérieur au laboratoire', type: 'text', aide: 'Un des deux champs coordinateur est obligatoire.' },
      { cle: 'type', label: 'Type', type: 'select', requis: true, options: opt({ NATIONAL: 'National', INTERNATIONAL: 'International', CONTRAT: 'Contrat' }) },
      { cle: 'financeur', label: 'Programme / financeur', type: 'text', large: true },
      { cle: 'dateDebut', label: 'Date de début', type: 'date' },
      { cle: 'dateFin', label: 'Date de fin', type: 'date' },
      { cle: 'membreIds', label: 'Membres LIMTIC impliqués', type: 'membres', large: true },
      { cle: 'axe', label: 'Axe de recherche', type: 'text', large: true },
    ],
    colonnes: [{ label: 'Intitulé', valeur: titre('intitule') },
      { label: 'Coordinateur', valeur: (f, n) => f['coordinateurId'] ? n([f['coordinateurId']]) : f['coordinateurExterne'] ?? '' },
      { label: 'Type', valeur: f => f['type'] ?? '' }],
  },
];

export function categorie(code: string): Categorie | undefined { return CATEGORIES.find(c => c.code === code); }

/** Lien de correction d'un contrôle vers l'écran concerné. */
export function lienControle(cat: string): string {
  const pages: Record<string, string> = { identite: '/rapport/identite', resume: '/rapport/resume', budget: '/rapport/budget',
    parametres: '/rapport/parametres', completude: '/rapport' };
  return pages[cat] ?? `/rapport/saisie/${cat}`;
}
