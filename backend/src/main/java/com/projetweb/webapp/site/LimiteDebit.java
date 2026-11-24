package com.projetweb.webapp.site;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation du débit (§5.2.2) sur les routes sensibles : connexion, inscription, mot de passe oublié, contact.
 * Fenêtre glissante d'une minute par adresse IP et par route.
 */
@Component
public class LimiteDebit extends OncePerRequestFilter {
    private static final Set<String> ROUTES = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/forgot",
            "/api/auth/reset", "/api/public/contact");
    private final int max;
    private final Map<String, Deque<Long>> fenetres = new ConcurrentHashMap<>();

    public LimiteDebit(@Value("${app.rate-limit.per-minute:10}") int max) { this.max = max; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return !"POST".equals(r.getMethod()) || !ROUTES.contains(r.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String cle = req.getRemoteAddr() + "|" + req.getRequestURI();
        long now = System.currentTimeMillis();
        Deque<Long> f = fenetres.computeIfAbsent(cle, k -> new ArrayDeque<>());
        boolean refuse;
        synchronized (f) {
            while (!f.isEmpty() && now - f.peekFirst() > 60_000) f.pollFirst();
            refuse = f.size() >= max;
            if (!refuse) f.addLast(now);
        }
        if (fenetres.size() > 10_000) fenetres.clear();
        if (refuse) {
            res.setStatus(429);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"message\":\"Trop de tentatives : réessayez dans une minute.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
