package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Chercheur;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.DomaineRepository;
import com.projetweb.webapp.repository.PublicationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/chercheurs")
public class ChercheurController {

    private final ChercheurRepository chercheurs;
    private final DomaineRepository domaines;
    private final PublicationRepository publications;

    public ChercheurController(ChercheurRepository c, DomaineRepository d, PublicationRepository p) {
        this.chercheurs = c; this.domaines = d; this.publications = p;
    }

    @GetMapping
    public List<ChercheurResponse> lister() {
        return chercheurs.findAll().stream().filter(c -> c.getStatut() == null || c.getStatut().pris())
                .sorted(java.util.Comparator.comparing(Chercheur::getNom).thenComparing(Chercheur::getPrenom)).map(this::versDto).toList();
    }

    @GetMapping("/{id}")
    public ChercheurResponse detail(@PathVariable Long id) {
        return versDto(trouver(id));
    }

    @GetMapping("/{id}/publications")
    public List<PublicationResponse> publicationsDe(@PathVariable Long id) {
        trouver(id);
        return publications.findByChercheur(id).stream().filter(p -> p.getStatut() == null || p.getStatut().pris())
                .map(PublicationResponse::of).toList();
    }

    @GetMapping("/rechercher")
    public List<ChercheurResponse> rechercher(@RequestParam String nom) {
        return chercheurs.rechercher(nom.trim()).stream().map(this::versDto).toList();
    }

    @GetMapping("/domaine/{domaineId}")
    public List<ChercheurResponse> parDomaine(@PathVariable Long domaineId) {
        return chercheurs.findByDomaineId(domaineId).stream().map(this::versDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChercheurResponse creer(@Valid @RequestBody ChercheurRequest req) {
        // Les champs du rapport (grade, CIN, établissement…) se saisissent dans le module Rapport (formulaire F3/F5).
        if (req.email() != null && !req.email().isBlank() && chercheurs.existsByEmailIgnoreCase(req.email().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un chercheur existe déjà avec cet email.");
        }
        Chercheur c = new Chercheur();
        appliquer(c, req);
        return versDto(chercheurs.save(c));
    }

    @PutMapping("/{id}")
    public ChercheurResponse modifier(@PathVariable Long id, @Valid @RequestBody ChercheurRequest req) {
        Chercheur c = trouver(id);
        appliquer(c, req);
        return versDto(chercheurs.save(c));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void supprimer(@PathVariable Long id) {
        Chercheur c = trouver(id);
        publications.findByChercheur(id).forEach(p -> p.getChercheurs().remove(c));
        chercheurs.delete(c);
    }

    private void appliquer(Chercheur c, ChercheurRequest req) {
        c.setNom(req.nom().trim());
        c.setPrenom(req.prenom().trim());
        c.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase());
        c.setSpecialite(req.specialite());
        c.setEtablissement(req.etablissement());
        if (req.grade() != null) c.setGrade(vide(req.grade()));
        c.setBureau(vide(req.bureau()));
        c.setTelephone(vide(req.telephone()));
        c.setOrcid(vide(req.orcid()));
        c.setGoogleScholarUrl(url(req.googleScholarUrl()));
        c.setResearchGateUrl(url(req.researchGateUrl()));
        c.setLinkedinUrl(url(req.linkedinUrl()));
        c.setBiographie(vide(req.biographie()));
        c.setPhotoMediaId(req.photoMediaId());
        if (req.statutMembre() != null) c.setStatutMembre(req.statutMembre());
        c.setDomaine(req.domaineId() == null ? null : domaines.findById(req.domaineId())
                .orElseThrow(() -> new NoSuchElementException("Domaine introuvable")));
    }

    static String vide(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Lien externe : seules les URL http(s) sont acceptées (pas de javascript:). */
    static String url(String s) {
        String v = vide(s);
        if (v == null) return null;
        if (!v.matches("(?i)^https?://\\S+$")) throw new IllegalArgumentException("Lien invalide : " + v + " (http:// ou https:// attendu).");
        return v;
    }

    private Chercheur trouver(Long id) {
        return chercheurs.findById(id).orElseThrow(() -> new NoSuchElementException("Chercheur introuvable"));
    }

    private ChercheurResponse versDto(Chercheur c) {
        return ChercheurResponse.of(c, publications.countByChercheur(c.getId()));
    }
}
