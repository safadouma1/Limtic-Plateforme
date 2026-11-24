package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** F2 – Résumé des activités, un par campagne. */
@Entity @Table(name = "resumes_rapport")
@Getter @Setter @NoArgsConstructor
public class ResumeRapport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private Integer annee;
    @Column(length = 2000)
    private String historique;
    @Column(length = 4000)
    private String thematiques;
    @Column(length = 2000)
    private String bilan;
}
