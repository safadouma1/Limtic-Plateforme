package com.projetweb.webapp.site;

import com.projetweb.webapp.model.Utilisateur;
import com.projetweb.webapp.repository.UtilisateurRepository;
import com.projetweb.webapp.securite.CinCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Réinitialisation du mot de passe par lien à usage unique (30 minutes). */
@Service
public class Reinitialisation {
    private static final Logger log = LoggerFactory.getLogger(Reinitialisation.class);
    private final UtilisateurRepository users;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mail;
    private final String frontUrl;
    private final String expediteur;
    private final boolean smtpConfigure;
    private final SecureRandom aleatoire = new SecureRandom();

    public Reinitialisation(UtilisateurRepository users, PasswordEncoder encoder, ObjectProvider<JavaMailSender> mail,
                            @Value("${app.front-url:http://localhost:4200}") String frontUrl,
                            @Value("${app.mail.from:no-reply@limtic.local}") String expediteur,
                            @Value("${spring.mail.host:}") String smtpHost) {
        this.users = users; this.encoder = encoder; this.mail = mail;
        this.frontUrl = frontUrl.replaceAll("/$", ""); this.expediteur = expediteur;
        this.smtpConfigure = !smtpHost.isBlank();
    }

    @Transactional
    public void demander(String email) {
        users.findByEmailIgnoreCase(email).filter(Utilisateur::isActif).ifPresent(u -> {
            byte[] b = new byte[32];
            aleatoire.nextBytes(b);
            String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
            u.setResetEmpreinte(empreinte(jeton));
            u.setResetExpire(Instant.now().plus(Duration.ofMinutes(30)));
            users.save(u);
            String lien = frontUrl + "/auth/reinitialiser?jeton=" + jeton;
            if (smtpConfigure && mail.getIfAvailable() != null) {
                try {
                    SimpleMailMessage m = new SimpleMailMessage();
                    m.setFrom(expediteur);
                    m.setTo(u.getEmail());
                    m.setSubject("LIMTIC – réinitialisation de votre mot de passe");
                    m.setText("Bonjour " + u.getPrenom() + ",\n\nPour choisir un nouveau mot de passe, ouvrez ce lien (valable 30 minutes) :\n"
                            + lien + "\n\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message.\n\nLIMTIC");
                    mail.getObject().send(m);
                    return;
                } catch (Exception e) {
                    log.warn("Envoi de l'e-mail de réinitialisation impossible : {}", e.getMessage());
                }
            }
            // Sans serveur SMTP (développement) : le lien est écrit dans le journal du serveur.
            log.info("Lien de réinitialisation pour {} (SMTP non configuré) : {}", u.getEmail(), lien);
        });
    }

    @Transactional
    public void appliquer(String jeton, String motDePasse) {
        String e = empreinte(jeton);
        Utilisateur u = users.findAll().stream().filter(x -> e.equals(x.getResetEmpreinte())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien invalide ou déjà utilisé."));
        if (u.getResetExpire() == null || u.getResetExpire().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien expiré : refaites une demande.");
        u.setMotDePasse(encoder.encode(motDePasse));
        u.setResetEmpreinte(null);
        u.setResetExpire(null);
        users.save(u);
    }

    private static String empreinte(String jeton) {
        return HexFormat.of().formatHex(CinCrypto.sha256(jeton.getBytes(StandardCharsets.UTF_8)));
    }
}
