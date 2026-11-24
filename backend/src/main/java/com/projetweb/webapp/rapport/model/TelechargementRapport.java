package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Historique des téléchargements (§17.4). */
@Entity @Table(name = "telechargements_rapport")
@Getter @Setter @NoArgsConstructor
public class TelechargementRapport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long rapportId;
    private Integer annee;
    private int version;
    private Long fichierId;
    private String nomFichier;
    private String format;
    private String utilisateur;
    private Instant telechargeLe;
}
