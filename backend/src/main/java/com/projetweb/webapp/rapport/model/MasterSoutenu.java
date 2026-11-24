package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

/** F14 – Mastère de recherche soutenu (généré depuis F6). */
@Entity @Table(name = "masters_soutenus")
@Getter @Setter @NoArgsConstructor
public class MasterSoutenu extends FicheRapport {
    private Integer anneeSoutenance;
    private Integer anneePremiereInscription;
    @Column(length = 2000)
    private String sujet;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "master_encadrants", joinColumns = @JoinColumn(name = "fiche_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private List<Long> encadrantIds = new ArrayList<>();
    private String encadrantsTexte;
    private Long etudiantId;
    private String etudiantNom;
    private String mention;
}
