package com.projetweb.webapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Champs communs à tous les formulaires du rapport (§14.1) : année de référence, statut du workflow,
 * traçabilité de la saisie et de la validation. Les champs de workflow ne sont jamais modifiables
 * par le corps d'une requête (lecture seule en JSON) : ils changent uniquement par les actions
 * soumettre / valider / rejeter.
 */
@MappedSuperclass
@Getter @Setter
public abstract class FicheRapport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    private Integer anneeReference;

    @Enumerated(EnumType.STRING) @Column(length = 20)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private StatutFiche statut = StatutFiche.VALIDE;

    @Column(length = 1000)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String motifRejet;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long creeParId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String creePar;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant creeLe;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant modifieLe;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String validePar;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant valideLe;

    @PrePersist
    void avantCreation() {
        if (creeLe == null) creeLe = Instant.now();
        modifieLe = Instant.now();
        if (statut == null) statut = StatutFiche.BROUILLON;
        apresModification();
    }

    @PreUpdate
    void avantModification() {
        modifieLe = Instant.now();
        apresModification();
    }

    /** Point d'extension (ex. calcul de l'empreinte du CIN). */
    protected void apresModification() {}
}
