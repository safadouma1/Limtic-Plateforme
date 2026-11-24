package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Publication;
import com.projetweb.webapp.model.StatutFiche;
import com.projetweb.webapp.rapport.service.Correspondances;
import org.springframework.web.server.ResponseStatusException;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.DomaineRepository;
import com.projetweb.webapp.repository.PublicationRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    private final PublicationRepository publications;
    private final ChercheurRepository chercheurs;
    private final DomaineRepository domaines;
    private final Correspondances correspondances;

    public PublicationController(PublicationRepository p, ChercheurRepository c, DomaineRepository d, Correspondances corr) {
        this.publications = p; this.chercheurs = c; this.domaines = d; this.correspondances = corr;
    }

    @GetMapping
    public List<PublicationResponse> lister() {
        // Site public : seules les publications validées apparaissent (les brouillons restent dans le module Rapport)
        return publications.findAll(Sort.by(Sort.Direction.DESC, "annee").and(Sort.by("titre")))
                .stream().filter(p -> p.getStatut() == null || p.getStatut().pris()).map(PublicationResponse::of).toList();
    }

    @GetMapping("/{id}")
    public PublicationResponse detail(@PathVariable Long id) {
        Publication p = trouver(id);
        if (p.getStatut() != null && !p.getStatut().pris()) throw new NoSuchElementException("Publication introuvable");
        return PublicationResponse.of(p);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse creer(@Valid @RequestBody PublicationRequest req) {
        Publication p = new Publication();
        appliquer(p, req);
        p.setStatut(StatutFiche.VALIDE);
        p.setAnneeReference(p.getAnnee());
        return PublicationResponse.of(publications.save(p));
    }

    @PutMapping("/{id}")
    public PublicationResponse modifier(@PathVariable Long id, @Valid @RequestBody PublicationRequest req) {
        Publication p = trouver(id);
        if (p.getStatut() == StatutFiche.VERROUILLE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Publication verrouillée : la campagne de son année est clôturée.");
        appliquer(p, req);
        return PublicationResponse.of(publications.save(p));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        Publication p = trouver(id);
        if (p.getStatut() == StatutFiche.VERROUILLE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Publication verrouillée : la campagne de son année est clôturée.");
        publications.delete(p);
    }

    private void appliquer(Publication p, PublicationRequest req) {
        p.setTitre(req.titre().trim());
        p.setResume(req.resume());
        p.setAnnee(req.annee());
        p.setDoi(vide(req.doi()));
        p.setPdfUrl(vide(req.pdfUrl()));
        p.setMotsCles(req.motsCles() == null ? new ArrayList<>() : new ArrayList<>(req.motsCles().stream()
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList()));
        p.setChercheurs(req.chercheurIds() == null ? new LinkedHashSet<>()
                : new LinkedHashSet<>(chercheurs.findAllById(req.chercheurIds())));
        p.setDomaine(req.domaineId() == null ? null : domaines.findById(req.domaineId())
                .orElseThrow(() -> new NoSuchElementException("Domaine introuvable")));
        // Champs du rapport d'activité (F7)
        if (req.type() != null) p.setType(req.type());
        if (req.venue() != null) p.setVenue(vide(req.venue()));
        if (req.indexation() != null) p.setIndexation(req.indexation());
        if (req.estImpactee() != null) p.setEstImpactee(req.estImpactee());
        else if (p.getEstImpactee() == null && p.getIndexation() != null) p.setEstImpactee(correspondances.impactee(p.getIndexation()));
        if (req.auteursTexte() != null) p.setAuteursTexte(vide(req.auteursTexte()));
        // Partie 1 §3.7 : détails bibliographiques et classement
        p.setClassement(vide(req.classement()));
        p.setSourceClassement(req.sourceClassement());
        p.setFacteurImpact(req.facteurImpact());
        p.setSnip(req.snip());
        p.setCitations(req.citations());
        p.setVolume(vide(req.volume())); p.setNumero(vide(req.numero())); p.setPages(vide(req.pages()));
        p.setLieu(vide(req.lieu())); p.setPays(vide(req.pays())); p.setEditeur(vide(req.editeur()));
        p.setIsbn(vide(req.isbn())); p.setInstitution(vide(req.institution())); p.setComiteOrganisation(vide(req.comiteOrganisation()));
        p.setPdfMediaId(req.pdfMediaId());
    }

    private static String vide(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private Publication trouver(Long id) {
        return publications.findById(id).orElseThrow(() -> new NoSuchElementException("Publication introuvable"));
    }
}
