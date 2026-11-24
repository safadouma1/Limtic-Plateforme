package com.projetweb.webapp.rapport.service;

import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.RapportData.*;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.DomaineRepository;
import com.projetweb.webapp.repository.PublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Calcul du rapport (§15) : récapitulatif entièrement calculé à partir des enregistrements validés de
 * l'année N, tableaux détaillés, et contrôles bloquants / avertissements avant génération (§15.3).
 */
@Service
public class RapportService {

    public static final List<String> RUBRIQUES_BUDGET = List.of("Équipements", "Sous-traitance", "Consommables et petits matériels",
            "Missions", "Stages", "Déplacement et hébergement", "Manifestations scientifiques", "Vacations",
            "Documentation et réseaux", "Maintenance et divers");

    private final ChercheurRepository membres;
    private final DoctorantRepository doctorants;
    private final MasterienRepository masteriens;
    private final PublicationRepository publications;
    private final ProductionRepository productions;
    private final HabilitationRepository habilitations;
    private final TheseRepository theses;
    private final MasterSoutenuRepository masters;
    private final EvenementRepository evenements;
    private final ConventionRepository conventions;
    private final ProjetRepository projets;
    private final CampagneRepository campagnes;
    private final StructureIdentiteRepository identites;
    private final ResumeRapportRepository resumes;
    private final LigneBudgetRepository budgets;
    private final DomaineRepository domaines;
    private final Correspondances corr;
    private final Gabarit gabarit;

    public RapportService(ChercheurRepository membres, DoctorantRepository doctorants, MasterienRepository masteriens,
                          PublicationRepository publications, ProductionRepository productions, HabilitationRepository habilitations,
                          TheseRepository theses, MasterSoutenuRepository masters, EvenementRepository evenements,
                          ConventionRepository conventions, ProjetRepository projets, CampagneRepository campagnes,
                          StructureIdentiteRepository identites, ResumeRapportRepository resumes, LigneBudgetRepository budgets,
                          DomaineRepository domaines, Correspondances corr, Gabarit gabarit) {
        this.membres = membres; this.doctorants = doctorants; this.masteriens = masteriens; this.publications = publications;
        this.productions = productions; this.habilitations = habilitations; this.theses = theses; this.masters = masters;
        this.evenements = evenements; this.conventions = conventions; this.projets = projets; this.campagnes = campagnes;
        this.identites = identites; this.resumes = resumes; this.budgets = budgets; this.domaines = domaines;
        this.corr = corr; this.gabarit = gabarit;
    }

    /** Contexte de calcul d'une année. */
    private final class Ctx {
        final int n;
        final boolean soumis;
        final Campagne campagne;
        final Map<Long, Chercheur> parId;
        final Correspondances.Function1 corps;

        Ctx(int n, boolean soumis) {
            this.n = n;
            this.soumis = soumis;
            this.campagne = campagnes.findByAnnee(n).orElseGet(() -> { Campagne c = new Campagne(); c.setAnnee(n); return c; });
            this.parId = membres.findAll().stream().collect(Collectors.toMap(Chercheur::getId, c -> c));
            this.corps = corr.corpsFn();
        }

        boolean retenu(FicheRapport f) {
            return f.getStatut() == null || f.getStatut().pris() || (soumis && f.getStatut() == StatutFiche.SOUMIS);
        }

        String noms(List<Long> ids, String texte) {
            String s = ids == null ? "" : ids.stream().map(parId::get).filter(Objects::nonNull)
                    .map(Chercheur::getNomComplet).collect(Collectors.joining(", "));
            if (!Texte.vide(texte)) {
                // Ajoute les noms externes saisis en texte qui ne sont pas déjà cités
                if (s.isEmpty()) return texte.trim();
                if (!Texte.norm(s).contains(Texte.norm(texte))) return s + ", " + texte.trim();
            }
            return s;
        }
    }

    // ═══ Construction ═══════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public RapportData construire(int n, boolean inclureSoumis) {
        Ctx ctx = new Ctx(n, inclureSoumis);
        Gabarit.Def g = gabarit.actif();
        Map<String, Contenu> contenus = contenus(ctx);
        Map<String, String> n1 = new HashMap<>();
        boolean n1Dispo = campagnes.findByAnnee(n - 1).isPresent();
        if (n1Dispo) contenus(new Ctx(n - 1, false)).forEach((k, v) -> n1.put(k, v.valeur));

        List<Groupe> groupes = new ArrayList<>();
        int gi = 0;
        for (Gabarit.GroupeDef gd : g.groupes()) {
            gi++;
            String num = Gabarit.romain(gi);
            List<Section> sections = new ArrayList<>();
            int si = 0;
            for (Gabarit.SectionDef sd : gd.sections()) {
                si++;
                Contenu c = contenus.get(sd.code());
                if (c == null) continue;
                String numero = gd.sections().size() == 1 ? num : num + "-" + si;
                sections.add(new Section(sd.code(), numero, lib(sd.titre(), ctx), lib(sd.recap(), ctx), sd.colonnes(),
                        c.lignes, c.cin, lib(sd.total(), ctx), c.valeur, n1Dispo ? n1.get(sd.code()) : null, c.total));
            }
            groupes.add(new Groupe(num, gd.titre(), sections));
        }

