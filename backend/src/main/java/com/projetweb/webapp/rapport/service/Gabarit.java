package com.projetweb.webapp.rapport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projetweb.webapp.rapport.model.GabaritRapport;
import com.projetweb.webapp.rapport.repo.GabaritRapportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Gabarit du rapport (§17.1) : ordre des sections, libellés, colonnes et en-tête, modifiables une fois
 * pour toutes les sorties. Les codes de section sont fixes (ils désignent les données) ; tout le reste
 * (ordre, titres, colonnes, présence) vient du gabarit. Les fautes du modèle 2025 sont corrigées.
 */
@Service
public class Gabarit {

    public record Def(String version, List<String> entete, String titre, List<GroupeDef> groupes) {}
    public record GroupeDef(String titre, List<SectionDef> sections) {}
    public record SectionDef(String code, String titre, String recap, String total, List<String> colonnes) {}

    public static final String VERSION_2025 = "Modèle 2025";

    private static final List<String> PERS = List.of("Grade", "Nom et prénom", "N° CIN", "Établissement", "Université");
    private static final List<String> PERS_SANS_GRADE = List.of("Nom et prénom", "N° CIN", "Établissement", "Université");
    private static final List<String> OUVR = List.of("Titre", "Année", "Éditeur", "Édition", "Auteurs");

    public static Def defaut() {
        return new Def(VERSION_2025,
                List.of("RÉPUBLIQUE TUNISIENNE", "Ministère de l'Enseignement Supérieur et de la Recherche Scientifique",
                        "Direction Générale de la Recherche Scientifique", "Direction des Structures de Recherche"),
                "Rapport d'activité {annee}",
                List.of(
                        new GroupeDef("Ressources humaines et espace de recherche", List.of(
                                new SectionDef("CORPS_A", "Enseignants-chercheurs permanents — corps A", "Enseignants-chercheurs du corps A",
                                        "Nombre total des enseignants-chercheurs corps A", PERS),
                                new SectionDef("CORPS_B", "Enseignants-chercheurs permanents — corps B", "Enseignants-chercheurs du corps B",
                                        "Nombre total des enseignants-chercheurs corps B", PERS),
                                new SectionDef("DOCTORANTS", "Doctorants", "Doctorants", "Nombre total des doctorants", PERS_SANS_GRADE),
                                new SectionDef("CADRES", "Cadres ayant un grade équivalent au grade d'assistant de l'enseignement supérieur",
                                        "Cadres ayant un grade équivalent au grade d'assistant", "Nombre total des cadres", PERS),
                                new SectionDef("ETUDIANTS", "Étudiants en mastère de recherche", "Étudiants (mastère de recherche)",
                                        "Nombre total des étudiants en mastère de recherche", PERS_SANS_GRADE))),
                        new GroupeDef("Production scientifique", List.of(
                                new SectionDef("PUBLICATIONS", "Publications impactées parues en {annee} (JCR)", "Publications impactées parues en {annee} (JCR)",
                                        "Nombre total des publications impactées", List.of("Titre", "Titre du journal ou de la conférence", "Année", "Indexation", "Auteurs")),
                                new SectionDef("OUVRAGES", "Ouvrages scientifiques édités en {annee}", "Ouvrages scientifiques édités en {annee}",
                                        "Nombre total des ouvrages", OUVR),
                                new SectionDef("CHAPITRES", "Articles (indexés ou nationaux) et chapitres d'ouvrage édités en {annee}",
                                        "Articles (indexés ou nationaux) et chapitres d'ouvrage édités en {annee}", "Nombre total des articles et chapitres", OUVR),
                                new SectionDef("BREVETS", "Brevets d'invention (INNORPI, PCT et autres) déposés en {annee}",
                                        "Brevets d'invention (INNORPI, PCT et autres) déposés en {annee}", "Nombre total des brevets", List.of("Type", "Année", "Référence")),
                                new SectionDef("OBTENTIONS", "Obtentions végétales enregistrées en {annee}", "Obtentions végétales enregistrées en {annee}",
                                        "Nombre total des obtentions végétales", List.of("Titre", "Année", "Référence")),
                                new SectionDef("HABILITATIONS", "Habilitations universitaires et agrégations HU soutenues en {annee}",
                                        "Habilitations universitaires et agrégations HU soutenues en {annee}", "Nombre total des habilitations et agrégations",
                                        List.of("Titre", "Nom et prénom du titulaire", "Année", "Encadreur")),
                                new SectionDef("THESES", "Thèses de doctorat soutenues en {annee}", "Thèses de doctorat soutenues en {annee}",
                                        "Nombre total des thèses soutenues", List.of("Titre", "Année", "Année de la première inscription", "Sujet", "Encadrant(s)")),
                                new SectionDef("MASTERS", "Mastères de recherche soutenus en {annee}", "Mastères de recherche soutenus en {annee}",
                                        "Nombre total des mastères soutenus", List.of("Année", "Année de la première inscription", "Sujet", "Encadrant(s)")))),
                        new GroupeDef("Ouverture sur l'environnement (scientifique et socio-économique)", List.of(
                                new SectionDef("SEMINAIRES", "Séminaires, journées et ateliers scientifiques organisés en {annee}",
                                        "Séminaires, journées et ateliers scientifiques organisés en {annee}", "Nombre total des manifestations organisées",
                                        List.of("Titre", "Année", "Organisateurs", "Lieu")),
                                new SectionDef("CONVENTIONS", "Conventions signées avec des entreprises et partenaires socio-économiques en {annee}",
                                        "Conventions signées avec des entreprises et partenaires socio-économiques en {annee}", "Nombre total des conventions",
                                        List.of("Année", "Référence", "Type")),
                                new SectionDef("COOPERATION", "Conventions et projets de coopération scientifique internationale en {annee}",
                                        "Conventions et projets de coopération scientifique internationale en {annee}", "Nombre total des conventions et projets",
                                        List.of("Intitulé", "Coordinateur / responsable", "Nature")))),
                        new GroupeDef("Situation du reliquat de budget", List.of(
                                new SectionDef("BUDGET", "Reliquat du budget arrêté au {arrete} (hors crédit alloué au titre de {annee})",
                                        "Reliquat du budget, hors crédit alloué au titre de {annee} (montant en dinars)", "Total",
                                        List.of("Rubrique", "Crédits disponibles (1)", "Crédits engagés (2)", "Reliquat des crédits (3 = 1 − 2)"))))));
    }

