package com.projetweb.webapp.rapport.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Campagne annuelle du rapport (§16.1) : Préparation → Saisie ouverte → Revue → Clôturée. */
@Entity @Table(name = "campagnes")
@Getter @Setter @NoArgsConstructor
public class Campagne {
    public enum Statut { PREPARATION, SAISIE_OUVERTE, REVUE, CLOTUREE }
    /** Règle de calcul de III-3 (point ouvert §15.6). */
    public enum RegleIII3 { CONVENTIONS_ET_PROJETS, CONVENTIONS_ET_PROJETS_INTERNATIONAUX, CONVENTIONS_SEULEMENT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Integer annee;

    @Enumerated(EnumType.STRING) @Column(length = 20, nullable = false)
    private Statut statut = Statut.PREPARATION;

    private LocalDate dateDebutSaisie;
    private LocalDate dateEcheance;
    private LocalDate dateArreteBudget;

    // Paramètres de calcul (§15.1 et §15.6)
    /** I-3 : compter les doctorants ayant soutenu pendant l'année. */
    private boolean inclureDoctorantsSoutenus = true;
    /** Contrôle bloquant paramétrable : enregistrements encore « soumis » ou « brouillon ». */
    private boolean bloquerNonValides = true;
    /** II-3 : ajouter les publications F7 non impactées aux chapitres (à confirmer, §15.6). */
    private boolean inclureNonImpacteesII3 = false;
    @Enumerated(EnumType.STRING) @Column(length = 50)
    private RegleIII3 regleIII3 = RegleIII3.CONVENTIONS_ET_PROJETS;

    private Instant clotureLe;
    private String cloturePar;
    /** Nombre de clôtures : la n-ième clôture produit la version finale n. */
    private int nbClotures;

    /** Copie immuable (chiffrée) des données de l'année, prise à la clôture. */
    @JsonIgnore
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY) @Column(length = 50_000_000)
    private byte[] snapshot;

    public boolean isSnapshotDisponible() { return snapshot != null; }
}
