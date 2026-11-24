export type Role = 'DIRECTEUR' | 'ADMIN' | 'MODERATEUR' | 'CHERCHEUR' | 'UTILISATEUR';

export interface User {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
  /** Fiche membre reliée au compte (rôle Chercheur, « Mon activité »). */
  chercheurId?: number | null;
  /** Compte actif (un compte désactivé ne peut plus se connecter). */
  actif?: boolean;
  token: string | null;
}

export interface LoginRequest    { email: string; password: string; }
export interface RegisterRequest { nom: string; prenom: string; email: string; password: string; }

export const ROLE_LABELS: Record<Role, string> = {
  DIRECTEUR: 'Directeur (super-admin)',
  ADMIN: 'Administrateur',
  CHERCHEUR: 'Chercheur',
  MODERATEUR: 'Modérateur',
  UTILISATEUR: 'Utilisateur',
};
