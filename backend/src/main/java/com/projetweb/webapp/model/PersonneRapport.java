package com.projetweb.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projetweb.webapp.securite.CinConverter;
import com.projetweb.webapp.securite.CinCrypto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Personne citée dans le rapport (membre, doctorant, mastérien).
 * Le numéro de CIN est chiffré au repos et n'est JAMAIS renvoyé en JSON (écriture seule) :
 * un administrateur le consulte par une route dédiée et journalisée.
 */
@MappedSuperclass
@Getter @Setter
public abstract class PersonneRapport extends FicheRapport {
    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Convert(converter = CinConverter.class)
    @Column(length = 200)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String cin;

    @JsonIgnore
    @Column(length = 64)
    private String cinEmpreinte;

    /** Pièce d'identité étrangère (passeport) : pas de CIN tunisien exigé. */
    private boolean etranger;

    @Column(length = 300)
    private String etablissement;

    @Column(length = 300)
    private String universite;

    /** Photo de profil (table media), affichée sur le site public. */
    private Long photoMediaId;

    /** Une valeur vide laisse le CIN inchangé (le formulaire ne réaffiche jamais le CIN). */
    public void setCin(String cin) {
        if (cin != null && !cin.isBlank()) this.cin = cin.replaceAll("\\s", "");
    }

    /** Effacement explicite (pièce étrangère, correction). */
    public void effacerCin() { this.cin = null; this.cinEmpreinte = null; }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isCinRenseigne() { return cin != null && !cin.isBlank(); }

    @JsonIgnore
    public String getNomComplet() { return nom.toUpperCase() + " " + prenom; }

    @Override
    protected void apresModification() {
        cinEmpreinte = cin == null || cin.isBlank() ? null : CinCrypto.get().empreinte(cin);
    }
}
