package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

/**
 * F8 ouvrage, F9 chapitre d'ouvrage, F10 brevet, F11 obtention végétale :
 * une seule table, la nature distingue le formulaire.
 */
@Entity @Table(name = "productions")
@Getter @Setter @NoArgsConstructor
public class Production extends FicheRapport {
    public enum Nature { OUVRAGE, CHAPITRE, BREVET, OBTENTION_VEGETALE }
    public enum TypeBrevet { INNORPI, PCT, AUTRE }

    @Enumerated(EnumType.STRING) @Column(length = 30, nullable = false)
    private Nature nature;

    @Column(length = 1000)
    private String titre;
    private Integer annee;
    private String editeur;
    @Column(length = 1000)
    private String edition;
    @Column(length = 3000)
    private String auteursTexte;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "production_auteurs", joinColumns = @JoinColumn(name = "fiche_id"))
    @Column(name = "chercheur_id")
    @OrderColumn(name = "ordre")
    private List<Long> auteurIds = new ArrayList<>();
    private String isbn;
    private String pages;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private TypeBrevet typeBrevet;
    private String reference;
    /** Brevet : déposé ou délivré. */
    private String etat;
}
