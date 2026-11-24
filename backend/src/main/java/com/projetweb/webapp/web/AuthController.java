package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Role;
import com.projetweb.webapp.model.Utilisateur;
import com.projetweb.webapp.repository.UtilisateurRepository;
import com.projetweb.webapp.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UtilisateurRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final com.projetweb.webapp.site.Reinitialisation reinitialisation;

    public AuthController(UtilisateurRepository users, PasswordEncoder encoder, JwtService jwt,
                          com.projetweb.webapp.site.Reinitialisation reinitialisation) {
        this.reinitialisation = reinitialisation;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req) {
        Utilisateur u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getMotDePasse()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect."));
        if (!u.isActif()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte est désactivé : contactez l'administrateur du laboratoire.");
        return UserResponse.of(u, jwt.generer(u));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email.");
        }
        Utilisateur u = new Utilisateur();
        u.setNom(req.nom().trim());
        u.setPrenom(req.prenom().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setMotDePasse(encoder.encode(req.password()));
        u.setRole(Role.UTILISATEUR);   // un nouvel inscrit n'est jamais admin
        users.save(u);
        return UserResponse.of(u, jwt.generer(u));
    }

    /**
     * Mot de passe oublié (§4.1) : la réponse est la même que l'adresse existe ou non (pas d'énumération des comptes).
     * Le lien est envoyé par e-mail si un serveur SMTP est configuré, sinon il est écrit dans le journal du serveur.
     */
    @PostMapping("/forgot")
    public java.util.Map<String, String> oubli(@Valid @RequestBody ForgotRequest req) {
        reinitialisation.demander(req.email().trim());
        return java.util.Map.of("message", "Si un compte existe pour cette adresse, un lien de réinitialisation valable 30 minutes vient d'être envoyé.");
    }

    @PostMapping("/reset")
    public java.util.Map<String, String> reinitialiser(@Valid @RequestBody ResetRequest req) {
        reinitialisation.appliquer(req.jeton(), req.password());
        return java.util.Map.of("message", "Mot de passe modifié : vous pouvez vous connecter.");
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        Utilisateur u = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        return UserResponse.of(u, null);
    }
}
