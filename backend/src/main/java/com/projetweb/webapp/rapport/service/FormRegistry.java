package com.projetweb.webapp.rapport.service;

import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.PublicationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.*;

/**
 * Description des formulaires F3 à F17 (§14.2) : entité, champs obligatoires, année de rattachement,
 * droit de saisie du chercheur et lien avec un membre. Un seul contrôleur générique s'appuie dessus.
 */
@Component
public class FormRegistry {

    public record Def<T extends FicheRapport>(
            String code, String formulaire, String libelle, Class<T> type, JpaRepository<T, Long> repo,
            Supplier<List<T>> tous, Consumer<T> init, Function<T, Integer> annee, boolean saisieChercheur,
            Function<T, List<String>> obligatoires, Function<T, String> titre, BiPredicate<T, Long> concerne) {}

    private final Map<String, Def<? extends FicheRapport>> defs = new LinkedHashMap<>();

    public FormRegistry(ChercheurRepository membres, DoctorantRepository doctorants, MasterienRepository masteriens,
                        PublicationRepository publications, ProductionRepository productions,
                        HabilitationRepository habilitations, TheseRepository theses, MasterSoutenuRepository masters,
                        EvenementRepository evenements, ConventionRepository conventions, ProjetRepository projets) {

        add(new Def<>("membres", "F3 / F5", "Membres (enseignants-chercheurs et cadres)", Chercheur.class, membres,
                membres::findAll, c -> {}, c -> null, false,
                c -> {
                    List<String> e = personne(c);
                    req(e, c.getGrade(), "grade");
                    req(e, c.getCategorie(), "catégorie");
                    req(e, c.getDateEntree(), "date d'entrée");
                    return e;
                },
                c -> c.getNomComplet(), (c, id) -> Objects.equals(c.getId(), id)));

        add(new Def<>("doctorants", "F4", "Doctorants", Doctorant.class, doctorants,
                doctorants::findAll, d -> {}, d -> null, true,
                d -> {
                    List<String> e = personne(d);
                    req(e, d.getSujet(), "sujet de thèse");
                    if (d.getDirecteurIds().isEmpty() && Texte.vide(d.getDirecteursTexte())) e.add("directeur(s) de thèse");
                    req(e, d.getAnneePremiereInscription(), "année de première inscription");
                    if (d.getStatutThese() == Doctorant.Statut.SOUTENU) req(e, d.getDateSoutenance(), "date de soutenance");
                    return e;
                },
                PersonneRapport::getNomComplet, (d, id) -> d.getDirecteurIds().contains(id)));

        add(new Def<>("masteriens", "F6", "Étudiants en mastère de recherche", Masterien.class, masteriens,
                masteriens::findAll, m -> {}, m -> null, true,
                m -> {
                    List<String> e = personne(m);
                    req(e, m.getSujet(), "sujet de mémoire");
                    if (m.getEncadrantIds().isEmpty() && Texte.vide(m.getEncadrantsTexte())) e.add("encadrant(s)");
                    req(e, m.getAnneePremiereInscription(), "année de première inscription");
                    if (m.getStatutMastere() == Masterien.Statut.DIPLOME) req(e, m.getDateSoutenance(), "date de soutenance");
                    return e;
                },
                PersonneRapport::getNomComplet, (m, id) -> m.getEncadrantIds().contains(id)));

        add(new Def<>("publications", "F7", "Publications", Publication.class, publications,
                publications::findAll, p -> {}, Publication::getAnnee, true,
                p -> {
                    List<String> e = new ArrayList<>();
                    req(e, p.getType(), "type de publication");
                    req(e, p.getTitre(), "titre");
                    req(e, p.getVenue(), "titre du journal ou de la conférence");
                    req(e, p.getAnnee(), "année");
                    req(e, p.getIndexation(), "indexation");
                    req(e, p.getAuteursTexte(), "auteurs");
                    if (p.getChercheurs().isEmpty()) e.add("au moins un auteur interne (membre du laboratoire)");
                    return e;
                },
                Publication::getTitre, (p, id) -> p.getChercheurs().stream().anyMatch(c -> c.getId().equals(id))));

        production(productions, "ouvrages", "F8", "Ouvrages scientifiques", Production.Nature.OUVRAGE, p -> {
            List<String> e = new ArrayList<>();
            req(e, p.getTitre(), "titre"); req(e, p.getAnnee(), "année"); req(e, p.getEditeur(), "éditeur");
            req(e, p.getAuteursTexte(), "auteurs");
            if (p.getAuteurIds().isEmpty()) e.add("au moins un auteur interne");
            return e;
        });
        production(productions, "chapitres", "F9", "Chapitres d'ouvrage", Production.Nature.CHAPITRE, p -> {
            List<String> e = new ArrayList<>();
            req(e, p.getTitre(), "titre du chapitre"); req(e, p.getAnnee(), "année"); req(e, p.getEditeur(), "éditeur");
            req(e, p.getEdition(), "ouvrage / collection"); req(e, p.getAuteursTexte(), "auteurs");
            if (p.getAuteurIds().isEmpty()) e.add("au moins un auteur interne");
            return e;
        });
        production(productions, "brevets", "F10", "Brevets d'invention", Production.Nature.BREVET, p -> {
            List<String> e = new ArrayList<>();
            req(e, p.getTypeBrevet(), "type"); req(e, p.getAnnee(), "année de dépôt");
            req(e, p.getReference(), "référence"); req(e, p.getTitre(), "intitulé"); req(e, p.getAuteursTexte(), "inventeurs");
            return e;
        });
        production(productions, "obtentions", "F11", "Obtentions végétales", Production.Nature.OBTENTION_VEGETALE, p -> {
            List<String> e = new ArrayList<>();
            req(e, p.getTitre(), "titre"); req(e, p.getAnnee(), "année"); req(e, p.getReference(), "référence");
            return e;
        });

        add(new Def<>("habilitations", "F12", "Habilitations et agrégations HU", Habilitation.class, habilitations,
                habilitations::findAll, h -> {}, Habilitation::getAnnee, true,
                h -> {
                    List<String> e = new ArrayList<>();
                    req(e, h.getTitre(), "titre"); req(e, h.getAnnee(), "année"); req(e, h.getNature(), "nature");
                    if (h.getTitulaireId() == null && Texte.vide(h.getTitulaireNom())) e.add("titulaire");
                    if (h.getEncadreurId() == null && Texte.vide(h.getEncadreurNom())) e.add("encadreur");
                    return e;
                },
                Habilitation::getTitre, (h, id) -> id.equals(h.getTitulaireId()) || id.equals(h.getEncadreurId())));

        add(new Def<>("theses", "F13", "Thèses soutenues", These.class, theses,
                theses::findAll, t -> {}, These::getAnneeSoutenance, true,
                t -> {
                    List<String> e = new ArrayList<>();
                    req(e, t.getTitre(), "titre de la thèse"); req(e, t.getAnneeSoutenance(), "année de soutenance");
                    req(e, t.getAnneePremiereInscription(), "année de première inscription"); req(e, t.getSujet(), "sujet");
                    if (t.getEncadrantIds().isEmpty() && Texte.vide(t.getEncadrantsTexte())) e.add("encadrant(s)");
                    return e;
                },
                These::getTitre, (t, id) -> t.getEncadrantIds().contains(id)));

        add(new Def<>("masters", "F14", "Mastères soutenus", MasterSoutenu.class, masters,
                masters::findAll, m -> {}, MasterSoutenu::getAnneeSoutenance, true,
                m -> {
                    List<String> e = new ArrayList<>();
                    req(e, m.getAnneeSoutenance(), "année de soutenance");
                    req(e, m.getAnneePremiereInscription(), "année de première inscription"); req(e, m.getSujet(), "sujet");
                    if (m.getEncadrantIds().isEmpty() && Texte.vide(m.getEncadrantsTexte())) e.add("encadrant(s)");
                    return e;
                },
                MasterSoutenu::getSujet, (m, id) -> m.getEncadrantIds().contains(id)));

        add(new Def<>("evenements", "F15", "Séminaires, journées et ateliers", Evenement.class, evenements,
                evenements::findAll, ev -> {}, Evenement::anneeEffective, false,
                ev -> {
                    List<String> e = new ArrayList<>();
                    req(e, ev.getTitre(), "titre"); req(e, ev.getType(), "type"); req(e, ev.anneeEffective(), "année ou date");
                    req(e, ev.getLieu(), "lieu"); req(e, ev.getOrganisateurs(), "organisateurs");
                    return e;
                },
                Evenement::getTitre, (ev, id) -> false));

        add(new Def<>("conventions", "F16", "Conventions", Convention.class, conventions,
                conventions::findAll, c -> {}, Convention::getAnnee, false,
                c -> {
                    List<String> e = new ArrayList<>();
                    req(e, c.getAnnee(), "année de signature"); req(e, c.getReference(), "référence"); req(e, c.getType(), "type");
                    return e;
                },
                Convention::getReference, (c, id) -> id.equals(c.getResponsableId())));

        add(new Def<>("projets", "F17", "Projets de recherche", Projet.class, projets,
                projets::findAll, p -> {}, p -> null, true,
                p -> {
                    List<String> e = new ArrayList<>();
                    req(e, p.getIntitule(), "intitulé"); req(e, p.getType(), "type");
                    if (p.getCoordinateurId() == null && Texte.vide(p.getCoordinateurExterne())) e.add("coordinateur");
                    return e;
                },
                Projet::getIntitule, (p, id) -> id.equals(p.getCoordinateurId()) || p.getMembreIds().contains(id)));
    }

