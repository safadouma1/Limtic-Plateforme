package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.*;

/** F6 – Étudiant en mastère de recherche. */
@Entity @Table(name = "masteriens")
@Getter @Setter @NoArgsConstructor
public class Masterien extends PersonneRapport {
    public enum Statut { EN_COURS, DIPLOME }

    @Column(length = 2000)
    private String sujet;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "masterien_encadrants", joinColumns = @JoinColumn(name = "fiche_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private List<Long> encadrantIds = new ArrayList<>();
    private String encadrantsTexte;
    private Integer anneePremiereInscription;
    private String promotion;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Statut statutMastere = Statut.EN_COURS;
    private LocalDate dateSoutenance;
}
