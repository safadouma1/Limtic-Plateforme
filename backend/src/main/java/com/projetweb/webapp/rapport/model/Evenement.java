package com.projetweb.webapp.rapport.model;

import com.projetweb.webapp.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Événement scientifique ; ceux « organisés par le laboratoire » alimentent F15 / III-1. */
@Entity @Table(name = "evenements")
@Getter @Setter @NoArgsConstructor
public class Evenement extends FicheRapport {
    public enum Type { SEMINAIRE, JOURNEE_RECHERCHE, ATELIER, CONFERENCE }

    @Column(length = 1000)
    private String titre;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private Type type = Type.SEMINAIRE;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    /** Année de l'événement (déduite de la date de début si elle est renseignée). */
    private Integer annee;
    private String lieu;
    private String organisateurs = "LIMTIC";
    private boolean organiseParLabo = true;
    @Column(length = 20000)
    private String description;

    // ── Partie 1 §3.8 : page publique de l'événement ──
    /** Programme scientifique (texte riche assaini) et/ou PDF téléchargeable. */
    @Column(length = 20000)
    private String programme;
    private Long programmeMediaId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evenement_intervenants", joinColumns = @JoinColumn(name = "evenement_id"))
    @OrderColumn(name = "ordre")
    private java.util.List<Intervenant> intervenants = new java.util.ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evenement_photos", joinColumns = @JoinColumn(name = "evenement_id"))
    @OrderColumn(name = "ordre")
    private java.util.List<Photo> photos = new java.util.ArrayList<>();

    @Embeddable @Getter @Setter @NoArgsConstructor
    public static class Intervenant {
        @Column(length = 200) private String nom;
        @Column(length = 300) private String affiliation;
        @Column(length = 500) private String titreIntervention;
    }

    @Embeddable @Getter @Setter @NoArgsConstructor
    public static class Photo {
        private Long mediaId;
        @Column(length = 500) private String legende;
    }

    /** À venir / en cours / terminé, calculé chaque jour à partir des dates (archivage automatique). */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public String getEtat() {
        LocalDate auj = LocalDate.now(java.time.ZoneId.of("Africa/Tunis"));
        if (dateDebut == null) return annee != null && annee < auj.getYear() ? "TERMINE" : "A_VENIR";
        LocalDate fin = dateFin != null ? dateFin : dateDebut;
        if (auj.isBefore(dateDebut)) return "A_VENIR";
        return auj.isAfter(fin) ? "TERMINE" : "EN_COURS";
    }

    @Override
    protected void apresModification() {
        programme = com.projetweb.webapp.site.Html.nettoyer(programme);
        if (dateDebut != null) annee = dateDebut.getYear();
    }

    public Integer anneeEffective() { return dateDebut != null ? Integer.valueOf(dateDebut.getYear()) : annee; }
}
