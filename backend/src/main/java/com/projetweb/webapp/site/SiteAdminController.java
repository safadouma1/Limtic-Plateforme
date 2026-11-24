package com.projetweb.webapp.site;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.Acces;
import com.projetweb.webapp.rapport.service.AuditService;
import com.projetweb.webapp.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Back-office du site (Partie 1, chapitre 4) : paramètres, messages, alertes, exports, fiche du chercheur. */
@RestController
public class SiteAdminController {
    private final Acces acces;
    private final AuditService audit;
    private final SiteParametresRepository parametres;
    private final ContactMessageRepository messages;
    private final ChercheurRepository chercheurs;
    private final DomaineRepository domaines;
    private final PublicationRepository publications;
    private final DoctorantRepository doctorants;
    private final MasterienRepository masteriens;
    private final EvenementRepository evenements;
    private final EntityManager em;

    public SiteAdminController(Acces acces, AuditService audit, SiteParametresRepository parametres, ContactMessageRepository messages,
                               ChercheurRepository chercheurs, DomaineRepository domaines, PublicationRepository publications,
                               DoctorantRepository doctorants, MasterienRepository masteriens, EvenementRepository evenements, EntityManager em) {
        this.acces = acces; this.audit = audit; this.parametres = parametres; this.messages = messages; this.chercheurs = chercheurs;
        this.domaines = domaines; this.publications = publications; this.doctorants = doctorants; this.masteriens = masteriens;
        this.evenements = evenements; this.em = em;
    }

    // ── Paramètres généraux et page du directeur ────────────────
    @GetMapping("/api/site")
    @Transactional
    public SiteParametres lire() {
        acces.admin();
        return parametres.actuels();
    }

    @PutMapping("/api/site")
    @Transactional
    public SiteParametres enregistrer(@RequestBody SiteParametres p) {
        Utilisateur u = acces.admin();
        p.setId(1L);
        p.setMotDirecteur(Html.nettoyer(p.getMotDirecteur()));
        if (p.getCouleurPrincipale() != null && !p.getCouleurPrincipale().matches("^#[0-9a-fA-F]{6}$")) p.setCouleurPrincipale("#5b8cff");
        for (String lien : new String[]{p.getFacebookUrl(), p.getLinkedinUrl(), p.getYoutubeUrl(), p.getSiteIsiUrl()})
            if (lien != null && !lien.isBlank() && !lien.matches("(?i)^https?://\\S+$"))
                throw new IllegalArgumentException("Lien invalide : " + lien + " (http:// ou https:// attendu).");
        audit.log(u.getEmail(), "SITE_PARAMETRES", "Paramètres généraux du site modifiés");
        return parametres.save(p);
    }

    // ── Messages de contact ─────────────────────────────────────
    @GetMapping("/api/contact")
    public List<ContactMessage> messages() {
        acces.admin();
        return messages.findAllByOrderByCreeLeDesc();
    }

    @PatchMapping("/api/contact/{id}")
    @Transactional
    public ContactMessage marquer(@PathVariable Long id, @RequestBody Map<String, Boolean> corps) {
        acces.admin();
        ContactMessage m = messages.findById(id).orElseThrow(() -> new NoSuchElementException("Message introuvable"));
        m.setLu(Boolean.TRUE.equals(corps.get("lu")));
        return messages.save(m);
    }

    @DeleteMapping("/api/contact/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        Utilisateur u = acces.admin();
        messages.deleteById(id);
        audit.log(u.getEmail(), "CONTACT_SUPPRIME", "Message de contact #" + id);
    }

    // ── Tableau de bord : alertes (§4.3.1) ──────────────────────
    public record Alerte(String categorie, String libelle, long nombre, String lien) {}
    public record Alertes(List<Alerte> enAttente, List<SiteController.EvenementResume> prochainsEvenements, long messagesNonLus,
                          Map<String, Long> publicationsParType, long doctorants, long masteriens, long evenements) {}

    private static final List<String[]> FICHES = List.of(
            new String[]{"Chercheur", "membres", "Membres"}, new String[]{"Doctorant", "doctorants", "Doctorants"},
            new String[]{"Masterien", "masteriens", "Étudiants en mastère"}, new String[]{"Publication", "publications", "Publications"},
            new String[]{"Production", "ouvrages", "Ouvrages, chapitres, brevets"}, new String[]{"These", "theses", "Thèses soutenues"},
            new String[]{"MasterSoutenu", "masters", "Mastères soutenus"}, new String[]{"Evenement", "evenements", "Événements"},
            new String[]{"Convention", "conventions", "Conventions"}, new String[]{"Projet", "projets", "Projets"},
            new String[]{"Habilitation", "habilitations", "Habilitations"});

    @GetMapping("/api/admin/alertes")
    @Transactional(readOnly = true)
    public Alertes alertes() {
        acces.admin();
        List<Alerte> l = new ArrayList<>();
        for (String[] f : FICHES) {
            long n = em.createQuery("select count(e) from " + f[0] + " e where e.statut = :s", Long.class)
                    .setParameter("s", StatutFiche.SOUMIS).getSingleResult();
            if (n > 0) l.add(new Alerte(f[1], f[2], n, "/rapport/saisie/" + f[1]));
        }
        LocalDate auj = LocalDate.now();
        var prochains = evenements.findAll().stream().filter(e -> e.getDateDebut() != null && !e.getDateDebut().isBefore(auj)
                        && !e.getDateDebut().isAfter(auj.plusDays(60)))
                .sorted(Comparator.comparing(Evenement::getDateDebut))
                .map(e -> new SiteController.EvenementResume(e.getId(), e.getTitre(), e.getType(), e.getDateDebut(), e.getDateFin(),
                        e.anneeEffective(), e.getLieu(), e.getEtat(), null)).toList();
        Map<String, Long> parType = publications.findAll().stream().filter(p -> p.getStatut() == null || p.getStatut().pris())
                .collect(Collectors.groupingBy(p -> p.getType() == null ? "AUTRE" : p.getType().name(), TreeMap::new, Collectors.counting()));
        return new Alertes(l, prochains, messages.countByLuFalse(), parType,
                doctorants.count(), masteriens.count(), evenements.count());
    }

