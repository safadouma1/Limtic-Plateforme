package com.projetweb.webapp.rapport.service;

import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.FormRegistry.Def;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Cycle de vie d'une campagne annuelle (§16) : ouverture, revue, clôture avec verrouillage et copie immuable, réouverture. */
@Service
public class CampagneService {

    public record Creation(Integer annee, LocalDate dateDebutSaisie, LocalDate dateEcheance, LocalDate dateArreteBudget) {}

    private final CampagneRepository campagnes;
    private final LigneBudgetRepository budgets;
    private final ResumeRapportRepository resumes;
    private final RapportService rapports;
    private final GenerationService generation;
    private final FormRegistry registry;
    private final Acces acces;
    private final AuditService audit;

    public CampagneService(CampagneRepository campagnes, LigneBudgetRepository budgets, ResumeRapportRepository resumes,
                           RapportService rapports, GenerationService generation, FormRegistry registry, Acces acces, AuditService audit) {
        this.campagnes = campagnes; this.budgets = budgets; this.resumes = resumes; this.rapports = rapports;
        this.generation = generation; this.registry = registry; this.acces = acces; this.audit = audit;
    }

    @Transactional
    public Campagne creer(Creation req) {
        Utilisateur u = acces.directeur();
        Campagne c = creerSansControle(req);
        audit.log(u.getEmail(), "CAMPAGNE_CREATION", "Campagne " + c.getAnnee());
        return c;
    }