        StructureIdentite id = identites.findById(1L).orElseGet(StructureIdentite::new);
        Identite identite = new Identite(id.getUniversite(), id.getEtablissement(), id.getDenomination(), id.getCode(),
                chef(id, ctx), chefGrade(id, ctx), id.getFonction(), id.getTelephone(), id.getFax(), id.getEmail(), id.getSiteWeb());
        ResumeRapport r = resumes.findByAnnee(n).orElseGet(ResumeRapport::new);
        Resume resume = new Resume(r.getHistorique(), thematiques(r.getThematiques()), r.getBilan());

        RapportData brouillon = new RapportData(n, g.version(), g.entete(), lib(g.titre(), ctx), dateArrete(ctx), identite, resume,
                groupes, List.of(), Instant.now(), inclureSoumis, n1Dispo);
        return new RapportData(n, g.version(), g.entete(), brouillon.titre(), brouillon.dateArrete(), identite, resume, groupes,
                controles(ctx, brouillon, id, r), brouillon.calculeLe(), inclureSoumis, n1Dispo);
    }

    private record Contenu(List<List<String>> lignes, int cin, String valeur, List<String> total) {
        static Contenu de(List<List<String>> l, int cin) { return new Contenu(l, cin, String.valueOf(l.size()), null); }
    }

    private Map<String, Contenu> contenus(Ctx ctx) {
        int n = ctx.n;
        Map<String, Contenu> m = new HashMap<>();
        List<Chercheur> actifs = membres.findAll().stream().filter(ctx::retenu).filter(c -> c.actifEn(n))
                .sorted(Comparator.comparing((Chercheur c) -> Texte.norm(c.getNom())).thenComparing(c -> Texte.norm(c.getPrenom()))).toList();
        Predicate<Chercheur> ec = c -> c.getCategorie() != Chercheur.Categorie.CADRE_ASSIMILE;
        m.put("CORPS_A", Contenu.de(actifs.stream().filter(ec).filter(c -> "A".equals(ctx.corps.apply(c.getGrade()))).map(this::lignePersonne).toList(), 2));
        m.put("CORPS_B", Contenu.de(actifs.stream().filter(ec).filter(c -> "B".equals(ctx.corps.apply(c.getGrade()))).map(this::lignePersonne).toList(), 2));
        m.put("CADRES", Contenu.de(actifs.stream().filter(ec.negate()).map(this::lignePersonne).toList(), 2));

        m.put("DOCTORANTS", Contenu.de(doctorants.findAll().stream().filter(ctx::retenu).filter(d -> doctorantRetenu(d, ctx))
                .sorted(parNom()).map(d -> List.of(d.getNomComplet(), Texte.nn(d.getCin()), Texte.nn(d.getEtablissement()), Texte.nn(d.getUniversite()))).toList(), 1));
        m.put("ETUDIANTS", Contenu.de(masteriens.findAll().stream().filter(ctx::retenu).filter(e -> etudiantRetenu(e, n))
                .sorted(parNom()).map(e -> List.of(e.getNomComplet(), Texte.nn(e.getCin()), Texte.nn(e.getEtablissement()), Texte.nn(e.getUniversite()))).toList(), 1));

        List<Publication> pubsN = publications.findAll().stream().filter(ctx::retenu).filter(p -> Objects.equals(p.getAnnee(), n))
                .sorted(Comparator.comparing((Publication p) -> p.getAnnee() == null ? 0 : p.getAnnee()).thenComparing(p -> Texte.norm(p.getTitre()))).toList();
        m.put("PUBLICATIONS", Contenu.de(pubsN.stream().filter(p -> Boolean.TRUE.equals(p.getEstImpactee())).map(p -> lignePublication(p, ctx)).toList(), -1));

        Map<Production.Nature, List<Production>> prods = productions.findAll().stream().filter(ctx::retenu)
                .filter(p -> Objects.equals(p.getAnnee(), n))
                .sorted(Comparator.comparing((Production p) -> Texte.norm(p.getTitre())))
                .collect(Collectors.groupingBy(Production::getNature));
        m.put("OUVRAGES", Contenu.de(prods.getOrDefault(Production.Nature.OUVRAGE, List.of()).stream().map(p -> ligneOuvrage(p, ctx)).toList(), -1));
        List<List<String>> chap = new ArrayList<>(prods.getOrDefault(Production.Nature.CHAPITRE, List.of()).stream().map(p -> ligneOuvrage(p, ctx)).toList());
        if (ctx.campagne.isInclureNonImpacteesII3())
            pubsN.stream().filter(p -> !Boolean.TRUE.equals(p.getEstImpactee()))
                    .forEach(p -> chap.add(List.of(p.getTitre(), Texte.nn(p.getAnnee()), Texte.nn(p.getVenue()), libIndexation(p.getIndexation()), auteurs(p, ctx))));
        m.put("CHAPITRES", Contenu.de(chap, -1));
        m.put("BREVETS", Contenu.de(prods.getOrDefault(Production.Nature.BREVET, List.of()).stream()
                .map(p -> List.of(p.getTypeBrevet() == null ? "" : p.getTypeBrevet().name(), Texte.nn(p.getAnnee()), Texte.nn(p.getReference()))).toList(), -1));
        m.put("OBTENTIONS", Contenu.de(prods.getOrDefault(Production.Nature.OBTENTION_VEGETALE, List.of()).stream()
                .map(p -> List.of(Texte.nn(p.getTitre()), Texte.nn(p.getAnnee()), Texte.nn(p.getReference()))).toList(), -1));
        m.put("HABILITATIONS", Contenu.de(habilitations.findAll().stream().filter(ctx::retenu).filter(h -> Objects.equals(h.getAnnee(), n))
                .sorted(Comparator.comparing(h -> Texte.norm(h.getTitre())))
                .map(h -> List.of(Texte.nn(h.getTitre()), ctx.noms(h.getTitulaireId() == null ? List.of() : List.of(h.getTitulaireId()), h.getTitulaireNom()),
                        Texte.nn(h.getAnnee()), ctx.noms(h.getEncadreurId() == null ? List.of() : List.of(h.getEncadreurId()), h.getEncadreurNom()))).toList(), -1));
        m.put("THESES", Contenu.de(theses.findAll().stream().filter(ctx::retenu).filter(t -> Objects.equals(t.getAnneeSoutenance(), n))
                .sorted(Comparator.comparing(t -> Texte.norm(t.getTitre())))
                .map(t -> List.of(Texte.nn(t.getTitre()), Texte.nn(t.getAnneeSoutenance()), Texte.nn(t.getAnneePremiereInscription()),
                        Texte.nn(t.getSujet()), ctx.noms(t.getEncadrantIds(), t.getEncadrantsTexte()))).toList(), -1));
        m.put("MASTERS", Contenu.de(masters.findAll().stream().filter(ctx::retenu).filter(t -> Objects.equals(t.getAnneeSoutenance(), n))
                .sorted(Comparator.comparing((MasterSoutenu t) -> t.getAnneePremiereInscription() == null ? 0 : t.getAnneePremiereInscription())
                        .thenComparing(t -> Texte.norm(t.getSujet())))
                .map(t -> List.of(Texte.nn(t.getAnneeSoutenance()), Texte.nn(t.getAnneePremiereInscription()), Texte.nn(t.getSujet()),
                        ctx.noms(t.getEncadrantIds(), t.getEncadrantsTexte()))).toList(), -1));
        m.put("SEMINAIRES", Contenu.de(evenements.findAll().stream().filter(ctx::retenu).filter(Evenement::isOrganiseParLabo)
                .filter(e -> Objects.equals(e.anneeEffective(), n))
                .sorted(Comparator.comparing((Evenement e) -> e.getDateDebut() == null ? LocalDate.of(n, 1, 1) : e.getDateDebut()))
                .map(e -> List.of(Texte.nn(e.getTitre()), Texte.nn(e.anneeEffective()), Texte.nn(e.getOrganisateurs()), Texte.nn(e.getLieu()))).toList(), -1));

        Map<Convention.Type, String> rub = corr.rubriquesConventions();
        List<Convention> convN = conventions.findAll().stream().filter(ctx::retenu).filter(c -> Objects.equals(c.getAnnee(), n))
                .sorted(Comparator.comparing(c -> Texte.norm(c.getReference()))).toList();
        m.put("CONVENTIONS", Contenu.de(convN.stream().filter(c -> "CONVENTIONS".equals(rub.get(c.getType())))
                .map(c -> List.of(Texte.nn(c.getAnnee()), Texte.nn(c.getReference()), libConvention(c.getType()))).toList(), -1));
        List<List<String>> coop = new ArrayList<>();
        convN.stream().filter(c -> "COOPERATION".equals(rub.get(c.getType())))
                .forEach(c -> coop.add(List.of(Texte.nn(c.getReference()), ctx.noms(c.getResponsableId() == null ? List.of() : List.of(c.getResponsableId()), c.getPartenaire()), "Convention")));
        Campagne.RegleIII3 regle = ctx.campagne.getRegleIII3() == null ? Campagne.RegleIII3.CONVENTIONS_ET_PROJETS : ctx.campagne.getRegleIII3();
        if (regle != Campagne.RegleIII3.CONVENTIONS_SEULEMENT)
            projets.findAll().stream().filter(ctx::retenu).filter(p -> p.enCoursEn(n))
                    .filter(p -> regle == Campagne.RegleIII3.CONVENTIONS_ET_PROJETS || p.getType() == Projet.Type.INTERNATIONAL)
                    .sorted(Comparator.comparing(p -> Texte.norm(p.getIntitule())))
                    .forEach(p -> coop.add(List.of(Texte.nn(p.getIntitule()),
                            ctx.noms(p.getCoordinateurId() == null ? List.of() : List.of(p.getCoordinateurId()), p.getCoordinateurExterne()),
                            "Projet " + libProjet(p.getType()))));
        m.put("COOPERATION", Contenu.de(coop, -1));

        List<LigneBudget> lb = budgets.findByAnneeOrderByOrdre(n);
        List<List<String>> lignes = new ArrayList<>();
        BigDecimal td = BigDecimal.ZERO, te = BigDecimal.ZERO;
        boolean renseigne = false;
        for (LigneBudget l : lb) {
            lignes.add(List.of(l.getRubrique(), montant(l.getCreditsDisponibles()), montant(l.getCreditsEngages()), montant(l.getReliquat())));
            if (l.getCreditsDisponibles() != null) { td = td.add(l.getCreditsDisponibles()); renseigne = true; }
            if (l.getCreditsEngages() != null) { te = te.add(l.getCreditsEngages()); renseigne = true; }
        }
        if (lb.isEmpty()) RUBRIQUES_BUDGET.forEach(r -> lignes.add(List.of(r, "", "", "")));
        String total = renseigne ? montant(td.subtract(te)) : "";
        m.put("BUDGET", new Contenu(lignes, -1, total, List.of("Total", renseigne ? montant(td) : "", renseigne ? montant(te) : "", total)));
        return m;
    }

    private boolean doctorantRetenu(Doctorant d, Ctx ctx) {
        if (d.getAnneePremiereInscription() != null && d.getAnneePremiereInscription() > ctx.n) return false;
        return switch (d.getStatutThese() == null ? Doctorant.Statut.EN_COURS : d.getStatutThese()) {
            case EN_COURS -> true;
            case SOUTENU -> ctx.campagne.isInclureDoctorantsSoutenus() && d.getDateSoutenance() != null && d.getDateSoutenance().getYear() == ctx.n;
            case ABANDON -> false;
        };
    }

    private static boolean etudiantRetenu(Masterien e, int n) {
        if (e.getAnneePremiereInscription() != null && e.getAnneePremiereInscription() > n) return false;
        if (e.getStatutMastere() == Masterien.Statut.DIPLOME)
            return e.getDateSoutenance() == null || e.getDateSoutenance().getYear() >= n;
        return true;
    }

    private static <T extends PersonneRapport> Comparator<T> parNom() {
        return Comparator.comparing((T p) -> Texte.norm(p.getNom())).thenComparing(p -> Texte.norm(p.getPrenom()));
    }

    private List<String> lignePersonne(Chercheur c) {
        return List.of(Texte.nn(c.getGrade()), c.getNomComplet(), Texte.nn(c.getCin()), Texte.nn(c.getEtablissement()), Texte.nn(c.getUniversite()));
    }

    private List<String> lignePublication(Publication p, Ctx ctx) {
        return List.of(p.getTitre(), Texte.nn(p.getVenue()), Texte.nn(p.getAnnee()), libIndexation(p.getIndexation()), auteurs(p, ctx));
    }

    private static String auteurs(Publication p, Ctx ctx) {
        if (!Texte.vide(p.getAuteursTexte())) return p.getAuteursTexte();
        return ctx.noms(p.getChercheurs().stream().map(Chercheur::getId).toList(), null);
    }

    private List<String> ligneOuvrage(Production p, Ctx ctx) {
        return List.of(Texte.nn(p.getTitre()), Texte.nn(p.getAnnee()), Texte.nn(p.getEditeur()), Texte.nn(p.getEdition()),
                Texte.vide(p.getAuteursTexte()) ? ctx.noms(p.getAuteurIds(), null) : p.getAuteursTexte());
    }

    public static String libIndexation(Publication.Indexation i) {
        if (i == null) return "";
        return switch (i) { case WOS -> "WOS"; case SCOPUS -> "Scopus"; case AUTRES -> "Autres"; case AUCUNE -> "Aucune"; };
    }

    private static String libConvention(Convention.Type t) {
        if (t == null) return "";
        return switch (t) {
            case SOCIO_ECONOMIQUE -> "Milieu socio-économique";
            case COOPERATION_INTERNATIONALE -> "Coopération scientifique internationale";
            case AUTRE -> "Autre";
        };
    }

    private static String libProjet(Projet.Type t) {
        if (t == null) return "";
        return switch (t) { case NATIONAL -> "national"; case INTERNATIONAL -> "international"; case CONTRAT -> "contrat"; };
    }

    public static String montant(BigDecimal b) {
        return b == null ? "" : String.format(Locale.FRANCE, "%,.3f", b).replace(' ', ' ').replace(' ', ' ');
    }

    private String lib(String s, Ctx ctx) {
        return s == null ? "" : s.replace("{annee}", String.valueOf(ctx.n)).replace("{arrete}", dateArrete(ctx));
    }

    private static String dateArrete(Ctx ctx) {
        LocalDate d = ctx.campagne.getDateArreteBudget() != null ? ctx.campagne.getDateArreteBudget() : LocalDate.of(ctx.n, 10, 31);
        return d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE));
    }

    private String chef(StructureIdentite id, Ctx ctx) {
        Chercheur c = id.getChefId() == null ? null : ctx.parId.get(id.getChefId());
        return c != null ? c.getNomComplet() : id.getChefNom();
    }

    private String chefGrade(StructureIdentite id, Ctx ctx) {
        Chercheur c = id.getChefId() == null ? null : ctx.parId.get(id.getChefId());
        return c != null && !Texte.vide(c.getGrade()) ? c.getGrade() : id.getChefGrade();
    }

    /** Thématiques restituées sous forme de liste (§14 F2). */
    public static List<String> thematiques(String texte) {
        if (Texte.vide(texte)) return List.of();
        String[] parts = texte.split("\\s+-\\s+|;|\\n");
        List<String> r = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim().replaceAll("[.;]+$", "").trim();
            if (!t.isEmpty()) r.add(t);
        }
        return r;
    }

    /** Proposition automatique du bilan (§14 F2) à partir des chiffres calculés. */
    @Transactional(readOnly = true)
    public String propositionBilan(int n) {
        RapportData d = construire(n, false);
        String t = "En " + n + ", le laboratoire LIMTIC a vu la soutenance de " + v(d, "THESES") + " thèses et "
                + v(d, "MASTERS") + " mastères de recherche. Sa production scientifique compte " + v(d, "PUBLICATIONS")
                + " publications impactées et " + v(d, "CHAPITRES") + " articles ou chapitres d'ouvrage. L'équipe réunit "
                + v(d, "CORPS_A") + " enseignants-chercheurs du corps A, " + v(d, "CORPS_B") + " du corps B, "
                + v(d, "DOCTORANTS") + " doctorants et " + v(d, "CADRES") + " cadres assimilés.";
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    /** Thématiques proposées à partir des axes de recherche (domaines) actifs. */
    public String propositionThematiques() {
        List<String> axes = domaines.findAll().stream().map(Domaine::getNom).sorted().toList();
        if (axes.isEmpty()) return "";
        return "Les travaux de recherche du laboratoire LIMTIC couvrent " + axes.size() + " axes de recherche : - "
                + String.join(" ; - ", axes) + ".";
    }

    private static String v(RapportData d, String code) {
        Section s = d.section(code);
        return s == null ? "0" : s.valeur();
    }

    // ═══ Contrôles (§15.3) ══════════════════════════════════════════════════

    private static final Pattern NB_BILAN = Pattern.compile("(\\d+)\\s+(?:[\\p{L}'’-]+\\s+){0,2}?(th[eè]ses?|masters?|mast[eè]res?|publications?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private List<Controle> controles(Ctx ctx, RapportData d, StructureIdentite id, ResumeRapport r) {
        int n = ctx.n;
        List<Controle> c = new ArrayList<>();
        Campagne camp = ctx.campagne;

        // F1 – identité complète
        List<String> f1 = new ArrayList<>();
        FormRegistry.req(f1, id.getUniversite(), "université");
        FormRegistry.req(f1, id.getEtablissement(), "établissement");
        FormRegistry.req(f1, id.getDenomination(), "dénomination");
        FormRegistry.req(f1, d.identite().chef(), "chef du LR/UR");
        FormRegistry.req(f1, id.getSiteWeb(), "site web");
        if (Texte.vide(id.getCode())) f1.add("code de la structure");
        else if (!id.getCode().trim().matches("(?i)(LR|UR)\\d{2}[A-Z]{2,4}\\d{2,3}")) f1.add("code de la structure (format LRnnESnn)");
        if (Texte.vide(id.getEmail())) f1.add("e-mail");
        else if (!id.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) f1.add("e-mail valide");
        if (!f1.isEmpty()) c.add(bloq("F1_INCOMPLET", "Identité de la structure (F1) incomplète.", "identite", f1));

        // F2 – résumé
        List<String> f2 = new ArrayList<>();
        if (Texte.vide(r.getHistorique())) f2.add("historique manquant");
        else if (r.getHistorique().length() > 500) f2.add("historique : " + r.getHistorique().length() + " caractères (500 max.)");
        if (Texte.vide(r.getThematiques())) f2.add("thématiques manquantes");
        if (Texte.vide(r.getBilan())) f2.add("bilan manquant");
        else if (r.getBilan().length() > 500) f2.add("bilan : " + r.getBilan().length() + " caractères (500 max.)");
        if (!f2.isEmpty()) c.add(bloq("F2_INVALIDE", "Résumé des activités (F2) incomplet ou trop long.", "resume", f2));

        // Personnes retenues : champs du rapport, CIN
        List<Chercheur> actifs = membres.findAll().stream().filter(ctx::retenu).filter(m -> m.actifEn(n)).toList();
        List<String> pers = new ArrayList<>(), cinInv = new ArrayList<>(), sansCorps = new ArrayList<>();
        for (Chercheur m : actifs) {
            verifPersonne(m, "Membre", pers, cinInv);
            if (Texte.vide(m.getGrade())) pers.add("Membre " + m.getNomComplet() + " : grade");
            else if (m.getCategorie() != Chercheur.Categorie.CADRE_ASSIMILE && ctx.corps.apply(m.getGrade()) == null)
                sansCorps.add(m.getNomComplet() + " (" + m.getGrade() + ")");
        }
        doctorants.findAll().stream().filter(ctx::retenu).filter(x -> doctorantRetenu(x, ctx)).forEach(x -> verifPersonne(x, "Doctorant", pers, cinInv));
        masteriens.findAll().stream().filter(ctx::retenu).filter(x -> etudiantRetenu(x, n)).forEach(x -> verifPersonne(x, "Étudiant", pers, cinInv));
        if (!pers.isEmpty()) c.add(bloq("CHAMP_MANQUANT_PERSONNE", "Champs obligatoires manquants dans des fiches validées (personnes).", "membres", pers));
        if (!cinInv.isEmpty()) c.add(bloq("CIN_INVALIDE", "Numéro de CIN manquant ou invalide (8 chiffres attendus).", "membres", cinInv));
        if (!sansCorps.isEmpty()) c.add(bloq("GRADE_SANS_CORPS", "Grade sans correspondance de corps A/B (table de correspondance).", "parametres", sansCorps));
        List<String> doublonsCin = new ArrayList<>();
        doublons(membres.findAll(), doublonsCin);
        doublons(doctorants.findAll(), doublonsCin);
        doublons(masteriens.findAll(), doublonsCin);
        if (!doublonsCin.isEmpty()) c.add(bloq("CIN_DOUBLE", "Numéro de CIN en double.", "membres", doublonsCin));

        // Publications et productions de l'année
        List<Publication> pubs = publications.findAll().stream().filter(ctx::retenu).filter(p -> Objects.equals(p.getAnnee(), n)).toList();
        List<String> pubManq = new ArrayList<>(), sansInterne = new ArrayList<>(), ecart = new ArrayList<>();
        for (Publication p : pubs) {
            List<String> e = new ArrayList<>();
            FormRegistry.req(e, p.getVenue(), "journal/conférence");
            FormRegistry.req(e, p.getIndexation(), "indexation");
            FormRegistry.req(e, p.getAuteursTexte(), "auteurs");
            if (!e.isEmpty()) pubManq.add(court(p.getTitre()) + " : " + String.join(", ", e));
            if (p.getChercheurs().isEmpty()) sansInterne.add(court(p.getTitre()));
            if (Boolean.TRUE.equals(p.getEstImpactee()) && p.getIndexation() != null && !corr.impactee(p.getIndexation()))
                ecart.add(court(p.getTitre()) + " (" + libIndexation(p.getIndexation()) + ")");
        }
        publications.findAll().stream().filter(ctx::retenu).filter(p -> p.getAnnee() == null)
                .forEach(p -> pubManq.add(court(p.getTitre()) + " : année"));
        if (!pubManq.isEmpty()) c.add(bloq("PUBLICATION_INCOMPLETE", "Publications validées incomplètes (année, indexation, auteurs…).", "publications", pubManq));
        if (!sansInterne.isEmpty()) c.add(bloq("PUBLICATION_SANS_AUTEUR_INTERNE", "Publications sans auteur interne au laboratoire.", "publications", sansInterne));
        if (!ecart.isEmpty()) c.add(avert("IMPACTEE_HORS_TABLE",
                "Publications comptées comme impactées alors que leur indexation ne l'est pas selon la table de correspondance (écart à expliquer).",
                "publications", ecart));

        List<String> prodManq = new ArrayList<>();
        productions.findAll().stream().filter(ctx::retenu).filter(p -> Objects.equals(p.getAnnee(), n)).forEach(p -> {
            List<String> e = new ArrayList<>();
            FormRegistry.req(e, p.getTitre(), "titre");
            if (p.getNature() == Production.Nature.OUVRAGE || p.getNature() == Production.Nature.CHAPITRE) {
                FormRegistry.req(e, p.getEditeur(), "éditeur");
                FormRegistry.req(e, p.getAuteursTexte(), "auteurs");
                if (p.getAuteurIds().isEmpty()) e.add("auteur interne");
            } else FormRegistry.req(e, p.getReference(), "référence");
            if (!e.isEmpty()) prodManq.add(court(p.getTitre()) + " : " + String.join(", ", e));
        });
        theses.findAll().stream().filter(ctx::retenu).filter(t -> Objects.equals(t.getAnneeSoutenance(), n)).forEach(t -> {
            List<String> e = new ArrayList<>();
            FormRegistry.req(e, t.getTitre(), "titre"); FormRegistry.req(e, t.getSujet(), "sujet");
            FormRegistry.req(e, t.getAnneePremiereInscription(), "année de 1re inscription");
            if (t.getEncadrantIds().isEmpty() && Texte.vide(t.getEncadrantsTexte())) e.add("encadrant");
            if (!e.isEmpty()) prodManq.add("Thèse « " + court(Texte.nn(t.getTitre())) + " » : " + String.join(", ", e));
        });
        masters.findAll().stream().filter(ctx::retenu).filter(t -> Objects.equals(t.getAnneeSoutenance(), n)).forEach(t -> {
            List<String> e = new ArrayList<>();
            FormRegistry.req(e, t.getSujet(), "sujet"); FormRegistry.req(e, t.getAnneePremiereInscription(), "année de 1re inscription");
            if (t.getEncadrantIds().isEmpty() && Texte.vide(t.getEncadrantsTexte())) e.add("encadrant");
            if (!e.isEmpty()) prodManq.add("Mastère « " + court(Texte.nn(t.getSujet())) + " » : " + String.join(", ", e));
        });
        if (!prodManq.isEmpty()) c.add(bloq("PRODUCTION_INCOMPLETE", "Productions, thèses ou mastères validés incomplets.", "theses", prodManq));

        // Enregistrements non validés (bloquant paramétrable)
        List<String> nonValides = nonValides(n);
        if (!nonValides.isEmpty()) {
            String msg = nonValides.size() + " enregistrement(s) encore « soumis » ou « brouillon » pour " + n + ".";
            c.add(camp.isBloquerNonValides() ? bloq("NON_VALIDES", msg, "completude", nonValides) : avert("NON_VALIDES", msg, "completude", nonValides));
        }

        // ── Avertissements ──
        if (!Texte.vide(r.getBilan())) {
            List<String> contrad = new ArrayList<>();
            Matcher mt = NB_BILAN.matcher(r.getBilan());
            while (mt.find()) {
                String mot = Texte.norm(mt.group(2));
                String code = mot.startsWith("th") ? "THESES" : mot.startsWith("pub") ? "PUBLICATIONS" : "MASTERS";
                String calc = v(d, code);
                if (!mt.group(1).equals(calc))
                    contrad.add("Le bilan cite « " + mt.group(1) + " " + mt.group(2) + " », le récapitulatif calcule " + calc + ".");
            }
            if (!contrad.isEmpty()) c.add(avert("BILAN_CONTRADICTOIRE", "Le bilan textuel contredit le récapitulatif calculé.", "resume", contrad));
        }
        Map<String, List<Publication>> parCle = new HashMap<>();
        for (Publication p : pubs) {
            if (!Texte.vide(p.getDoi())) parCle.computeIfAbsent("doi:" + p.getDoi().trim().toLowerCase(), k -> new ArrayList<>()).add(p);
            parCle.computeIfAbsent("titre:" + Texte.norm(p.getTitre()), k -> new ArrayList<>()).add(p);
        }
        List<String> dbl = parCle.values().stream().filter(l -> l.size() > 1).map(l -> court(l.get(0).getTitre()) + " (" + l.size() + " fois)").distinct().toList();
        if (!dbl.isEmpty()) c.add(avert("PUBLICATION_DOUBLON", "Doublon probable de publication (DOI ou titre identique).", "publications", dbl));
        List<String> auteursRepetes = new ArrayList<>();
        for (Publication p : pubs) {
            if (Texte.vide(p.getAuteursTexte())) continue;
            String t = " " + Texte.norm(p.getAuteursTexte()) + " ";
            boolean repete = false;
            // Auteur interne cité plusieurs fois (ex. « BEN HASSINE Faten … BEN HASSINE Faten »)
            for (Chercheur m : p.getChercheurs()) {
                String nom = " " + Texte.norm(m.getNom()) + " ", prenom = " " + Texte.norm(m.getPrenom()).split(" ")[0] + " ";
                if (occurrences(t, nom) > 1 && occurrences(t, prenom) > 1) repete = true;
            }
            // Liste séparée par des virgules : doublon exact
            if (p.getAuteursTexte().matches(".*[,;].*")) {
                List<String> noms = Arrays.stream(p.getAuteursTexte().split("[,;]| and | et ")).map(Texte::norm).filter(x -> !x.isEmpty()).toList();
                if (new HashSet<>(noms).size() < noms.size()) repete = true;
            }
            if (repete) auteursRepetes.add(court(p.getTitre()));
        }
        if (!auteursRepetes.isEmpty()) c.add(avert("AUTEUR_REPETE", "Un même auteur est répété dans la liste des auteurs.", "publications", auteursRepetes));

        List<LigneBudget> lb = budgets.findByAnneeOrderByOrdre(n);
        if (lb.stream().allMatch(l -> l.getCreditsDisponibles() == null && l.getCreditsEngages() == null))
            c.add(avert("BUDGET_VIDE", "Budget (F18) non renseigné.", "budget", List.of()));
        List<String> depass = lb.stream().filter(l -> l.getCreditsDisponibles() != null && l.getCreditsEngages() != null
                && l.getCreditsEngages().compareTo(l.getCreditsDisponibles()) > 0).map(LigneBudget::getRubrique).toList();
        if (!depass.isEmpty()) c.add(avert("BUDGET_DEPASSEMENT", "Crédits engagés supérieurs aux crédits disponibles.", "budget", depass));

        Set<Long> docAvecThese = theses.findAll().stream().map(These::getDoctorantId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> soutSansThese = doctorants.findAll().stream().filter(x -> x.getStatutThese() == Doctorant.Statut.SOUTENU && !docAvecThese.contains(x.getId()))
                .map(PersonneRapport::getNomComplet).toList();
        if (!soutSansThese.isEmpty()) c.add(avert("DOCTORANT_SANS_THESE", "Doctorant soutenu sans fiche thèse liée.", "doctorants", soutSansThese));
        List<These> thN = theses.findAll().stream().filter(ctx::retenu).filter(t -> Objects.equals(t.getAnneeSoutenance(), n)).toList();
        List<String> sansEnc = thN.stream().filter(t -> t.getEncadrantIds().isEmpty()).map(t -> court(Texte.nn(t.getTitre()))).toList();
        if (!sansEnc.isEmpty()) c.add(avert("THESE_SANS_ENCADRANT_MEMBRE", "Thèse sans encadrant membre du laboratoire.", "theses", sansEnc));
        List<String> sansDoc = thN.stream().filter(t -> t.getDoctorantId() == null).map(t -> court(Texte.nn(t.getTitre()))).toList();
        if (!sansDoc.isEmpty()) c.add(avert("THESE_SANS_DOCTORANT", "Thèse non reliée à une fiche doctorant (F4).", "theses", sansDoc));
        List<String> docIncomplets = doctorants.findAll().stream().filter(ctx::retenu).filter(x -> doctorantRetenu(x, ctx))
                .filter(x -> Texte.vide(x.getSujet()) || (x.getDirecteurIds().isEmpty() && Texte.vide(x.getDirecteursTexte())) || x.getAnneePremiereInscription() == null)
                .map(PersonneRapport::getNomComplet).toList();
        if (!docIncomplets.isEmpty()) c.add(avert("DOCTORANT_INCOMPLET",
                "Fiches doctorants sans sujet, directeur ou année de 1re inscription (non restitués dans le rapport, mais requis par F4).",
                "doctorants", docIncomplets));
        return c;
    }

    private List<String> nonValides(int n) {
        List<String> l = new ArrayList<>();
        Predicate<FicheRapport> nv = f -> f.getStatut() == StatutFiche.BROUILLON || f.getStatut() == StatutFiche.SOUMIS;
        publications.findAll().stream().filter(nv).filter(p -> Objects.equals(p.getAnnee(), n)).forEach(p -> l.add("Publication : " + court(p.getTitre()) + " (" + p.getStatut() + ")"));
        productions.findAll().stream().filter(nv).filter(p -> Objects.equals(p.getAnnee(), n)).forEach(p -> l.add(p.getNature() + " : " + court(p.getTitre()) + " (" + p.getStatut() + ")"));
        habilitations.findAll().stream().filter(nv).filter(p -> Objects.equals(p.getAnnee(), n)).forEach(p -> l.add("Habilitation : " + court(p.getTitre()) + " (" + p.getStatut() + ")"));
        theses.findAll().stream().filter(nv).filter(p -> Objects.equals(p.getAnneeSoutenance(), n)).forEach(p -> l.add("Thèse : " + court(Texte.nn(p.getTitre())) + " (" + p.getStatut() + ")"));
        masters.findAll().stream().filter(nv).filter(p -> Objects.equals(p.getAnneeSoutenance(), n)).forEach(p -> l.add("Mastère : " + court(Texte.nn(p.getSujet())) + " (" + p.getStatut() + ")"));
        evenements.findAll().stream().filter(nv).filter(p -> Objects.equals(p.anneeEffective(), n)).forEach(p -> l.add("Événement : " + court(p.getTitre()) + " (" + p.getStatut() + ")"));
        conventions.findAll().stream().filter(nv).filter(p -> Objects.equals(p.getAnnee(), n)).forEach(p -> l.add("Convention : " + court(p.getReference()) + " (" + p.getStatut() + ")"));
        projets.findAll().stream().filter(nv).filter(p -> p.enCoursEn(n)).forEach(p -> l.add("Projet : " + court(p.getIntitule()) + " (" + p.getStatut() + ")"));
        membres.findAll().stream().filter(nv).filter(p -> p.actifEn(n)).forEach(p -> l.add("Membre : " + p.getNomComplet() + " (" + p.getStatut() + ")"));
        doctorants.findAll().stream().filter(nv).forEach(p -> l.add("Doctorant : " + p.getNomComplet() + " (" + p.getStatut() + ")"));
        masteriens.findAll().stream().filter(nv).forEach(p -> l.add("Étudiant : " + p.getNomComplet() + " (" + p.getStatut() + ")"));
        return l;
    }

    private static void verifPersonne(PersonneRapport p, String type, List<String> manq, List<String> cinInv) {
        List<String> e = new ArrayList<>();
        FormRegistry.req(e, p.getEtablissement(), "établissement");
        FormRegistry.req(e, p.getUniversite(), "université");
        if (!e.isEmpty()) manq.add(type + " " + p.getNomComplet() + " : " + String.join(", ", e));
        if (!p.isEtranger() && (Texte.vide(p.getCin()) || !p.getCin().matches("\\d{8}")))
            cinInv.add(type + " " + p.getNomComplet() + (Texte.vide(p.getCin()) ? " : CIN manquant" : " : CIN invalide"));
    }

    private static void doublons(List<? extends PersonneRapport> l, List<String> sortie) {
        Map<String, List<PersonneRapport>> m = new HashMap<>();
        l.stream().filter(p -> p.getCinEmpreinte() != null).forEach(p -> m.computeIfAbsent(p.getCinEmpreinte(), k -> new ArrayList<>()).add(p));
        m.values().stream().filter(x -> x.size() > 1)
                .forEach(x -> sortie.add(x.stream().map(PersonneRapport::getNomComplet).collect(Collectors.joining(" / "))));
    }

    private static int occurrences(String texte, String motif) {
        int n = 0, i = 0;
        while ((i = texte.indexOf(motif, i)) >= 0) { n++; i += motif.length() - 1; }
        return n;
    }

    private static String court(String s) {
        if (s == null) return "";
        return s.length() > 90 ? s.substring(0, 87) + "…" : s;
    }

    private static Controle bloq(String code, String msg, String cat, List<String> el) { return new Controle("BLOQUANT", code, msg, cat, el); }
    private static Controle avert(String code, String msg, String cat, List<String> el) { return new Controle("AVERTISSEMENT", code, msg, cat, el); }
}
