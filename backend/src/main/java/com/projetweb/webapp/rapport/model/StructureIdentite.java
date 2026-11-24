package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** F1 – Identité de la structure (page de garde). Enregistrement unique. */
@Entity @Table(name = "structure_identite")
@Getter @Setter @NoArgsConstructor
public class StructureIdentite {
    @Id
    private Long id = 1L;
    private String universite;
    private String etablissement;
    @Column(length = 500)
    private String denomination;
    private String code;
    private Long chefId;
    private String chefNom;
    private String chefGrade;
    private String fonction;
    private String telephone;
    private String fax;
    private String email;
    private String siteWeb;
}
