package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** Tables de correspondance configurables (§15.4) : grade → corps, indexation → impactée, convention → rubrique. */
@Entity @Table(name = "regles_correspondance", uniqueConstraints = @UniqueConstraint(columnNames = {"tableCode", "cle"}))
@Getter @Setter @NoArgsConstructor
public class RegleCorrespondance {
    public enum Table { GRADE_CORPS, INDEXATION_IMPACTEE, CONVENTION_RUBRIQUE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(length = 30, nullable = false)
    private Table tableCode;
    @Column(nullable = false)
    private String cle;
    @Column(nullable = false)
    private String valeur;

    public RegleCorrespondance(Table t, String cle, String valeur) { this.tableCode = t; this.cle = cle; this.valeur = valeur; }
}