    /** Utilisé aussi par l'import du jeu d'essai. */
    @Transactional
    public Campagne creerSansControle(Creation req) {
        if (req.annee() == null || req.annee() < 2000 || req.annee() > 2100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Année de référence invalide.");
        if (campagnes.findByAnnee(req.annee()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une campagne existe déjà pour " + req.annee() + ".");
        Campagne c = new Campagne();
        c.setAnnee(req.annee());
        c.setDateDebutSaisie(req.dateDebutSaisie());
        c.setDateEcheance(req.dateEcheance());
        c.setDateArreteBudget(req.dateArreteBudget() != null ? req.dateArreteBudget() : LocalDate.of(req.annee(), 10, 31));
        c = campagnes.save(c);
        // F18 : les 10 rubriques fixes
        if (budgets.findByAnneeOrderByOrdre(req.annee()).isEmpty()) {
            int i = 0;
            for (String r : RapportService.RUBRIQUES_BUDGET) {
                LigneBudget l = new LigneBudget();
                l.setAnnee(req.annee());
                l.setOrdre(i++);
                l.setRubrique(r);
                budgets.save(l);
            }
        }
        // F2 : texte reconduit de l'année précédente, thématiques depuis les axes
        if (resumes.findByAnnee(req.annee()).isEmpty()) {
            ResumeRapport r = new ResumeRapport();
            r.setAnnee(req.annee());
            resumes.findAll().stream().filter(x -> x.getAnnee() < req.annee()).max(Comparator.comparing(ResumeRapport::getAnnee))
                    .ifPresent(prev -> { r.setHistorique(prev.getHistorique()); r.setThematiques(prev.getThematiques()); });
            if (Texte.vide(r.getThematiques())) r.setThematiques(rapports.propositionThematiques());
            resumes.save(r);
        }
        return c;
    }

    @Transactional
    public Campagne parametres(int annee, Campagne p) {
        Utilisateur u = acces.directeur();
        Campagne c = trouver(annee);
        if (c.getStatut() == Campagne.Statut.CLOTUREE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campagne clôturée : rouvrez-la pour modifier ses paramètres.");
        c.setDateDebutSaisie(p.getDateDebutSaisie());
        c.setDateEcheance(p.getDateEcheance());
        if (p.getDateArreteBudget() != null) c.setDateArreteBudget(p.getDateArreteBudget());
        c.setInclureDoctorantsSoutenus(p.isInclureDoctorantsSoutenus());
        c.setBloquerNonValides(p.isBloquerNonValides());
        c.setInclureNonImpacteesII3(p.isInclureNonImpacteesII3());
        if (p.getRegleIII3() != null) c.setRegleIII3(p.getRegleIII3());
        audit.log(u.getEmail(), "CAMPAGNE_PARAMETRES", "Campagne " + annee);
        return campagnes.save(c);
    }

    @Transactional
    public Campagne changerStatut(int annee, Campagne.Statut cible) {
        Utilisateur u = acces.directeur();
        Campagne c = trouver(annee);
        Campagne.Statut actuel = c.getStatut();
        if (actuel == cible) return c;
        if (cible == Campagne.Statut.CLOTUREE) return cloturer(c, u);
        if (actuel == Campagne.Statut.CLOTUREE) return rouvrir(c, cible, u);
        if (cible == Campagne.Statut.PREPARATION && actuel != Campagne.Statut.SAISIE_OUVERTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition impossible.");
        c.setStatut(cible);
        audit.log(u.getEmail(), "CAMPAGNE_STATUT", "Campagne " + annee + " : " + actuel + " → " + cible);
        return campagnes.save(c);
    }

    private Campagne cloturer(Campagne c, Utilisateur u) {
        RapportData d = rapports.construire(c.getAnnee(), false);
        if (d.nbBloquants() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Clôture refusée : " + d.nbBloquants()
                    + " contrôle(s) bloquant(s) à lever (" + d.controles().stream().filter(RapportData.Controle::bloquant)
                    .map(RapportData.Controle::message).collect(Collectors.joining(" ; ")) + ").");
        int verrouilles = verrouiller(c.getAnnee(), true);
        c.setSnapshot(generation.scellerSnapshot(d));
        c.setStatut(Campagne.Statut.CLOTUREE);
        c.setClotureLe(Instant.now());
        c.setCloturePar(u.getEmail());
        c.setNbClotures(c.getNbClotures() + 1);
        audit.log(u.getEmail(), "CAMPAGNE_CLOTURE", "Campagne " + c.getAnnee() + " clôturée (" + verrouilles
                + " enregistrements verrouillés, copie immuable n°" + c.getNbClotures() + ")");
        return campagnes.save(c);
    }

    private Campagne rouvrir(Campagne c, Campagne.Statut cible, Utilisateur u) {
        int n = verrouiller(c.getAnnee(), false);
        c.setStatut(cible);
        c.setSnapshot(null);
        audit.log(u.getEmail(), "CAMPAGNE_REOUVERTURE", "Campagne " + c.getAnnee() + " rouverte (" + n + " enregistrements déverrouillés)");
        return campagnes.save(c);
    }

    /** Verrouille (ou déverrouille) les enregistrements validés rattachés à l'année. Les fiches de personnes restent modifiables. */
    private int verrouiller(int annee, boolean verrou) {
        int n = 0;
        for (Def<? extends FicheRapport> def0 : registry.toutes()) {
            @SuppressWarnings("unchecked") Def<FicheRapport> def = (Def<FicheRapport>) def0;
            for (FicheRapport f : def.tous().get()) {
                Integer a = def.annee().apply(f);
                if (a == null || a != annee) continue;
                if (verrou && f.getStatut() == StatutFiche.VALIDE) { f.setStatut(StatutFiche.VERROUILLE); def.repo().save(f); n++; }
                else if (!verrou && f.getStatut() == StatutFiche.VERROUILLE) { f.setStatut(StatutFiche.VALIDE); def.repo().save(f); n++; }
            }
        }
        return n;
    }

    public Campagne trouver(int annee) {
        return campagnes.findByAnnee(annee).orElseThrow(() -> new NoSuchElementException("Aucune campagne pour " + annee));
    }

    /** Tableau de bord de complétude (RA-19) : par formulaire et par déclarant. */
    @Transactional(readOnly = true)
    public Map<String, Object> completude(int annee) {
        acces.admin();
        List<Map<String, Object>> parForm = new ArrayList<>();
        Map<String, Map<String, Integer>> parDeclarant = new TreeMap<>();
        for (Def<? extends FicheRapport> def0 : registry.toutes()) {
            @SuppressWarnings("unchecked") Def<FicheRapport> def = (Def<FicheRapport>) def0;
            Map<String, Integer> compte = new LinkedHashMap<>();
            for (StatutFiche s : StatutFiche.values()) compte.put(s.name(), 0);
            for (FicheRapport f : def.tous().get()) {
                Integer a = def.annee().apply(f);
                if (a != null && a != annee) continue;
                compte.merge(f.getStatut().name(), 1, Integer::sum);
                String qui = f.getCreePar() == null ? "import" : f.getCreePar();
                parDeclarant.computeIfAbsent(qui, k -> new LinkedHashMap<>()).merge(f.getStatut().name(), 1, Integer::sum);
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("code", def.code());
            l.put("formulaire", def.formulaire());
            l.put("libelle", def.libelle());
            l.put("statuts", compte);
            parForm.add(l);
        }
        return Map.of("formulaires", parForm, "declarants", parDeclarant);
    }
}
