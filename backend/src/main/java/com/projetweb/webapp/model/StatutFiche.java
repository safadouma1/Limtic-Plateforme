package com.projetweb.webapp.model;

/** Cycle de vie d'un enregistrement du rapport d'activité (cahier des charges §16.2). */
public enum StatutFiche {
    BROUILLON, SOUMIS, VALIDE, VERROUILLE;

    public boolean pris() { return this == VALIDE || this == VERROUILLE; }
}
