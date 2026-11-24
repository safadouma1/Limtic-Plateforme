package com.projetweb.webapp.site;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Paramètres généraux du site (§4.3.6) et contenu de la page du directeur (§3.2). Ligne unique (id = 1). */
@Entity @Table(name = "site_parametres")
@Getter @Setter @NoArgsConstructor
public class SiteParametres {
    @Id private Long id = 1L;

    @Column(length = 120) private String nomCourt = "LIMTIC";
    @Column(length = 400) private String nomComplet;
    @Column(length = 4000) private String presentation;
    @Column(length = 20) private String couleurPrincipale = "#5b8cff";

    /** Mot du directeur (texte riche assaini). */
    @Column(length = 20000) private String motDirecteur;
    @Column(length = 200) private String titreDirecteur;
    @Column(length = 500) private String axesPrioritairesDirecteur;

    @Column(length = 500) private String adresse;
    @Column(length = 60) private String telephone;
    @Column(length = 60) private String fax;
    @Column(length = 200) private String email;
    /** Requête Google Maps (adresse ou coordonnées) pour la carte intégrée. */
    @Column(length = 300) private String carteRequete;
    @Column(length = 500) private String facebookUrl;
    @Column(length = 500) private String linkedinUrl;
    @Column(length = 500) private String youtubeUrl;
    @Column(length = 500) private String siteIsiUrl;

    @Column(length = 200) private String seoTitre;
    @Column(length = 500) private String seoDescription;
    @Column(length = 500) private String seoMotsCles;
}
