package com.projetweb.webapp.rapport.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** Fichier d'une version (Word, PDF, ZIP ou CSV d'un tableau). Le contenu est stocké à part. */
@Entity @Table(name = "fichiers_rapport")
@Getter @Setter @NoArgsConstructor
public class FichierRapport {
    public enum Format { DOCX, PDF, ZIP, CSV }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JsonIgnore
    private RapportGenere rapport;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Format format;
    /** Pour un CSV : numéro et code du tableau (ex. II-1). */
    private String tableau;
    private String nomFichier;
    private long taille;
    @Column(length = 64)
    private String sha256;
    @JsonIgnore
    private Long contenuId;
}
