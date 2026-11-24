package com.projetweb.webapp.site;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Fichier déposé sur le site : photo de profil, photo d'événement, PDF de publication ou de programme. */
@Entity @Table(name = "media")
@Getter @Setter @NoArgsConstructor
public class Media {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 255) private String nom;
    @Column(length = 100) private String contentType;
    private long taille;
    @JsonIgnore
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "VARBINARY(15728640)")
    private byte[] contenu;
    private Long deposeParId;
    private Instant deposeLe = Instant.now();
}
