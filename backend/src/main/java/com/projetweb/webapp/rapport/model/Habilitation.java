package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** F12 – Habilitation universitaire / agrégation hospitalo-universitaire. */
@Entity @Table(name = "habilitations")
@Getter @Setter @NoArgsConstructor
public class Habilitation extends FicheRapport {
    public enum Nature { HABILITATION, AGREGATION_HU }

    @Column(length = 1000)
    private String titre;
    private Long titulaireId;
    private String titulaireNom;
    private Integer annee;
    private Long encadreurId;
    private String encadreurNom;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Nature nature = Nature.HABILITATION;
}
