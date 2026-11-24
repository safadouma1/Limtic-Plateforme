package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** F16 – Convention avec un partenaire. */
@Entity @Table(name = "conventions")
@Getter @Setter @NoArgsConstructor
public class Convention extends FicheRapport {
    public enum Type { SOCIO_ECONOMIQUE, COOPERATION_INTERNATIONALE, AUTRE }

    private Integer annee;
    @Column(length = 1000)
    private String reference;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private Type type = Type.SOCIO_ECONOMIQUE;
    private String partenaire;
    private String pays;
    private String secteur;
    @Column(length = 4000)
    private String objet;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Long responsableId;
}
