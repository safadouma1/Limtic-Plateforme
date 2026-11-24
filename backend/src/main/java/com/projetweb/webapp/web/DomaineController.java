package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Domaine;
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
@RequestMapping("/api/domaines")
public class DomaineController {

    private final DomaineRepository domaines;
    private final ChercheurRepository chercheurs;
    private final PublicationRepository publications;

    public DomaineController(DomaineRepository d, ChercheurRepository c, PublicationRepository p) {
        this.domaines = d; this.chercheurs = c; this.publications = p;
    }

    @GetMapping
    public List<DomaineResponse> lister() {
        return domaines.findAll().stream().map(this::versDto).toList();
    }

    @GetMapping("/{id}")
    public DomaineResponse detail(@PathVariable Long id) {
        return versDto(trouver(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomaineResponse creer(@Valid @RequestBody DomaineRequest req) {
        if (domaines.existsByNomIgnoreCase(req.nom().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce domaine existe déjà.");
        }
        Domaine d = new Domaine(req.nom().trim(), req.description());
        d.setResponsableIds(req.responsableIds() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(req.responsableIds()));
        return versDto(domaines.save(d));
    }

    @PutMapping("/{id}")
    public DomaineResponse modifier(@PathVariable Long id, @Valid @RequestBody DomaineRequest req) {
        Domaine d = trouver(id);
        d.setNom(req.nom().trim());
        d.setDescription(req.description());
        if (req.responsableIds() != null) d.setResponsableIds(new java.util.ArrayList<>(req.responsableIds()));
        return versDto(domaines.save(d));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void supprimer(@PathVariable Long id) {
        Domaine d = trouver(id);
        // On détache le domaine des chercheurs/publications au lieu de bloquer la suppression
        chercheurs.findByDomaineId(id).forEach(c -> c.setDomaine(null));
        publications.findByDomaineId(id).forEach(p -> p.setDomaine(null));
        domaines.delete(d);
    }

    private Domaine trouver(Long id) {
        return domaines.findById(id).orElseThrow(() -> new NoSuchElementException("Domaine introuvable"));
    }

    private DomaineResponse versDto(Domaine d) {
        return new DomaineResponse(d.getId(), d.getNom(), d.getDescription(),
                chercheurs.countByDomaineId(d.getId()), publications.countByDomaineId(d.getId()),
                chercheurs.findAllById(d.getResponsableIds()).stream().map(AuteurRef::of).toList());
    }
}
