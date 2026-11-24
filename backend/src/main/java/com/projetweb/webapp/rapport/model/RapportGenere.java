package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Une version générée du rapport (§17.4) avec ses fichiers. */
@Entity @Table(name = "rapports_generes", uniqueConstraints = @UniqueConstraint(columnNames = {"annee", "version"}))
@Getter @Setter @NoArgsConstructor
public class RapportGenere {
    public enum Nature { PROVISOIRE, FINALE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer annee;
    private int version;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Nature nature;
    private boolean avecCin;
    private boolean inclureSoumis;
    private boolean remplacee;
    /** Numéro de la clôture dont provient une version finale. */
    private int numeroCloture;
    private String gabaritVersion;
    private String generePar;
    private Instant genereLe;
    private int nbBloquants;
    private int nbAvertissements;

    @OneToMany(mappedBy = "rapport", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<FichierRapport> fichiers = new ArrayList<>();
}
