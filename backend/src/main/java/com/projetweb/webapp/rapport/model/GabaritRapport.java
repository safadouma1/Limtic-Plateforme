package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Gabarit du rapport (§17.1) : ordre des sections, libellés, colonnes, en-tête (JSON). */
@Entity @Table(name = "gabarits_rapport")
@Getter @Setter @NoArgsConstructor
public class GabaritRapport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String version;
    private boolean actif;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String structure;
}
