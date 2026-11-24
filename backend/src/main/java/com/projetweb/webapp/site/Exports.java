package com.projetweb.webapp.site;

import com.projetweb.webapp.model.Chercheur;
import com.projetweb.webapp.model.Publication;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** Export des publications en BibTeX et en CSV (§3.7.3). */
public final class Exports {
    private Exports() {}

    public static String bibtex(List<Publication> l) {
        StringBuilder sb = new StringBuilder("% Publications du laboratoire LIMTIC\n\n");
        Set<String> cles = new HashSet<>();
        for (Publication p : l) {
            String type = switch (p.getType() == null ? Publication.Type.AUTRE : p.getType()) {
                case ARTICLE_JOURNAL -> "article";
                case CONFERENCE_INTERNATIONALE, CONFERENCE_NATIONALE -> "inproceedings";
                case CHAPITRE -> "incollection";
                case RAPPORT_TECHNIQUE -> "techreport";
                default -> "misc";
            };
            String cle = cle(p, cles);
            sb.append('@').append(type).append('{').append(cle).append(",\n");
            champ(sb, "title", p.getTitre());
            champ(sb, "author", auteurs(p));
            switch (type) {
                case "article" -> champ(sb, "journal", p.getVenue());
                case "inproceedings", "incollection" -> champ(sb, "booktitle", p.getVenue());
                case "techreport" -> champ(sb, "institution", p.getInstitution() != null ? p.getInstitution() : p.getVenue());
                default -> champ(sb, "howpublished", p.getVenue());
            }
            champ(sb, "year", p.getAnnee() == null ? null : p.getAnnee().toString());
            champ(sb, "volume", p.getVolume());
            champ(sb, "number", p.getNumero());
            champ(sb, "pages", p.getPages() == null ? null : p.getPages().replaceAll("\\s*[-–]\\s*", "--"));
            champ(sb, "publisher", p.getEditeur());
            champ(sb, "address", p.getLieu());
            champ(sb, "isbn", p.getIsbn());
            champ(sb, "doi", p.getDoi());
            champ(sb, "url", p.getPdfUrl());
            champ(sb, "keywords", p.getMotsCles().isEmpty() ? null : String.join(", ", p.getMotsCles()));
            champ(sb, "note", p.getClassement() == null ? null
                    : "Classement : " + p.getClassement() + (p.getSourceClassement() == null ? "" : " (" + p.getSourceClassement() + ")"));
            sb.setLength(sb.length() - 2);
            sb.append("\n}\n\n");
        }
        return sb.toString();
    }

    private static String auteurs(Publication p) {
        if (p.getAuteursTexte() != null && !p.getAuteursTexte().isBlank())
            return Arrays.stream(p.getAuteursTexte().split("\\s*[,;]\\s*|\\s+and\\s+|\\s+et\\s+")).filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" and "));
        return p.getChercheurs().stream().map(c -> c.getNom() + ", " + c.getPrenom()).collect(Collectors.joining(" and "));
    }

    private static String cle(Publication p, Set<String> pris) {
        String nom = p.getChercheurs().stream().findFirst().map(Chercheur::getNom)
                .orElse(p.getAuteursTexte() == null ? "limtic" : p.getAuteursTexte().split("[ ,;]")[0]);
        String mot = p.getTitre().split("\\s+")[0];
        String base = ascii(nom).toLowerCase() + (p.getAnnee() == null ? "" : p.getAnnee()) + ascii(mot).toLowerCase();
        if (base.isBlank()) base = "pub" + p.getId();
        String c = base; int i = 2;
        while (!pris.add(c)) c = base + (char) ('a' + i++ - 2);
        return c;
    }

    private static String ascii(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "");
    }

    private static void champ(StringBuilder sb, String nom, String valeur) {
        if (valeur == null || valeur.isBlank()) return;
        String v = valeur.replace("\\", "\\textbackslash{}").replace("{", "\\{").replace("}", "\\}")
                .replace("&", "\\&").replace("%", "\\%").replace("$", "\\$").replace("#", "\\#").replace("_", "\\_");
        sb.append("  ").append(nom).append(" = {").append(v).append("},\n");
    }

    public static String csv(List<Publication> l) {
        StringBuilder sb = new StringBuilder("Titre;Auteurs;Type;Venue;Année;Classement;Source du classement;Facteur d'impact;Citations;DOI;Axe;Mots-clés\n");
        for (Publication p : l) {
            sb.append(String.join(";", List.of(c(p.getTitre()), c(auteurs(p).replace(" and ", ", ")), c(p.getType() == null ? "" : p.getType().name()),
                    c(p.getVenue()), c(p.getAnnee()), c(p.getClassement()), c(p.getSourceClassement()), c(p.getFacteurImpact()),
                    c(p.getCitations()), c(p.getDoi()), c(p.getDomaine() == null ? null : p.getDomaine().getNom()),
                    c(String.join(", ", p.getMotsCles()))))).append('\n');
        }
        return sb.toString();
    }

    /** Cellule CSV échappée ; neutralise les formules (injection CSV). */
    static String c(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(";") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
