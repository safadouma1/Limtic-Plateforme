package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Role;
import com.projetweb.webapp.repository.ChercheurRepository;
import com.projetweb.webapp.repository.UtilisateurRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/** Gestion des comptes et des rôles (ADMIN uniquement, voir SecurityConfig). */
@RestController
@RequestMapping("/api/utilisateurs")
public class UtilisateurController {

    private final UtilisateurRepository users;
    private final ChercheurRepository chercheurs;

    public UtilisateurController(UtilisateurRepository users, ChercheurRepository chercheurs) {
        this.users = users; this.chercheurs = chercheurs;
    }

    @GetMapping
    public List<UserResponse> lister() {
        return users.findAll().stream().map(u -> UserResponse.of(u, null)).toList();
    }

    @PutMapping("/{id}/role")
    @Transactional
    public UserResponse changerRole(@PathVariable Long id, @Valid @RequestBody RoleUpdate req, Authentication auth) {
        var u = users.findById(id).orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        if (u.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas modifier votre propre rôle.");
        }
        var moi = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        // Seul le directeur (super-administrateur) attribue ou retire le rôle DIRECTEUR
        if ((req.role() == Role.DIRECTEUR || u.getRole() == Role.DIRECTEUR) && moi.getRole() != Role.DIRECTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le directeur peut attribuer ou retirer le rôle Directeur.");
        }
        u.setRole(req.role());
        return UserResponse.of(users.save(u), null);
    }

    /** Relie un compte à une fiche membre (nécessaire au rôle CHERCHEUR et à « Mon activité »). */
    @PutMapping("/{id}/chercheur")
    @Transactional
    public UserResponse lierChercheur(@PathVariable Long id, @RequestBody LienChercheur req) {
        var u = users.findById(id).orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        if (req.chercheurId() != null && !chercheurs.existsById(req.chercheurId())) {
            throw new NoSuchElementException("Fiche membre introuvable");
        }
        u.setChercheurId(req.chercheurId());
        return UserResponse.of(users.save(u), null);
    }

    /** Activation / désactivation d'un compte (§4.3.2) : un compte désactivé ne peut plus se connecter. */
    @PutMapping("/{id}/actif")
    @Transactional
    public UserResponse activer(@PathVariable Long id, @RequestBody ActifUpdate req, Authentication auth) {
        var u = users.findById(id).orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        if (u.getEmail().equalsIgnoreCase(auth.getName()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas désactiver votre propre compte.");
        var moi = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (u.getRole() == Role.DIRECTEUR && moi.getRole() != Role.DIRECTEUR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le directeur peut désactiver un compte Directeur.");
        u.setActif(req.actif());
        return UserResponse.of(users.save(u), null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id, Authentication auth) {
        var u = users.findById(id).orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        if (u.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte.");
        }
        var moi = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (u.getRole() == Role.DIRECTEUR && moi.getRole() != Role.DIRECTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le directeur peut supprimer un compte Directeur.");
        }
        users.delete(u);
    }
}