    public static final Set<String> CODES = Set.of("CORPS_A", "CORPS_B", "DOCTORANTS", "CADRES", "ETUDIANTS", "PUBLICATIONS",
            "OUVRAGES", "CHAPITRES", "BREVETS", "OBTENTIONS", "HABILITATIONS", "THESES", "MASTERS", "SEMINAIRES",
            "CONVENTIONS", "COOPERATION", "BUDGET");

    private final GabaritRapportRepository repo;
    private final ObjectMapper mapper;

    public Gabarit(GabaritRapportRepository repo, ObjectMapper mapper) { this.repo = repo; this.mapper = mapper; }

    @Transactional
    public void initialiser() {
        if (repo.count() > 0) return;
        GabaritRapport g = new GabaritRapport();
        g.setVersion(VERSION_2025);
        g.setActif(true);
        g.setStructure(json(defaut()));
        repo.save(g);
    }

    public Def actif() {
        return repo.findFirstByActifTrue().map(g -> lire(g.getStructure())).orElse(defaut());
    }

    public List<GabaritRapport> tous() { return repo.findAll(); }

    /** Enregistre un gabarit (nouvelle version ou mise à jour) après validation, et l'active. */
    @Transactional
    public GabaritRapport enregistrer(String structureJson) {
        Def d = lire(structureJson);
        valider(d);
        GabaritRapport g = repo.findAll().stream().filter(x -> x.getVersion().equals(d.version())).findFirst().orElseGet(GabaritRapport::new);
        repo.findAll().forEach(x -> { x.setActif(false); repo.save(x); });
        g.setVersion(d.version());
        g.setStructure(json(d));
        g.setActif(true);
        return repo.save(g);
    }

    private void valider(Def d) {
        if (d.version() == null || d.version().isBlank()) throw new IllegalArgumentException("Le gabarit doit porter une version.");
        Map<String, SectionDef> defaut = new HashMap<>();
        defaut().groupes().forEach(g -> g.sections().forEach(s -> defaut.put(s.code(), s)));
        Set<String> vus = new HashSet<>();
        for (GroupeDef g : d.groupes())
            for (SectionDef s : g.sections()) {
                if (!CODES.contains(s.code())) throw new IllegalArgumentException("Code de section inconnu : " + s.code());
                if (!vus.add(s.code())) throw new IllegalArgumentException("Section en double : " + s.code());
                if (s.colonnes() == null || s.colonnes().size() != defaut.get(s.code()).colonnes().size())
                    throw new IllegalArgumentException("La section " + s.code() + " doit avoir " + defaut.get(s.code()).colonnes().size() + " colonnes.");
            }
    }

    public Def lire(String json) {
        try {
            return mapper.readValue(json, Def.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Gabarit invalide : " + e.getMessage());
        }
    }

    public String json(Def d) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String romain(int n) {
        String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n - 1] : String.valueOf(n);
    }
}
