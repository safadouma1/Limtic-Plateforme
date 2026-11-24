package com.projetweb.webapp.rapport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.PublicationRepository;
import com.projetweb.webapp.securite.CinCrypto;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Import CSV ou Excel des données historiques (§17.8) avec compte rendu ligne par ligne
 * (importée, rejetée avec motif, doublon). Les colonnes reprennent celles des tableaux du rapport.
 * Le jeu d'essai 2025 (CIN fictifs) est fourni dans src/main/resources/donnees-essai/2025.
 */
@Service
public class ImportService {

    public record LigneImport(int ligne, String statut, String message) {}
    public record CompteRendu(String categorie, int importees, int rejetees, int doublons, List<LigneImport> lignes) {}

    public static final List<String> CATEGORIES = List.of("membres", "doctorants", "masteriens", "publications", "ouvrages",
            "chapitres", "theses", "masters", "evenements", "conventions", "projets");

    private final ChercheurRepository membres;
    private final DoctorantRepository doctorants;
    private final MasterienRepository masteriens;
    private final PublicationRepository publications;
    private final ProductionRepository productions;
    private final TheseRepository theses;
    private final MasterSoutenuRepository masters;
    private final EvenementRepository evenements;
    private final ConventionRepository conventions;
    private final ProjetRepository projets;
    private final StructureIdentiteRepository identites;
    private final ResumeRapportRepository resumes;
    private final CampagneRepository campagnes;
    private final CampagneService campagneService;
    private final Correspondances corr;
    private final CinCrypto crypto;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public ImportService(ChercheurRepository membres, DoctorantRepository doctorants, MasterienRepository masteriens,
                         PublicationRepository publications, ProductionRepository productions, TheseRepository theses,
                         MasterSoutenuRepository masters, EvenementRepository evenements, ConventionRepository conventions,
                         ProjetRepository projets, StructureIdentiteRepository identites, ResumeRapportRepository resumes,
                         CampagneRepository campagnes, CampagneService campagneService, Correspondances corr,
                         CinCrypto crypto, AuditService audit, ObjectMapper mapper) {
        this.membres = membres; this.doctorants = doctorants; this.masteriens = masteriens; this.publications = publications;
        this.productions = productions; this.theses = theses; this.masters = masters; this.evenements = evenements;
        this.conventions = conventions; this.projets = projets; this.identites = identites; this.resumes = resumes;
        this.campagnes = campagnes; this.campagneService = campagneService; this.corr = corr; this.crypto = crypto;
        this.audit = audit; this.mapper = mapper;
    }

    // ═══ Point d'entrée ═════════════════════════════════════════════════════

