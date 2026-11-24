package com.projetweb.webapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "utilisateurs")
@Getter @Setter @NoArgsConstructor
public class Utilisateur {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.UTILISATEUR;

    /** Fiche membre reliée au compte (rôle CHERCHEUR) : sert à « Mon activité » et aux droits de saisie. */
    private Long chercheurId;

    /** Compte désactivé : connexion refusée (§4.3.2). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean actif = true;

    /** Réinitialisation du mot de passe : empreinte du jeton envoyé par e-mail et échéance. */
    @Column(length = 64)
    private String resetEmpreinte;
    private java.time.Instant resetExpire;
}
