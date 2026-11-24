package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** Octets d'un fichier archivé (immuable). */
@Entity @Table(name = "contenus_fichier")
@Getter @Setter @NoArgsConstructor
public class ContenuFichier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY) @Column(length = 100_000_000)
    private byte[] octets;

    public ContenuFichier(byte[] octets) { this.octets = octets; }
}
