package com.projetweb.webapp.rapport.generation;

import com.projetweb.webapp.rapport.service.RapportData;
import com.projetweb.webapp.rapport.service.RapportData.Section;

import java.util.ArrayList;
import java.util.List;

/** Options communes aux rendus Word / PDF / CSV. */
public record Rendu(boolean provisoire, boolean avecCin, String mention, boolean fiche, String titreFiche) {

    public static final String FILIGRANE = "PROVISOIRE – NON VALIDÉ";

    /** Colonnes visibles d'une section (la colonne CIN est retirée des versions sans CIN). */
    public List<String> colonnes(Section s) {
        List<String> c = new ArrayList<>(s.colonnes());
        if (!avecCin && s.colonneCin() >= 0 && s.colonneCin() < c.size()) c.remove(s.colonneCin());
        return c;
    }

    public List<String> ligne(Section s, List<String> l) {
        List<String> c = new ArrayList<>(l);
        if (!avecCin && s.colonneCin() >= 0 && s.colonneCin() < c.size()) c.remove(s.colonneCin());
        return c;
    }

    public static String recapValeur(RapportData d, Section s) {
        return s.valeur() == null || s.valeur().isEmpty() ? "—" : s.valeur();
    }
}