    @Transactional
    public CompteRendu importer(String categorie, String nomFichier, byte[] contenu, Integer anneeDefaut, String auteur) {
        if (!CATEGORIES.contains(categorie)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catégorie d'import inconnue : " + categorie);
        List<Map<String, String>> lignes = lire(nomFichier, contenu);
        Imp imp = new Imp(categorie, anneeDefaut, auteur);
        int num = 1;
        for (Map<String, String> l : lignes) {
            num++;
            if (l.values().stream().allMatch(Texte::vide)) continue;
            try {
                String msg = switch (categorie) {
                    case "membres" -> imp.membre(l);
                    case "doctorants" -> imp.doctorant(l);
                    case "masteriens" -> imp.masterien(l);
                    case "publications" -> imp.publication(l);
                    case "ouvrages" -> imp.production(l, Production.Nature.OUVRAGE);
                    case "chapitres" -> imp.production(l, Production.Nature.CHAPITRE);
                    case "theses" -> imp.these(l);
                    case "masters" -> imp.master(l);
                    case "evenements" -> imp.evenement(l);
                    case "conventions" -> imp.convention(l);
                    case "projets" -> imp.projet(l);
                    default -> throw new IllegalStateException();
                };
                if (msg != null && msg.startsWith("DOUBLON:")) imp.ajouter(num, "DOUBLON", msg.substring(8));
                else imp.ajouter(num, "IMPORTEE", msg == null ? "" : msg);
            } catch (Rejet r) {
                imp.ajouter(num, "REJETEE", r.getMessage());
            }
        }
        CompteRendu cr = imp.compteRendu();
        audit.log(auteur, "IMPORT", categorie + " (" + nomFichier + ") : " + cr.importees() + " importée(s), "
                + cr.rejetees() + " rejetée(s), " + cr.doublons() + " doublon(s)");
        return cr;
    }

    /** Charge le jeu d'essai 2025 complet : membres, productions, identité (F1), résumé (F2) et campagne. */
    @Transactional
    public List<CompteRendu> importerJeuEssai(String auteur) {
        int annee = 2025;
        List<CompteRendu> r = new ArrayList<>();
        for (String c : List.of("membres", "doctorants", "masteriens", "publications", "chapitres", "theses", "masters",
                "evenements", "conventions", "projets")) {
            try (InputStream in = new ClassPathResource("donnees-essai/2025/" + c + ".csv").getInputStream()) {
                r.add(importer(c, c + ".csv", in.readAllBytes(), annee, auteur));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (campagnes.findByAnnee(annee).isEmpty()) {
            Campagne camp = campagneService.creerSansControle(new CampagneService.Creation(annee, LocalDate.of(annee, 9, 1),
                    LocalDate.of(annee, 12, 15), LocalDate.of(annee, 10, 31)));
            camp.setStatut(Campagne.Statut.REVUE);
            campagnes.save(camp);
        }
        if (identites.findById(1L).isEmpty()) {
            StructureIdentite id = new StructureIdentite();
            id.setUniversite("UNIVERSITE DE TUNIS EL MANAR");
            id.setEtablissement("INSTITUT SUPERIEUR D'INFORMATIQUE A L'ARIANA");
            id.setDenomination("Informatique, modélisation et traitement de l'information et de la connaissance");
            id.setCode("LR16ES06");
            membres.findAll().stream().filter(m -> "ZAGROUBA".equals(m.getNom()) && m.getPrenom().startsWith("Ezz")).findFirst()
                    .ifPresent(m -> { id.setChefId(m.getId()); id.setChefNom(m.getNomComplet()); id.setChefGrade(m.getGrade()); });
            id.setFonction("Directeur du laboratoire");
            id.setTelephone("+216 71 706 164");
            id.setFax("+216 71 706 698");
            id.setEmail("ezzeddine.zagrouba@isi.utm.tn");
            id.setSiteWeb("www.isi.rnu.tn");
            identites.save(id);
        }
        try (InputStream in = new ClassPathResource("donnees-essai/2025/resume.json").getInputStream()) {
            JsonNode j = mapper.readTree(in);
            ResumeRapport rr = resumes.findByAnnee(annee).orElseGet(() -> { ResumeRapport x = new ResumeRapport(); x.setAnnee(annee); return x; });
            rr.setHistorique(j.path("historique").asText());
            rr.setThematiques(j.path("thematiques").asText());
            rr.setBilan(j.path("bilan").asText());
            resumes.save(rr);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    // ═══ Lecture CSV / Excel ════════════════════════════════════════════════

    static List<Map<String, String>> lire(String nom, byte[] contenu) {
        List<List<String>> brut = nom != null && nom.toLowerCase().matches(".*\\.xlsx?$") ? excel(contenu) : csv(contenu);
        if (brut.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide.");
        List<String> entetes = brut.get(0).stream().map(ImportService::cle).toList();
        List<Map<String, String>> res = new ArrayList<>();
        for (List<String> l : brut.subList(1, brut.size())) {
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < entetes.size(); i++) m.put(entetes.get(i), i < l.size() && l.get(i) != null ? l.get(i).trim() : "");
            res.add(m);
        }
        return res;
    }

    private static String cle(String s) { return Texte.norm(s).replace(" ", ""); }

    static List<List<String>> csv(byte[] contenu) {
        String t = new String(contenu, StandardCharsets.UTF_8);
        if (t.startsWith("﻿")) t = t.substring(1);
        String premiere = t.lines().findFirst().orElse("");
        char sep = premiere.chars().filter(ch -> ch == ';').count() >= premiere.chars().filter(ch -> ch == ',').count() ? ';' : ',';
        List<List<String>> lignes = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean guillemets = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (guillemets) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') { champ.append('"'); i++; }
                    else guillemets = false;
                } else champ.append(c);
            } else if (c == '"') guillemets = true;
            else if (c == sep) { cur.add(champ.toString()); champ.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                cur.add(champ.toString()); champ.setLength(0);
                lignes.add(cur); cur = new ArrayList<>();
            } else champ.append(c);
        }
        if (champ.length() > 0 || !cur.isEmpty()) { cur.add(champ.toString()); lignes.add(cur); }
        return lignes;
    }

    static List<List<String>> excel(byte[] contenu) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet s = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.FRANCE);
            List<List<String>> l = new ArrayList<>();
            for (Row row : s) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
                    Cell c = row.getCell(i);
                    cells.add(c == null ? "" : fmt.formatCellValue(c));
                }
                l.add(cells);
            }
            return l;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier Excel illisible : " + e.getMessage());
        }
    }

    // ═══ Correspondance des noms vers les membres ═══════════════════════════

    /** Retrouve les membres cités dans un texte libre (liste d'auteurs, encadrants), dans l'ordre d'apparition. */
    public List<Long> membresCites(String texte) {
        if (Texte.vide(texte)) return new ArrayList<>();
        String t = " " + Texte.norm(texte) + " ";
        List<Chercheur> tous = membres.findAll();
        Map<String, Long> nbParNom = new HashMap<>();
        tous.forEach(m -> nbParNom.merge(Texte.norm(m.getNom()), 1L, Long::sum));
        TreeMap<Integer, Long> trouves = new TreeMap<>();
        for (Chercheur m : tous) {
            String nom = Texte.norm(m.getNom());
            int pos = t.indexOf(" " + nom + " ");
            if (nom.isEmpty() || pos < 0) continue;
            String prenom1 = Texte.norm(m.getPrenom()).split(" ")[0];
            boolean prenomPresent = !prenom1.isEmpty() && t.contains(" " + prenom1 + " ");
            if (prenomPresent || nbParNom.get(nom) == 1) trouves.putIfAbsent(pos, m.getId());
        }
        return new ArrayList<>(new LinkedHashSet<>(trouves.values()));
    }

    // ═══ Import par catégorie ═══════════════════════════════════════════════

    private static final class Rejet extends RuntimeException { Rejet(String m) { super(m); } }

    private final class Imp {
        final String categorie;
        final Integer anneeDefaut;
        final String auteur;
        final List<LigneImport> lignes = new ArrayList<>();

        Imp(String categorie, Integer anneeDefaut, String auteur) { this.categorie = categorie; this.anneeDefaut = anneeDefaut; this.auteur = auteur; }

        void ajouter(int n, String statut, String msg) { lignes.add(new LigneImport(n, statut, msg)); }

        CompteRendu compteRendu() {
            return new CompteRendu(categorie, (int) lignes.stream().filter(l -> l.statut().equals("IMPORTEE")).count(),
                    (int) lignes.stream().filter(l -> l.statut().equals("REJETEE")).count(),
                    (int) lignes.stream().filter(l -> l.statut().equals("DOUBLON")).count(), lignes);
        }

        <T extends FicheRapport> T valide(T f, Integer annee) {
            f.setStatut(StatutFiche.VALIDE);
            f.setAnneeReference(annee != null ? annee : anneeDefaut);
            f.setCreePar(auteur);
            f.setCreeLe(Instant.now());
            f.setValidePar(auteur);
            f.setValideLe(Instant.now());
            return f;
        }

        String obl(Map<String, String> l, String cle, String libelle) {
            String v = l.getOrDefault(cle, "");
            if (Texte.vide(v)) throw new Rejet("champ obligatoire manquant : " + libelle);
            return v.trim();
        }

        Integer annee(Map<String, String> l, String cle, boolean obligatoire) {
            String v = l.getOrDefault(cle, "").trim();
            if (v.isEmpty()) { if (obligatoire) throw new Rejet("année manquante (" + cle + ")"); return null; }
            try {
                int a = Integer.parseInt(v.replaceAll("\\D.*$", ""));
                if (a < 1950 || a > 2100) throw new Rejet("année non plausible : " + v);
                return a;
            } catch (NumberFormatException e) {
                throw new Rejet("année invalide : " + v);
            }
        }

        LocalDate date(Map<String, String> l, String cle) {
            String v = l.getOrDefault(cle, "").trim();
            if (v.isEmpty()) return null;
            try { return LocalDate.parse(v); } catch (Exception e) { throw new Rejet("date invalide (AAAA-MM-JJ attendu) : " + v); }
        }

        <E extends Enum<E>> E enumeration(Class<E> type, String v, E defaut) {
            if (Texte.vide(v)) return defaut;
            try { return Enum.valueOf(type, v.trim().toUpperCase().replace(' ', '_').replace('-', '_')); }
            catch (Exception e) { throw new Rejet("valeur inconnue « " + v + " » (attendu : " + Arrays.toString(type.getEnumConstants()) + ")"); }
        }

        /** Champs communs aux personnes ; retourne une note sur le CIN. */
        String personne(PersonneRapport p, Map<String, String> l, List<? extends PersonneRapport> existants) {
            p.setNom(obl(l, "nom", "nom").toUpperCase());
            p.setPrenom(obl(l, "prenom", "prénom"));
            p.setEtablissement(l.getOrDefault("etablissement", ""));
            p.setUniversite(l.getOrDefault("universite", ""));
            String piece = l.getOrDefault("typepiece", "CIN");
            String cin = l.getOrDefault("cin", "").replaceAll("\\s", "");
            String note = "";
            if (piece.equalsIgnoreCase("PASSEPORT")) { p.setEtranger(true); note = "pièce étrangère (passeport) : pas de CIN"; }
            else if (cin.isEmpty()) note = "CIN manquant : à compléter (contrôle bloquant)";
            else if (!cin.matches("\\d{8}")) throw new Rejet("CIN invalide (8 chiffres attendus)");
            else {
                String e = crypto.empreinte(cin);
                if (existants.stream().anyMatch(x -> e.equals(x.getCinEmpreinte()))) throw new DoublonEx("CIN déjà présent");
                p.setCin(cin);
            }
            if (existants.stream().anyMatch(x -> Texte.norm(x.getNom()).equals(Texte.norm(p.getNom())) && Texte.norm(x.getPrenom()).equals(Texte.norm(p.getPrenom()))))
                throw new DoublonEx(p.getNomComplet() + " existe déjà");
            return note;
        }

        String membre(Map<String, String> l) {
            try {
                Chercheur c = new Chercheur();
                String note = personne(c, l, membres.findAll());
                c.setCategorie(enumeration(Chercheur.Categorie.class, l.get("categorie"), Chercheur.Categorie.ENSEIGNANT_CHERCHEUR));
                c.setGrade(obl(l, "grade", "grade"));
                if (c.getCategorie() == Chercheur.Categorie.ENSEIGNANT_CHERCHEUR && corr.corps(c.getGrade()) == null)
                    note = (note.isEmpty() ? "" : note + " ; ") + "grade « " + c.getGrade() + " » sans corps A/B dans la table de correspondance";
                if (!Texte.vide(l.get("email"))) c.setEmail(l.get("email").trim().toLowerCase());
                c.setDateEntree(date(l, "dateentree"));
                c.setDateSortie(date(l, "datesortie"));
                membres.save(valide(c, null));
                return note;
            } catch (DoublonEx d) { return "DOUBLON:" + d.getMessage(); }
        }

        String doctorant(Map<String, String> l) {
            try {
                Doctorant d = new Doctorant();
                String note = personne(d, l, doctorants.findAll());
                d.setSujet(l.getOrDefault("sujet", ""));
                d.setDirecteursTexte(l.getOrDefault("directeurs", ""));
                d.setDirecteurIds(membresCites(d.getDirecteursTexte()));
                d.setAnneePremiereInscription(annee(l, "anneepremiereinscription", false));
                d.setStatutThese(enumeration(Doctorant.Statut.class, l.get("statut"), Doctorant.Statut.EN_COURS));
                doctorants.save(valide(d, null));
                return note;
            } catch (DoublonEx e) { return "DOUBLON:" + e.getMessage(); }
        }

        String masterien(Map<String, String> l) {
            try {
                Masterien m = new Masterien();
                String note = personne(m, l, masteriens.findAll());
                m.setSujet(l.getOrDefault("sujet", ""));
                m.setEncadrantsTexte(l.getOrDefault("encadrants", ""));
                m.setEncadrantIds(membresCites(m.getEncadrantsTexte()));
                m.setAnneePremiereInscription(annee(l, "anneepremiereinscription", false));
                m.setStatutMastere(enumeration(Masterien.Statut.class, l.get("statut"), Masterien.Statut.EN_COURS));
                masteriens.save(valide(m, null));
                return note;
            } catch (DoublonEx e) { return "DOUBLON:" + e.getMessage(); }
        }

        String publication(Map<String, String> l) {
            Publication p = new Publication();
            p.setTitre(obl(l, "titre", "titre"));
            p.setVenue(obl(l, "venue", "titre du journal ou de la conférence"));
            p.setAnnee(annee(l, "annee", true));
            p.setDoi(Texte.vide(l.get("doi")) ? null : l.get("doi").trim());
            String note = "";
            String ix = Texte.norm(l.getOrDefault("indexation", ""));
            switch (ix) {
                case "wos", "jcr", "wos jcr", "web of science" -> p.setIndexation(Publication.Indexation.WOS);
                case "scopus" -> p.setIndexation(Publication.Indexation.SCOPUS);
                case "autres", "autre" -> p.setIndexation(Publication.Indexation.AUTRES);
                case "aucune", "nationale" -> p.setIndexation(Publication.Indexation.AUCUNE);
                case "" -> throw new Rejet("indexation manquante");
                default -> { p.setIndexation(Publication.Indexation.AUTRES); note = "indexation « " + l.get("indexation") + " » ramenée à « Autres »"; }
            }
            p.setAuteursTexte(obl(l, "auteurs", "auteurs"));
            p.setType(enumeration(Publication.Type.class, l.get("type"), Publication.Type.ARTICLE_JOURNAL));
            String imp = Texte.norm(l.getOrDefault("impactee", ""));
            boolean proposee = corr.impactee(p.getIndexation());
            p.setEstImpactee(imp.isEmpty() ? proposee : imp.startsWith("o") || imp.equals("true") || imp.equals("1"));
            String titre = Texte.norm(p.getTitre());
            boolean doublon = publications.findAll().stream().anyMatch(x -> Texte.norm(x.getTitre()).equals(titre)
                    || (p.getDoi() != null && p.getDoi().equalsIgnoreCase(x.getDoi())));
            if (doublon) return "DOUBLON:publication déjà présente (titre ou DOI identique)";
            List<Long> ids = membresCites(p.getAuteursTexte());
            p.setChercheurs(new LinkedHashSet<>(membres.findAllById(ids)));
            if (ids.isEmpty()) note = (note.isEmpty() ? "" : note + " ; ") + "aucun auteur interne reconnu (contrôle bloquant)";
            publications.save(valide(p, p.getAnnee()));
            return note.isEmpty() ? ids.size() + " auteur(s) interne(s) reconnu(s)" : note;
        }

        String production(Map<String, String> l, Production.Nature nature) {
            Production p = new Production();
            p.setNature(nature);
            p.setTitre(obl(l, "titre", "titre"));
            p.setAnnee(annee(l, "annee", true));
            p.setEditeur(l.getOrDefault("editeur", ""));
            p.setEdition(l.getOrDefault("edition", ""));
            p.setAuteursTexte(l.getOrDefault("auteurs", ""));
            p.setIsbn(Texte.vide(l.get("isbn")) ? null : l.get("isbn"));
            p.setAuteurIds(membresCites(p.getAuteursTexte()));
            if (productions.findByNature(nature).stream().anyMatch(x -> Texte.norm(x.getTitre()).equals(Texte.norm(p.getTitre()))))
                return "DOUBLON:déjà présent";
            productions.save(valide(p, p.getAnnee()));
            return p.getAuteurIds().isEmpty() ? "aucun auteur interne reconnu" : p.getAuteurIds().size() + " auteur(s) interne(s) reconnu(s)";
        }

        String these(Map<String, String> l) {
            These t = new These();
            t.setTitre(obl(l, "titre", "titre"));
            t.setAnneeSoutenance(annee(l, "anneesoutenance", true));
            t.setAnneePremiereInscription(annee(l, "anneepremiereinscription", false));
            t.setSujet(l.getOrDefault("sujet", ""));
            t.setEncadrantsTexte(l.getOrDefault("encadrants", ""));
            t.setEncadrantIds(membresCites(t.getEncadrantsTexte()));
            if (!t.getEncadrantIds().isEmpty()) t.setEncadrantsTexte(null);
            if (theses.findAll().stream().anyMatch(x -> Texte.norm(x.getTitre()).equals(Texte.norm(t.getTitre())))) return "DOUBLON:thèse déjà présente";
            theses.save(valide(t, t.getAnneeSoutenance()));
            return t.getEncadrantIds().isEmpty() ? "encadrant non reconnu parmi les membres" : "";
        }

        String master(Map<String, String> l) {
            MasterSoutenu m = new MasterSoutenu();
            m.setAnneeSoutenance(annee(l, "anneesoutenance", true));
            m.setAnneePremiereInscription(annee(l, "anneepremiereinscription", false));
            m.setSujet(obl(l, "sujet", "sujet"));
            m.setEncadrantsTexte(l.getOrDefault("encadrants", ""));
            m.setEncadrantIds(membresCites(m.getEncadrantsTexte()));
            if (!m.getEncadrantIds().isEmpty()) m.setEncadrantsTexte(null);
            if (masters.findAll().stream().anyMatch(x -> Texte.norm(x.getSujet()).equals(Texte.norm(m.getSujet())))) return "DOUBLON:mastère déjà présent";
            masters.save(valide(m, m.getAnneeSoutenance()));
            return m.getEncadrantIds().isEmpty() ? "encadrant non reconnu parmi les membres" : "";
        }

        String evenement(Map<String, String> l) {
            Evenement e = new Evenement();
            e.setTitre(obl(l, "titre", "titre"));
            e.setAnnee(annee(l, "annee", false));
            e.setDateDebut(date(l, "datedebut"));
            if (e.getDateDebut() != null) e.setAnnee(e.getDateDebut().getYear());
            if (e.getAnnee() == null) throw new Rejet("année ou date de début manquante");
            e.setOrganisateurs(Texte.vide(l.get("organisateurs")) ? "LIMTIC" : l.get("organisateurs"));
            e.setLieu(l.getOrDefault("lieu", ""));
            e.setType(enumeration(Evenement.Type.class, l.get("type"), Evenement.Type.SEMINAIRE));
            e.setOrganiseParLabo(!"non".equalsIgnoreCase(l.getOrDefault("organiseparlabo", "oui")));
            if (evenements.findAll().stream().anyMatch(x -> Texte.norm(x.getTitre()).equals(Texte.norm(e.getTitre())) && Objects.equals(x.getAnnee(), e.getAnnee())))
                return "DOUBLON:événement déjà présent";
            evenements.save(valide(e, e.getAnnee()));
            return "";
        }

        String convention(Map<String, String> l) {
            Convention c = new Convention();
            c.setAnnee(annee(l, "annee", true));
            c.setReference(obl(l, "reference", "référence"));
            c.setType(enumeration(Convention.Type.class, l.get("type"), Convention.Type.SOCIO_ECONOMIQUE));
            c.setPartenaire(Texte.vide(l.get("partenaire")) ? c.getReference() : l.get("partenaire"));
            if (conventions.findAll().stream().anyMatch(x -> Texte.norm(x.getReference()).equals(Texte.norm(c.getReference())) && Objects.equals(x.getAnnee(), c.getAnnee())))
                return "DOUBLON:convention déjà présente";
            conventions.save(valide(c, c.getAnnee()));
            return "";
        }

        String projet(Map<String, String> l) {
            Projet p = new Projet();
            p.setIntitule(obl(l, "intitule", "intitulé"));
            String coord = obl(l, "coordinateur", "coordinateur");
            List<Long> ids = membresCites(coord);
            if (ids.size() == 1) p.setCoordinateurId(ids.get(0)); else p.setCoordinateurExterne(coord);
            p.setType(enumeration(Projet.Type.class, l.get("type"), Projet.Type.NATIONAL));
            p.setDateDebut(date(l, "datedebut"));
            p.setDateFin(date(l, "datefin"));
            p.setFinanceur(l.getOrDefault("financeur", ""));
            if (projets.findAll().stream().anyMatch(x -> Texte.norm(x.getIntitule()).equals(Texte.norm(p.getIntitule())))) return "DOUBLON:projet déjà présent";
            projets.save(valide(p, null));
            return p.getCoordinateurId() != null ? "coordinateur relié à sa fiche membre" : "coordinateur extérieur au laboratoire";
        }
    }

    private static final class DoublonEx extends RuntimeException { DoublonEx(String m) { super(m); } }

    @SuppressWarnings("unused")
    private static <T> Function<T, T> id() { return x -> x; }
}
