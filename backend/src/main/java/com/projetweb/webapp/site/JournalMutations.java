package com.projetweb.webapp.site;

import com.projetweb.webapp.rapport.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/**
 * Journal d'audit du back-office (§4.1) : toute création, modification ou suppression réussie
 * sur l'API de gestion est tracée (utilisateur, méthode, ressource). Les routes publiques et l'IA sont exclues.
 */
@Configuration
public class JournalMutations implements WebMvcConfigurer, HandlerInterceptor {
    private static final Set<String> METHODES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AuditService audit;

    public JournalMutations(AuditService audit) { this.audit = audit; }

    @Override
    public void addInterceptors(InterceptorRegistry r) {
        r.addInterceptor(this).addPathPatterns("/api/chercheurs/**", "/api/publications/**", "/api/domaines/**",
                "/api/utilisateurs/**", "/api/forms/**", "/api/media/**", "/api/me/fiche");
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        if (!METHODES.contains(req.getMethod()) || res.getStatus() >= 400) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String action = switch (req.getMethod()) { case "POST" -> "AJOUT"; case "DELETE" -> "SUPPRESSION"; default -> "MODIFICATION"; };
        audit.log(auth == null ? "anonyme" : auth.getName(), action, req.getMethod() + " " + req.getRequestURI());
    }
}