    // ── Export CSV des listes de membres (§4.3.2), sans CIN ─────
    @GetMapping("/api/admin/export/{type}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exporter(@PathVariable String type) {
        Utilisateur u = acces.admin();
        Map<Long, Chercheur> m = chercheurs.findAll().stream().collect(Collectors.toMap(Chercheur::getId, c -> c));
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case "chercheurs" -> {
                sb.append("Nom;Prénom;Catégorie;Grade;Spécialité;Axe;Email;Bureau;Téléphone;Établissement;Statut;ORCID\n");
                m.values().stream().sorted(Comparator.comparing(Chercheur::getNom)).forEach(c -> ligne(sb, c.getNom(), c.getPrenom(),
                        c.getCategorie(), c.getGrade(), c.getSpecialite(), c.getDomaine() == null ? null : c.getDomaine().getNom(), c.getEmail(),
                        c.getBureau(), c.getTelephone(), c.getEtablissement(), c.getStatutMembre(), c.getOrcid()));
            }
            case "doctorants" -> {
                sb.append("Nom;Prénom;Sujet;Directeur(s);Année de 1re inscription;Statut;Date de soutenance;Mention;Établissement\n");
                doctorants.findAll().stream().sorted(Comparator.comparing(Doctorant::getNom)).forEach(d -> ligne(sb, d.getNom(), d.getPrenom(),
                        d.getSujet(), noms(d.getDirecteurIds(), m, d.getDirecteursTexte()), d.getAnneePremiereInscription(), d.getStatutThese(),
                        d.getDateSoutenance(), d.getMention(), d.getEtablissement()));
            }
            case "masteriens" -> {
                sb.append("Nom;Prénom;Sujet;Encadrant(s);Promotion;Statut;Établissement\n");
                masteriens.findAll().stream().sorted(Comparator.comparing(Masterien::getNom)).forEach(x -> ligne(sb, x.getNom(), x.getPrenom(),
                        x.getSujet(), noms(x.getEncadrantIds(), m, x.getEncadrantsTexte()),
                        x.getPromotion() != null ? x.getPromotion() : x.getAnneePremiereInscription(), x.getStatutMastere(), x.getEtablissement()));
            }
            default -> throw new IllegalArgumentException("Type d'export inconnu : " + type);
        }
        audit.log(u.getEmail(), "EXPORT_MEMBRES", "Export CSV " + type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("LIMTIC_" + type + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(("﻿" + sb).getBytes(StandardCharsets.UTF_8));
    }

    private static String noms(List<Long> ids, Map<Long, Chercheur> m, String texte) {
        String s = ids.stream().map(m::get).filter(Objects::nonNull).map(Chercheur::getNomComplet).collect(Collectors.joining(", "));
        return s.isBlank() ? texte : s;
    }

    private static void ligne(StringBuilder sb, Object... v) {
        sb.append(Arrays.stream(v).map(Exports::c).collect(Collectors.joining(";"))).append('\n');
    }

    // ── Page personnelle : le chercheur modifie sa propre fiche (§3.3.2, §7.3) ──
    @GetMapping("/api/me/fiche")
    @Transactional(readOnly = true)
    public ChercheurResponse maFiche() {
        Chercheur c = moi();
        return ChercheurResponse.of(c, publications.countByChercheur(c.getId()));
    }

    @PutMapping("/api/me/fiche")
    @Transactional
    public ChercheurResponse modifierMaFiche(@Valid @RequestBody MaFicheRequest r) {
        Chercheur c = moi();
        c.setSpecialite(vide(r.specialite()));
        c.setBureau(vide(r.bureau()));
        c.setTelephone(vide(r.telephone()));
        c.setOrcid(vide(r.orcid()));
        c.setGoogleScholarUrl(url(r.googleScholarUrl()));
        c.setResearchGateUrl(url(r.researchGateUrl()));
        c.setLinkedinUrl(url(r.linkedinUrl()));
        c.setBiographie(vide(r.biographie()));
        c.setPhotoMediaId(r.photoMediaId());
        if (r.domaineId() != null) c.setDomaine(domaines.findById(r.domaineId()).orElseThrow(() -> new NoSuchElementException("Axe introuvable")));
        chercheurs.save(c);
        return ChercheurResponse.of(c, publications.countByChercheur(c.getId()));
    }

    private Chercheur moi() {
        Utilisateur u = acces.courant();
        if (u.getChercheurId() == null)
            throw new IllegalArgumentException("Votre compte n'est relié à aucune fiche chercheur : demandez-le à l'administrateur.");
        return chercheurs.findById(u.getChercheurId()).orElseThrow(() -> new NoSuchElementException("Fiche chercheur introuvable"));
    }

    private static String vide(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String url(String s) {
        String v = vide(s);
        if (v != null && !v.matches("(?i)^https?://\\S+$")) throw new IllegalArgumentException("Lien invalide : " + v + " (http:// ou https:// attendu).");
        return v;
    }
}
