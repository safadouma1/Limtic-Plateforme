package com.projetweb.webapp.rapport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.FormRegistry.Def;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.DomaineRepository;
import com.projetweb.webapp.securite.CinCrypto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.Year;
import java.util.*;

/**
 * Saisie des formulaires et workflow brouillon → soumis → validé → verrouillé (§16.2).
 * - Le chercheur saisit ses propres enregistrements, uniquement pendant une campagne ouverte.
 * - L'administrateur crée, modifie, valide ou rejette (motif obligatoire).
 * - Un enregistrement verrouillé (campagne clôturée) n'est plus modifiable.
 */
@Service
public class FormService {

    public record Resultat(Object fiche, String message) {}

    private final FormRegistry registry;
    private final ObjectMapper mapper;
    private final Acces acces;
    private final AuditService audit;
    private final CampagneRepository campagnes;
    private final ChercheurRepository membres;
    private final DomaineRepository domaines;
    private final TheseRepository theses;
    private final MasterSoutenuRepository masters;
    private final CinCrypto crypto;
    private final Correspondances correspondances;
    private final com.projetweb.webapp.repository.PublicationRepository publications;

    public FormService(FormRegistry registry, ObjectMapper mapper, Acces acces, AuditService audit,
                       CampagneRepository campagnes, ChercheurRepository membres, DomaineRepository domaines,
                       TheseRepository theses, MasterSoutenuRepository masters, CinCrypto crypto,
                       com.projetweb.webapp.repository.PublicationRepository publications, Correspondances correspondances) {
        this.correspondances = correspondances;
        this.publications = publications;
        this.registry = registry; this.mapper = mapper; this.acces = acces; this.audit = audit;
        this.campagnes = campagnes; this.membres = membres; this.domaines = domaines;
        this.theses = theses; this.masters = masters; this.crypto = crypto;
    }

