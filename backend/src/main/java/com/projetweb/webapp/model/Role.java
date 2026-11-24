package com.projetweb.webapp.model;

/**
 * Rôles de la plateforme.
 * DIRECTEUR = super-administrateur (directeur du laboratoire) : il a aussi tous les droits ADMIN.
 * CHERCHEUR = membre du laboratoire relié à sa fiche (saisie de ses propres activités pour le rapport).
 */
public enum Role { DIRECTEUR, ADMIN, MODERATEUR, CHERCHEUR, UTILISATEUR }
