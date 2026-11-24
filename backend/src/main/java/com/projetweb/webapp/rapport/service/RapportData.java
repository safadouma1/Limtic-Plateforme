package com.projetweb.webapp.rapport.service;

import java.time.Instant;
import java.util.List;

/**
 * Contenu complet d'un rapport, calculé à partir des enregistrements validés (aperçu, génération,
 * copie immuable à la clôture). Les numéros des rubriques sont générés depuis l'ordre du gabarit :
 * récapitulatif et détail portent donc toujours les mêmes numéros.
 */
public record RapportData(int annee, String gabaritVersion, List<String> entete, String titre, String dateArrete,
                          Identite identite, Resume resume, List<Groupe> groupes, List<Controle> controles,
                          Instant calculeLe, boolean inclureSoumis, boolean anneePrecedenteDisponible) {

    public record Identite(String universite, String etablissement, String denomination, String code,
                           String chef, String grade, String fonction, String telephone, String fax,
                           String email, String siteWeb) {}

    public record Resume(String historique, List<String> thematiques, String bilan) {}

    public record Groupe(String numero, String titre, List<Section> sections) {}

    /**
     * @param colonneCin index de la colonne « N° CIN » (-1 si aucune) : retirée des versions sans CIN
     * @param valeur     valeur du récapitulatif (nombre de lignes, ou montant pour le budget)
     * @param ligneTotal ligne de total du tableau (budget), sinon null
     */
    public record Section(String code, String numero, String titre, String libelleRecap, List<String> colonnes,
                          List<List<String>> lignes, int colonneCin, String libelleTotal, String valeur,
                          String valeurN1, List<String> ligneTotal) {}

    public record Controle(String niveau, String code, String message, String categorie, List<String> elements) {
        public boolean bloquant() { return "BLOQUANT".equals(niveau); }
    }

    public long nbBloquants() { return controles.stream().filter(Controle::bloquant).count(); }

    public Section section(String code) {
        return groupes.stream().flatMap(g -> g.sections().stream()).filter(s -> s.code().equals(code)).findFirst().orElse(null);
    }
}