    private void production(ProductionRepository repo, String code, String f, String libelle, Production.Nature nature,
                            Function<Production, List<String>> obligatoires) {
        add(new Def<>(code, f, libelle, Production.class, repo,
                () -> repo.findByNature(nature), p -> p.setNature(nature), Production::getAnnee, true,
                obligatoires, Production::getTitre, (p, id) -> p.getAuteurIds().contains(id)));
    }

    private void add(Def<? extends FicheRapport> d) { defs.put(d.code(), d); }

    @SuppressWarnings("unchecked")
    public Def<FicheRapport> get(String code) {
        Def<? extends FicheRapport> d = defs.get(code);
        if (d == null) throw new NoSuchElementException("Formulaire inconnu : " + code);
        return (Def<FicheRapport>) d;
    }

    public Collection<Def<? extends FicheRapport>> toutes() { return defs.values(); }

    // ── Aides ──
    static List<String> personne(PersonneRapport p) {
        List<String> e = new ArrayList<>();
        req(e, p.getNom(), "nom"); req(e, p.getPrenom(), "prénom");
        if (!p.isEtranger()) {
            if (Texte.vide(p.getCin())) e.add("N° CIN");
            else if (!p.getCin().matches("\\d{8}")) e.add("N° CIN valide (8 chiffres)");
        }
        req(e, p.getEtablissement(), "établissement"); req(e, p.getUniversite(), "université");
        return e;
    }

    static void req(List<String> e, Object v, String libelle) {
        if (v == null || (v instanceof String s && s.isBlank())) e.add(libelle);
    }
}
