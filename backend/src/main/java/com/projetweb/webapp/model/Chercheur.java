package com.projetweb.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Membre du laboratoire. Partie 1 : fiche publique du chercheur.
 * Partie 2 : formulaires F3 (enseignant-chercheur permanent, corps A/B) et F5 (cadre équivalent assistant).
 * Le corps A/B n'est pas stocké : il est déduit du grade par la table de correspondance.
 */
@Entity
@Table(name = "chercheurs")
@Getter @Setter @NoArgsConstructor
public class Chercheur extends PersonneRapport {

    public enum Categorie { ENSEIGNANT_CHERCHEUR, CADRE_ASSIMILE }
    public enum StatutMembre { ACTIF, RETRAITE, MUTE, DEMISSIONNAIRE, DECEDE }

    @Column(unique = true)
    private String email;

    private String specialite;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnore
    private Domaine domaine;

    @Transient @JsonIgnore
    private Long domaineIdDemande;
    @Transient @JsonIgnore
    private boolean domaineIdFourni;

    @JsonProperty("domaineId")
    public Long getDomaineId() { return domaine == null ? null : domaine.getId(); }

    @JsonProperty("domaineId")
    public void setDomaineId(Long id) { this.domaineIdDemande = id; this.domaineIdFourni = true; }

    @Enumerated(EnumType.STRING) @Column(length = 30)
    private Categorie categorie = Categorie.ENSEIGNANT_CHERCHEUR;

    private String grade;
    private String fonction;
    private LocalDate dateEntree;
    private LocalDate dateSortie;

    @Enumerated(EnumType.STRING) @Column(length = 20)
    private StatutMembre statutMembre = StatutMembre.ACTIF;

    // ── Partie 1 §3.3.2 : page personnelle ──
    @Column(length = 100) private String bureau;
    @Column(length = 60) private String telephone;
    @Column(length = 60) private String orcid;
    @Column(length = 500) private String googleScholarUrl;
    @Column(length = 500) private String researchGateUrl;
    @Column(length = 500) private String linkedinUrl;
    @Column(length = 4000) private String biographie;

    /** Actif au moins un jour de l'année N (§14 F3). Sans date d'entrée : présent depuis avant N. */
    public boolean actifEn(int annee) {
        boolean entre = dateEntree == null || dateEntree.getYear() <= annee;
        boolean pasSorti = dateSortie == null || dateSortie.getYear() >= annee;
        return entre && pasSorti;
    }
}
