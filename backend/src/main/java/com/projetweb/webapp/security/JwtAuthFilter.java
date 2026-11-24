package com.projetweb.webapp.security;

import com.projetweb.webapp.model.Role;
import com.projetweb.webapp.repository.UtilisateurRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UtilisateurRepository users;

    public JwtAuthFilter(JwtService jwt, UtilisateurRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.lire(header.substring(7));
                // On relit l'utilisateur en base : un compte supprimé ou dont le rôle a changé est pris en compte.
                users.findByEmailIgnoreCase(claims.getSubject()).filter(x -> x.isActif()).ifPresent(u -> {
                    // Le directeur (super-administrateur) a aussi tous les droits d'administrateur
                    List<SimpleGrantedAuthority> roles = u.getRole() == Role.DIRECTEUR
                            ? List.of(new SimpleGrantedAuthority("ROLE_DIRECTEUR"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                            : List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
                    var auth = new UsernamePasswordAuthenticationToken(u.getEmail(), null, roles);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            } catch (JwtException | IllegalArgumentException ignored) {
                // token invalide ou expiré : la requête reste anonyme (401 si la route est protégée)
            }
        }
        chain.doFilter(req, res);
    }
}
