package com.projetweb.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

/**
 * Publication. Même entité pour le site public (Partie 1) et le formulaire F7 du rapport (Partie 2) :
 * une publication saisie une fois apparaît aux deux endroits (§15.5).
 */
@Entity
@Table(name = "publications")
@Getter @Setter @NoArgsConstructor
public class Publication extends FicheRapport {

    public enum Type { ARTICLE_JOURNAL, CONFERENCE_INTERNATIONALE, CONFERENCE_NATIONALE, CHAPITRE, RAPPORT_TECHNIQUE, AUTRE }
    /** Référentiel du classement de la venue (§3.7.2). */
    public enum SourceClassement { SCIMAGO, CORE, JCR, SNIP, AUTRE }
    public enum Indexation { WOS, SCOPUS, AUTRES, AUCUNE }

    @Column(nullable = false, length = 500)
    private String titre;

    @Column(length = 8000)
    private String resume;

    private Integer annee;
    private String doi;
    private String pdfUrl;

    @Enumerated(EnumType.STRING) @Column(length = 30)
    private Type type = Type.ARTICLE_JOURNAL;

    /** Titre du journal ou de la conférence : jamais tronqué dans le rapport. */
    @Column(length = 1000)
    private String venue;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Indexation indexation;

    /** Comptée dans II-1 « publications impactées » ; proposée par la table de correspondance. */
    private Boolean estImpactee;

    /** Liste complète et ordonnée des auteurs (internes et externes), telle qu'elle figure au rapport. */
    @Column(length = 3000)
    private String auteursTexte;

    // ── Partie 1 §3.7 : détails bibliographiques et classement de la venue ──
    /** Classement affiché en badge : Q1…Q4 (Scimago), A*, A, B, C (CORE) ou valeur libre. */
    @Column(length = 30)
    private String classement;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private SourceClassement sourceClassement;
    private Double facteurImpact;
    private Double snip;
    private Integer citations;
    @Column(length = 40) private String volume;
    @Column(length = 40) private String numero;
    @Column(length = 40) private String pages;
    @Column(length = 300) private String lieu;
    @Column(length = 100) private String pays;
    @Column(length = 300) private String editeur;
    @Column(length = 40) private String isbn;
    @Column(length = 300) private String institution;
    @Column(length = 300) private String comiteOrganisation;
    /** PDF déposé sur le site (table media). */
    private Long pdfMediaId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "publication_mots_cles", joinColumns = @JoinColumn(name = "publication_id"))
    @Column(name = "mot_cle")
    private List<String> motsCles = new ArrayList<>();

    /** Auteurs internes (membres du laboratoire). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "publication_chercheurs",
            joinColumns = @JoinColumn(name = "publication_id"),
            inverseJoinColumns = @JoinColumn(name = "chercheur_id"))
    @JsonIgnore
    private Set<Chercheur> chercheurs = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnore
    private Domaine domaine;

    /** Identifiants des auteurs internes, en lecture et en écriture pour les formulaires. */
    @Transient
    @JsonIgnore
    private List<Long> auteurIdsDemandes;

    @JsonProperty("auteurIds")
    public List<Long> getAuteurIds() { return chercheurs.stream().map(Chercheur::getId).toList(); }

    @JsonProperty("auteurIds")
    public void setAuteurIds(List<Long> ids) { this.auteurIdsDemandes = ids; }

    @Transient @JsonIgnore
    private Long domaineIdDemande;
    @Transient @JsonIgnore
    private boolean domaineIdFourni;

    @JsonProperty("domaineId")
    public Long getDomaineId() { return domaine == null ? null : domaine.getId(); }

    @JsonProperty("domaineId")
    public void setDomaineId(Long id) { this.domaineIdDemande = id; this.domaineIdFourni = true; }
}
