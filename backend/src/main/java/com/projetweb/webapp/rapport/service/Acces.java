package com.projetweb.webapp.rapport.service;

import com.projetweb.webapp.model.Role;
import com.projetweb.webapp.model.Utilisateur;
import com.projetweb.webapp.repository.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Droits du module Rapport d'activité (cahier des charges, chapitre 12).
 * Visiteur, utilisateur simple et modérateur : aucun accès.
 */
@Component
public class Acces {
    private final UtilisateurRepository users;

    public Acces(UtilisateurRepository users) { this.users = users; }

    public Utilisateur courant() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public static boolean directeur(Utilisateur u) { return u.getRole() == Role.DIRECTEUR; }
    public static boolean admin(Utilisateur u) { return u.getRole() == Role.ADMIN || u.getRole() == Role.DIRECTEUR; }
    public static boolean chercheur(Utilisateur u) { return u.getRole() == Role.CHERCHEUR; }

    /** Accès au module (directeur, administrateur, chercheur). */
    public Utilisateur module() {
        Utilisateur u = courant();
        if (!admin(u) && !chercheur(u)) refuser("Le module Rapport d'activité est réservé aux membres du laboratoire.");
        return u;
    }

    public Utilisateur admin() {
        Utilisateur u = courant();
        if (!admin(u)) refuser("Action réservée aux administrateurs.");
        return u;
    }

    public Utilisateur directeur() {
        Utilisateur u = courant();
        if (!directeur(u)) refuser("Action réservée au directeur du laboratoire (super-administrateur).");
        return u;
    }

    public static void refuser(String message) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
