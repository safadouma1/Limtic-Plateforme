package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.*;

/** F4 – Doctorant. */
@Entity @Table(name = "doctorants")
@Getter @Setter @NoArgsConstructor
public class Doctorant extends PersonneRapport {
    public enum Statut { EN_COURS, SOUTENU, ABANDON }

    @Column(length = 2000)
    private String sujet;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "doctorant_directeurs", joinColumns = @JoinColumn(name = "fiche_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private List<Long> directeurIds = new ArrayList<>();
    /** Directeurs extérieurs au laboratoire ou texte importé. */
    private String directeursTexte;
    private Integer anneePremiereInscription;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Statut statutThese = Statut.EN_COURS;
    private LocalDate dateSoutenance;
    private String mention;
}
