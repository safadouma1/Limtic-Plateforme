package com.projetweb.webapp.rapport.service;

import com.projetweb.webapp.model.Publication;
import com.projetweb.webapp.rapport.model.Convention;
import com.projetweb.webapp.rapport.model.RegleCorrespondance;
import com.projetweb.webapp.rapport.model.RegleCorrespondance.Table;
import com.projetweb.webapp.rapport.repo.RegleCorrespondanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Tables de correspondance modifiables par le super-administrateur (§15.4).
 * Valeurs initiales déduites du rapport 2025 (à valider par l'encadrant).
 */
@Service
public class Correspondances {
    private final RegleCorrespondanceRepository repo;

    public Correspondances(RegleCorrespondanceRepository repo) { this.repo = repo; }

    @Transactional
    public void initialiser() {
        if (repo.count() > 0) return;
        for (String g : List.of("Professeur d'Enseignement Supérieur", "Professeur", "Maître de Conférences", "Maître Technologue"))
            repo.save(new RegleCorrespondance(Table.GRADE_CORPS, g, "A"));
        for (String g : List.of("Maître Assistant", "Technologue", "Assistant Technologue", "Assistant"))
            repo.save(new RegleCorrespondance(Table.GRADE_CORPS, g, "B"));
        repo.save(new RegleCorrespondance(Table.INDEXATION_IMPACTEE, "WOS", "OUI"));
        repo.save(new RegleCorrespondance(Table.INDEXATION_IMPACTEE, "SCOPUS", "NON"));
        repo.save(new RegleCorrespondance(Table.INDEXATION_IMPACTEE, "AUTRES", "NON"));
        repo.save(new RegleCorrespondance(Table.INDEXATION_IMPACTEE, "AUCUNE", "NON"));
        repo.save(new RegleCorrespondance(Table.CONVENTION_RUBRIQUE, "SOCIO_ECONOMIQUE", "CONVENTIONS"));
        repo.save(new RegleCorrespondance(Table.CONVENTION_RUBRIQUE, "COOPERATION_INTERNATIONALE", "COOPERATION"));
        repo.save(new RegleCorrespondance(Table.CONVENTION_RUBRIQUE, "AUTRE", "AUCUNE"));
    }

    public List<RegleCorrespondance> toutes() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing((RegleCorrespondance r) -> r.getTableCode().ordinal()).thenComparing(RegleCorrespondance::getValeur)
                        .thenComparing(RegleCorrespondance::getCle)).toList();
    }

    @Transactional
    public List<RegleCorrespondance> remplacer(Table table, Map<String, String> valeurs) {
        repo.deleteAll(repo.findByTableCode(table));
        repo.flush();
        valeurs.forEach((k, v) -> {
            if (!Texte.vide(k) && !Texte.vide(v)) repo.save(new RegleCorrespondance(table, k.trim(), v.trim()));
        });
        return toutes();
    }

    private Map<String, String> table(Table t) {
        Map<String, String> m = new HashMap<>();
        repo.findByTableCode(t).forEach(r -> m.put(Texte.norm(r.getCle()), r.getValeur()));
        return m;
    }

    /** « A », « B » ou null si le grade n'a pas de correspondance. */
    public String corps(String grade) { return grade == null ? null : table(Table.GRADE_CORPS).get(Texte.norm(grade)); }

    public Function1 corpsFn() {
        Map<String, String> m = table(Table.GRADE_CORPS);
        return g -> g == null ? null : m.get(Texte.norm(g));
    }

    public boolean impactee(Publication.Indexation ix) {
        if (ix == null) return false;
        return "OUI".equalsIgnoreCase(table(Table.INDEXATION_IMPACTEE).get(Texte.norm(ix.name())));
    }

    /** Code de la section du rapport qui reçoit ce type de convention (CONVENTIONS, COOPERATION ou AUCUNE). */
    public Map<Convention.Type, String> rubriquesConventions() {
        Map<String, String> m = table(Table.CONVENTION_RUBRIQUE);
        Map<Convention.Type, String> r = new EnumMap<>(Convention.Type.class);
        for (Convention.Type t : Convention.Type.values()) r.put(t, m.getOrDefault(Texte.norm(t.name()), "AUCUNE"));
        return r;
    }

    public List<String> grades() {
        return repo.findByTableCode(Table.GRADE_CORPS).stream().map(RegleCorrespondance::getCle).sorted().toList();
    }

    @FunctionalInterface
    public interface Function1 { String apply(String s); }
}
