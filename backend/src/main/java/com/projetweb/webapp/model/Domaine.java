package com.projetweb.webapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "domaines")
@Getter @Setter @NoArgsConstructor
public class Domaine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    @Column(length = 4000)
    private String description;

    /** Responsable(s) de l'axe (§3.6). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "domaine_responsables", joinColumns = @JoinColumn(name = "domaine_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private java.util.List<Long> responsableIds = new java.util.ArrayList<>();

    public Domaine(String nom, String description) {
        this.nom = nom;
        this.description = description;
    }
}
