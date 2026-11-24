package com.projetweb.webapp.rapport.web;

import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.*;
import com.projetweb.webapp.rapport.service.RapportData.*;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.PublicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Fiche d'activité individuelle du chercheur (§17.6) : ses contributions validées, sans aucun CIN. */
@RestController
@RequestMapping("/api/me")
public class MeController {
    private final Acces acces;
    private final ChercheurRepository membres;
    private final PublicationRepository publications;
    private final ProductionRepository productions;
    private final TheseRepository theses;
    private final MasterSoutenuRepository masters;
    private final ProjetRepository projets;
    private final GenerationService generation;
    private final AuditService audit;

    public MeController(Acces acces, ChercheurRepository membres, PublicationRepository publications, ProductionRepository productions,
                        TheseRepository theses, MasterSoutenuRepository masters, ProjetRepository projets,
                        GenerationService generation, AuditService audit) {
        this.acces = acces; this.membres = membres; this.publications = publications; this.productions = productions;
        this.theses = theses; this.masters = masters; this.projets = projets; this.generation = generation; this.audit = audit;
    }

    @GetMapping("/activity")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> fiche(@RequestParam int year, @RequestParam(defaultValue = "pdf") String format) {
        Utilisateur u = acces.module();
        if (u.getChercheurId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Votre compte n'est relié à aucune fiche membre : demandez-le à l'administrateur.");
        Chercheur moi = membres.findById(u.getChercheurId()).orElseThrow(() -> new NoSuchElementException("Fiche membre introuvable"));
        Long id = moi.getId();
        Map<Long, Chercheur> parId = membres.findAll().stream().collect(Collectors.toMap(Chercheur::getId, c -> c));
        Function<List<Long>, String> noms = ids -> ids.stream().map(parId::get).filter(Objects::nonNull).map(Chercheur::getNomComplet).collect(Collectors.joining(", "));

        List<Section> s = new ArrayList<>();
        List<List<String>> pubs = publications.findByChercheur(id).stream().filter(p -> p.getStatut().pris() && Objects.equals(p.getAnnee(), year))
                .map(p -> List.of(p.getTitre(), Texte.nn(p.getVenue()), Texte.nn(p.getAnnee()), RapportService.libIndexation(p.getIndexation()), Texte.nn(p.getAuteursTexte()))).toList();
        s.add(sec("PUBLICATIONS", "1", "Publications", List.of("Titre", "Journal / conférence", "Année", "Indexation", "Auteurs"), pubs));
        for (var nat : List.of(Production.Nature.OUVRAGE, Production.Nature.CHAPITRE, Production.Nature.BREVET)) {
            List<List<String>> l = productions.findByNature(nat).stream().filter(p -> p.getStatut().pris() && Objects.equals(p.getAnnee(), year) && p.getAuteurIds().contains(id))
                    .map(p -> List.of(Texte.nn(p.getTitre()), Texte.nn(p.getAnnee()), Texte.nn(p.getEditeur() != null ? p.getEditeur() : p.getReference()), Texte.nn(p.getAuteursTexte()))).toList();
            String titre = switch (nat) { case OUVRAGE -> "Ouvrages"; case CHAPITRE -> "Chapitres d'ouvrage"; default -> "Brevets"; };
            s.add(sec(nat.name(), String.valueOf(s.size() + 1), titre, List.of("Titre", "Année", "Éditeur / référence", "Auteurs"), l));
        }
        s.add(sec("THESES", String.valueOf(s.size() + 1), "Thèses encadrées soutenues", List.of("Titre", "Année", "Doctorant", "Encadrant(s)"),
                theses.findAll().stream().filter(t -> t.getStatut().pris() && Objects.equals(t.getAnneeSoutenance(), year) && t.getEncadrantIds().contains(id))
                        .map(t -> List.of(Texte.nn(t.getTitre()), Texte.nn(t.getAnneeSoutenance()), Texte.nn(t.getDoctorantNom()), noms.apply(t.getEncadrantIds()))).toList()));
        s.add(sec("MASTERS", String.valueOf(s.size() + 1), "Mastères encadrés soutenus", List.of("Sujet", "Année", "Étudiant", "Encadrant(s)"),
                masters.findAll().stream().filter(t -> t.getStatut().pris() && Objects.equals(t.getAnneeSoutenance(), year) && t.getEncadrantIds().contains(id))
                        .map(t -> List.of(Texte.nn(t.getSujet()), Texte.nn(t.getAnneeSoutenance()), Texte.nn(t.getEtudiantNom()), noms.apply(t.getEncadrantIds()))).toList()));
        s.add(sec("PROJETS", String.valueOf(s.size() + 1), "Projets coordonnés", List.of("Intitulé", "Type", "Financeur"),
                projets.findAll().stream().filter(p -> p.getStatut().pris() && p.enCoursEn(year) && id.equals(p.getCoordinateurId()))
                        .map(p -> List.of(Texte.nn(p.getIntitule()), p.getType() == null ? "" : p.getType().name(), Texte.nn(p.getFinanceur()))).toList()));

        RapportData d = new RapportData(year, "", List.of(), "Fiche d'activité " + year, "", null, null,
                List.of(new Groupe("", "Contributions validées", s)), List.of(), Instant.now(), false, false);
        boolean pdf = !"docx".equalsIgnoreCase(format);
        String titre = "Fiche d'activité " + year + " – " + moi.getNomComplet();
        byte[] b = generation.ficheActivite(d, titre, "Document personnel, hors rapport officiel – sans numéro de CIN", pdf);
        audit.log(u.getEmail(), "FICHE_ACTIVITE", year + " (" + (pdf ? "PDF" : "Word") + ")");
        return RapportController.fichier(b, "LIMTIC_Fiche_activite_" + year + "_" + moi.getNom().replaceAll("\\W+", "_") + (pdf ? ".pdf" : ".docx"),
                pdf ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private static Section sec(String code, String num, String titre, List<String> cols, List<List<String>> lignes) {
        return new Section(code, num, titre, titre, cols, lignes, -1, "Total", String.valueOf(lignes.size()), null, null);
    }
}
