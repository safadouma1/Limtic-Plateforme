package com.projetweb.webapp.rapport.service;

import com.projetweb.webapp.rapport.model.AuditLog;
import com.projetweb.webapp.rapport.repo.AuditLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Journal d'audit (§18) : chaque génération, export, téléchargement, réouverture est tracé. */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(String action, String details) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        log(auth == null ? "système" : auth.getName(), action, details);
    }

    public void log(String utilisateur, String action, String details) {
        AuditLog a = new AuditLog();
        a.setDate(Instant.now());
        a.setUtilisateur(utilisateur);
        a.setAction(action);
        a.setDetails(details == null ? null : details.length() > 2000 ? details.substring(0, 2000) : details);
        repo.save(a);
    }
}
