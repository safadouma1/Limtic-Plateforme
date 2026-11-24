package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Publication;
import com.projetweb.webapp.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/** Statistiques réelles calculées à partir de la base (aucune valeur codée en dur). */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final ChercheurRepository chercheurs;
    private final PublicationRepository publications;
    private final DomaineRepository domaines;
    private final UtilisateurRepository utilisateurs;

    public StatsController(ChercheurRepository c, PublicationRepository p, DomaineRepository d, UtilisateurRepository u) {
        this.chercheurs = c; this.publications = p; this.domaines = d; this.utilisateurs = u;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        List<Publication> pubs = publications.findAll().stream().filter(p -> p.getStatut() == null || p.getStatut().pris()).toList();

        List<CountItem> parDomaine = trier(pubs.stream().collect(Collectors.groupingBy(
                p -> p.getDomaine() == null ? "Sans domaine" : p.getDomaine().getNom(), Collectors.counting())));

        List<CountItem> parAnnee = pubs.stream().filter(p -> p.getAnnee() != null)
                .collect(Collectors.groupingBy(Publication::getAnnee, TreeMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new CountItem(String.valueOf(e.getKey()), e.getValue())).toList();

        Map<String, Long> mots = new HashMap<>();
        Map<String, String> libelle = new HashMap<>();
        pubs.forEach(p -> p.getMotsCles().forEach(m -> {
            String k = m.trim().toLowerCase(Locale.ROOT);
            libelle.putIfAbsent(k, m.trim());
            mots.merge(k, 1L, Long::sum);
        }));
        List<CountItem> motsFrequents = mots.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12).map(e -> new CountItem(libelle.get(e.getKey()), e.getValue())).toList();

        return new StatsResponse(chercheurs.count(), pubs.size(), domaines.count(), utilisateurs.count(),
                parDomaine, parAnnee, motsFrequents);
    }

    private static List<CountItem> trier(Map<String, Long> m) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new CountItem(e.getKey(), e.getValue())).toList();
    }
}
