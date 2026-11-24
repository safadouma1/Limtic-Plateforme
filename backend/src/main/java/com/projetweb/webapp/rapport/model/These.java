package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.*;

/** F13 – Thèse de doctorat soutenue (générée depuis F4). */
@Entity @Table(name = "theses")
@Getter @Setter @NoArgsConstructor
public class These extends FicheRapport {
    @Column(length = 1000)
    private String titre;
    private Integer anneeSoutenance;
    private Integer anneePremiereInscription;
    @Column(length = 8000)
    private String sujet;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "these_encadrants", joinColumns = @JoinColumn(name = "fiche_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private List<Long> encadrantIds = new ArrayList<>();
    private String encadrantsTexte;
    private Long doctorantId;
    private String doctorantNom;
    private LocalDate dateSoutenance;
    private String mention;
}