    // ── Lecture ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FicheRapport> lister(String code, Integer annee, StatutFiche statut) {
        Utilisateur u = acces.module();
        Def<FicheRapport> d = registry.get(code);
        return d.tous().get().stream()
                .filter(f -> annee == null || d.annee().apply(f) == null || annee.equals(d.annee().apply(f)))
                .filter(f -> statut == null || f.getStatut() == statut)
                .filter(f -> Acces.admin(u) || visiblePar(d, f, u))
                .sorted(Comparator.comparing(f -> Texte.norm(Texte.nn(d.titre().apply(f)))))
                .toList();
    }

    @Transactional(readOnly = true)
    public FicheRapport lire(String code, Long id) {
        Utilisateur u = acces.module();
        Def<FicheRapport> d = registry.get(code);
        FicheRapport f = trouver(d, id);
        if (!Acces.admin(u) && !visiblePar(d, f, u)) Acces.refuser("Cet enregistrement ne vous concerne pas.");
        return f;
    }

    /** Consultation d'un CIN : administrateurs uniquement, journalisée. */
    @Transactional(readOnly = true)
    public String cin(String code, Long id) {
        Utilisateur u = acces.admin();
        FicheRapport f = trouver(registry.get(code), id);
        if (!(f instanceof PersonneRapport p)) throw new NoSuchElementException("Pas de CIN pour ce formulaire");
        audit.log(u.getEmail(), "CIN_CONSULTATION", code + " #" + id + " (" + p.getNomComplet() + ")");
        return p.getCin();
    }

    // ── Écriture ─────────────────────────────────────────────────

    @Transactional
    public Resultat creer(String code, JsonNode corps) {
        Utilisateur u = acces.module();
        Def<FicheRapport> d = registry.get(code);
        if (Acces.chercheur(u) && !d.saisieChercheur()) Acces.refuser("Ce formulaire est saisi par l'administrateur.");
        FicheRapport f;
        try {
            f = d.type().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        d.init().accept(f);
        appliquer(d, f, corps);
        if (f.getAnneeReference() == null) f.setAnneeReference(anneeParDefaut(d, f));
        verifierCampagne(d, f, u);
        f.setCreePar(u.getEmail());
        f.setCreeParId(u.getId());
        f.setCreeLe(Instant.now());
        String message;
        if (Acces.admin(u)) {
            // Saisie par l'administrateur : validée d'emblée si elle est complète.
            List<String> manquants = d.obligatoires().apply(f);
            if (manquants.isEmpty()) { marquerValide(f, u); message = "Enregistrement créé et validé."; }
            else { f.setStatut(StatutFiche.BROUILLON); message = "Enregistré en brouillon : champs à compléter (" + String.join(", ", manquants) + ")."; }
        } else {
            f.setStatut(StatutFiche.BROUILLON);
            message = "Brouillon enregistré. Soumettez-le pour validation quand il est complet.";
        }
        verifierCinUnique(d, f);
        f = d.repo().save(f);
        String suite = suite(f, u);
        return new Resultat(f, suite == null ? message : message + " " + suite);
    }

    @Transactional
    public Resultat modifier(String code, Long id, JsonNode corps) {
        Utilisateur u = acces.module();
        Def<FicheRapport> d = registry.get(code);
        FicheRapport f = trouver(d, id);
        verifierModifiable(d, f, u);
        appliquer(d, f, corps);
        verifierCampagne(d, f, u);
        if (Acces.chercheur(u) && f.getStatut() != StatutFiche.BROUILLON) f.setStatut(StatutFiche.BROUILLON);
        verifierCinUnique(d, f);
        f = d.repo().save(f);
        String suite = suite(f, u);
        return new Resultat(f, suite == null ? "Modifications enregistrées." : "Modifications enregistrées. " + suite);
    }

    @Transactional
    public void supprimer(String code, Long id) {
        Utilisateur u = acces.module();
        Def<FicheRapport> d = registry.get(code);
        FicheRapport f = trouver(d, id);
        verifierModifiable(d, f, u);
        if (f instanceof Chercheur c) {
            // Détache le membre de ses publications avant suppression
            publications.findByChercheur(c.getId()).forEach(p -> p.getChercheurs().removeIf(x -> x.getId().equals(c.getId())));
        }
        d.repo().delete(f);
        audit.log(u.getEmail(), "SUPPRESSION", code + " #" + id + " « " + d.titre().apply(f) + " »");
    }

    @Transactional
    public Resultat soumettre(String code, Long id) {
        Utilisateur u = acces.module();
        Def<FicheRapport> d = registry.get(code);
        FicheRapport f = trouver(d, id);
        if (f.getStatut() != StatutFiche.BROUILLON) throw conflit("Seul un brouillon peut être soumis.");
        if (Acces.chercheur(u) && !Objects.equals(f.getCreeParId(), u.getId())) Acces.refuser("Vous ne pouvez soumettre que vos propres enregistrements.");
        verifierCampagne(d, f, u);
        exigerComplet(d, f);
        f.setStatut(StatutFiche.SOUMIS);
        f.setMotifRejet(null);
        return new Resultat(d.repo().save(f), "Enregistrement soumis pour validation.");
    }

    @Transactional
    public Resultat valider(String code, Long id) {
        Utilisateur u = acces.admin();
        Def<FicheRapport> d = registry.get(code);
        FicheRapport f = trouver(d, id);
        if (f.getStatut() == StatutFiche.VERROUILLE) throw conflit("Enregistrement verrouillé (campagne clôturée).");
        exigerComplet(d, f);
        marquerValide(f, u);
        audit.log(u.getEmail(), "VALIDATION", code + " #" + id);
        return new Resultat(d.repo().save(f), "Enregistrement validé.");
    }

    @Transactional
    public Resultat rejeter(String code, Long id, String motif) {
        Utilisateur u = acces.admin();
        if (Texte.vide(motif)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif de rejet est obligatoire.");
        Def<FicheRapport> d = registry.get(code);
        FicheRapport f = trouver(d, id);
        if (f.getStatut() == StatutFiche.VERROUILLE) throw conflit("Enregistrement verrouillé (campagne clôturée).");
        f.setStatut(StatutFiche.BROUILLON);
        f.setMotifRejet(motif.trim());
        f.setValidePar(null);
        f.setValideLe(null);
        audit.log(u.getEmail(), "REJET", code + " #" + id + " : " + motif.trim());
        return new Resultat(d.repo().save(f), "Enregistrement renvoyé en brouillon avec le motif de rejet.");
    }

    // ── Règles ───────────────────────────────────────────────────

    private boolean visiblePar(Def<FicheRapport> d, FicheRapport f, Utilisateur u) {
        return Objects.equals(f.getCreeParId(), u.getId())
                || (u.getChercheurId() != null && d.concerne().test(f, u.getChercheurId()));
    }

    private void verifierModifiable(Def<FicheRapport> d, FicheRapport f, Utilisateur u) {
        if (f.getStatut() == StatutFiche.VERROUILLE)
            throw conflit("Enregistrement verrouillé : la campagne est clôturée. Le directeur doit la rouvrir pour le modifier.");
        if (Acces.chercheur(u)) {
            if (!Objects.equals(f.getCreeParId(), u.getId())) Acces.refuser("Vous ne pouvez modifier que vos propres enregistrements.");
            if (f.getStatut() == StatutFiche.VALIDE) Acces.refuser("Enregistrement déjà validé : contactez l'administrateur pour une correction.");
            if (f.getStatut() == StatutFiche.SOUMIS) Acces.refuser("Enregistrement en cours de revue.");
        }
    }

    /** Le chercheur ne saisit que pendant une campagne « Saisie ouverte » ; personne n'ajoute à une année clôturée. */
    private void verifierCampagne(Def<FicheRapport> d, FicheRapport f, Utilisateur u) {
        Integer annee = d.annee().apply(f);
        if (annee == null) annee = f.getAnneeReference();
        Optional<Campagne> c = annee == null ? Optional.empty() : campagnes.findByAnnee(annee);
        if (c.isPresent() && c.get().getStatut() == Campagne.Statut.CLOTUREE && d.annee().apply(f) != null)
            throw conflit("La campagne " + c.get().getAnnee() + " est clôturée : aucun ajout ni modification pour cette année.");
        if (Acces.chercheur(u) && (c.isEmpty() || c.get().getStatut() != Campagne.Statut.SAISIE_OUVERTE))
            Acces.refuser("La saisie n'est pas ouverte pour l'année " + annee + ".");
    }

    private Integer anneeParDefaut(Def<FicheRapport> d, FicheRapport f) {
        Integer a = d.annee().apply(f);
        if (a != null) return a;
        return campagnes.findAllByOrderByAnneeDesc().stream()
                .filter(c -> c.getStatut() == Campagne.Statut.SAISIE_OUVERTE).map(Campagne::getAnnee).findFirst()
                .orElse(Year.now().getValue());
    }

    private void exigerComplet(Def<FicheRapport> d, FicheRapport f) {
        List<String> manquants = d.obligatoires().apply(f);
        if (!manquants.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champs obligatoires manquants ou invalides : " + String.join(", ", manquants) + ".");
    }

    private static void marquerValide(FicheRapport f, Utilisateur u) {
        f.setStatut(StatutFiche.VALIDE);
        f.setMotifRejet(null);
        f.setValidePar(u.getEmail());
        f.setValideLe(Instant.now());
    }

    private void verifierCinUnique(Def<FicheRapport> d, FicheRapport f) {
        if (!(f instanceof PersonneRapport p) || Texte.vide(p.getCin())) return;
        String e = crypto.empreinte(p.getCin());
        boolean doublon = d.tous().get().stream()
                .anyMatch(o -> o != f && !Objects.equals(o.getId(), f.getId()) && e.equals(((PersonneRapport) o).getCinEmpreinte()));
        if (doublon) throw conflit("Ce numéro de CIN est déjà attribué à une autre fiche.");
    }

    /** Réutilisation automatique (§15.5) : doctorant soutenu → thèse F13, étudiant diplômé → mastère F14. */
    private String suite(FicheRapport f, Utilisateur u) {
        if (f instanceof Doctorant doc && doc.getStatutThese() == Doctorant.Statut.SOUTENU && !theses.existsByDoctorantId(doc.getId())) {
            These t = new These();
            t.setDoctorantId(doc.getId());
            t.setDoctorantNom(doc.getNomComplet());
            t.setAnneePremiereInscription(doc.getAnneePremiereInscription());
            t.setAnneeSoutenance(doc.getDateSoutenance() != null ? doc.getDateSoutenance().getYear() : doc.getAnneeReference());
            t.setAnneeReference(t.getAnneeSoutenance());
            t.setSujet(doc.getSujet());
            t.setEncadrantIds(new ArrayList<>(doc.getDirecteurIds()));
            t.setEncadrantsTexte(doc.getDirecteursTexte());
            t.setDateSoutenance(doc.getDateSoutenance());
            t.setMention(doc.getMention());
            t.setStatut(StatutFiche.BROUILLON);
            t.setCreePar(u.getEmail()); t.setCreeParId(u.getId());
            theses.save(t);
            return "Une fiche « Thèse soutenue » (F13) pré-remplie a été créée en brouillon : complétez son titre.";
        }
        if (f instanceof Masterien m && m.getStatutMastere() == Masterien.Statut.DIPLOME && !masters.existsByEtudiantId(m.getId())) {
            MasterSoutenu ms = new MasterSoutenu();
            ms.setEtudiantId(m.getId());
            ms.setEtudiantNom(m.getNomComplet());
            ms.setAnneePremiereInscription(m.getAnneePremiereInscription());
            ms.setAnneeSoutenance(m.getDateSoutenance() != null ? m.getDateSoutenance().getYear() : m.getAnneeReference());
            ms.setAnneeReference(ms.getAnneeSoutenance());
            ms.setSujet(m.getSujet());
            ms.setEncadrantIds(new ArrayList<>(m.getEncadrantIds()));
            ms.setEncadrantsTexte(m.getEncadrantsTexte());
            ms.setStatut(StatutFiche.BROUILLON);
            ms.setCreePar(u.getEmail()); ms.setCreeParId(u.getId());
            masters.save(ms);
            return "Une fiche « Mastère soutenu » (F14) pré-remplie a été créée en brouillon.";
        }
        return null;
    }

    /** Applique le corps JSON sur l'entité sans jamais toucher aux champs de workflow. */
    private void appliquer(Def<FicheRapport> d, FicheRapport f, JsonNode corps) {
        // Sauvegarde des champs protégés
        Long id = f.getId(); Integer anneeRef = f.getAnneeReference(); StatutFiche statut = f.getStatut();
        String motif = f.getMotifRejet(), creePar = f.getCreePar(), validePar = f.getValidePar();
        Long creeParId = f.getCreeParId(); Instant creeLe = f.getCreeLe(), valideLe = f.getValideLe();
        boolean effacerCin = corps.path("effacerCin").asBoolean(false);
        try {
            mapper.readerForUpdating(f).readValue(corps);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Données invalides : " + (e instanceof com.fasterxml.jackson.core.JsonProcessingException j ? j.getOriginalMessage() : e.getMessage()));
        }
        f.setId(id); f.setStatut(statut); f.setMotifRejet(motif); f.setCreePar(creePar); f.setCreeParId(creeParId);
        f.setCreeLe(creeLe); f.setValidePar(validePar); f.setValideLe(valideLe);
        if (anneeRef != null && !corps.hasNonNull("anneeReference")) f.setAnneeReference(anneeRef);

        if (f instanceof PersonneRapport p) {
            if (effacerCin) p.effacerCin();
            if (p.getCin() != null && !p.getCin().matches("\\d{8}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le N° CIN doit comporter exactement 8 chiffres.");
            p.setNom(Texte.vide(p.getNom()) ? p.getNom() : p.getNom().trim().toUpperCase());
            p.setPrenom(Texte.vide(p.getPrenom()) ? p.getPrenom() : p.getPrenom().trim());
        }
        if (f instanceof Chercheur c) {
            if (c.isDomaineIdFourni()) c.setDomaine(c.getDomaineIdDemande() == null ? null : domaines.findById(c.getDomaineIdDemande()).orElse(null));
            if (Texte.vide(c.getEmail())) c.setEmail(null); else c.setEmail(c.getEmail().trim().toLowerCase());
        }
        if (f instanceof Publication p) {
            // « Comptée comme impactée » : proposée par la table de correspondance, modifiable par l'administrateur seul
            boolean admin = Acces.admin(acces.courant());
            boolean fourni = admin && corps.hasNonNull("estImpactee");
            if (!fourni && (!admin || p.getEstImpactee() == null || corps.has("indexation")))
                p.setEstImpactee(p.getIndexation() == null ? null : correspondances.impactee(p.getIndexation()));
            if (p.getAuteurIdsDemandes() != null) p.setChercheurs(new LinkedHashSet<>(membres.findAllById(p.getAuteurIdsDemandes())));
            if (p.isDomaineIdFourni()) p.setDomaine(p.getDomaineIdDemande() == null ? null : domaines.findById(p.getDomaineIdDemande()).orElse(null));
        }
        if (f instanceof Production p) p.setAuteurIds(existants(p.getAuteurIds()));
        if (f instanceof These t) t.setEncadrantIds(existants(t.getEncadrantIds()));
        if (f instanceof MasterSoutenu m) m.setEncadrantIds(existants(m.getEncadrantIds()));
        if (f instanceof Doctorant doc) doc.setDirecteurIds(existants(doc.getDirecteurIds()));
        if (f instanceof Masterien m) m.setEncadrantIds(existants(m.getEncadrantIds()));
        if (f instanceof Projet p) p.setMembreIds(existants(p.getMembreIds()));
        if (f instanceof Evenement ev && ev.getDateDebut() != null) ev.setAnnee(ev.getDateDebut().getYear());
        verifierAnnees(f);
    }

    private List<Long> existants(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Set<Long> ok = new HashSet<>(membres.findAllById(ids).stream().map(Chercheur::getId).toList());
        return new ArrayList<>(ids.stream().filter(ok::contains).distinct().toList());
    }

    private static void verifierAnnees(FicheRapport f) {
        int max = Year.now().getValue() + 5;
        List<Integer> annees = new ArrayList<>();
        if (f instanceof Publication p) annees.add(p.getAnnee());
        if (f instanceof Production p) annees.add(p.getAnnee());
        if (f instanceof These t) { annees.add(t.getAnneeSoutenance()); annees.add(t.getAnneePremiereInscription());
            if (t.getAnneeSoutenance() != null && t.getAnneePremiereInscription() != null && t.getAnneePremiereInscription() > t.getAnneeSoutenance())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'année de première inscription doit précéder la soutenance."); }
        if (f instanceof MasterSoutenu m) { annees.add(m.getAnneeSoutenance()); annees.add(m.getAnneePremiereInscription()); }
        if (f instanceof Doctorant d) { annees.add(d.getAnneePremiereInscription());
            if (d.getAnneePremiereInscription() != null && d.getAnneeReference() != null && d.getAnneePremiereInscription() > d.getAnneeReference())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'année de première inscription doit être inférieure ou égale à l'année de référence."); }
        if (f instanceof Masterien m) annees.add(m.getAnneePremiereInscription());
        if (f instanceof Convention c) annees.add(c.getAnnee());
        if (f instanceof Evenement e) annees.add(e.getAnnee());
        if (f instanceof Habilitation h) annees.add(h.getAnnee());
        for (Integer a : annees)
            if (a != null && (a < 1950 || a > max))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Année non plausible : " + a + ".");
    }

    private FicheRapport trouver(Def<FicheRapport> d, Long id) {
        FicheRapport f = d.repo().findById(id).orElseThrow(() -> new NoSuchElementException("Enregistrement introuvable"));
        if (!d.tous().get().stream().anyMatch(o -> o.getId().equals(id))) throw new NoSuchElementException("Enregistrement introuvable");
        return f;
    }

    private static ResponseStatusException conflit(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
