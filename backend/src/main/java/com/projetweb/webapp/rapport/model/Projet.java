package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.*;

/** F17 – Projet de recherche. */
@Entity @Table(name = "projets")
@Getter @Setter @NoArgsConstructor
public class Projet extends FicheRapport {
    public enum Type { NATIONAL, INTERNATIONAL, CONTRAT }

    @Column(length = 1000)
    private String intitule;
    private Long coordinateurId;
    private String coordinateurExterne;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Type type = Type.NATIONAL;
    @Column(length = 500)
    private String financeur;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "projet_membres", joinColumns = @JoinColumn(name = "fiche_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private List<Long> membreIds = new ArrayList<>();
    private String axe;
    /** Axe de recherche (domaine) auquel le projet est rattaché sur le site (§3.6). */
    private Long axeId;

    /** En cours pendant l'année N ; sans dates, le projet est considéré en cours. */
    public boolean enCoursEn(int n) {
        return (dateDebut == null || dateDebut.getYear() <= n) && (dateFin == null || dateFin.getYear() >= n);
    }
}
