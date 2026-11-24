package com.projetweb.webapp.site;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Message envoyé depuis le formulaire de contact (§3.9). */
@Entity @Table(name = "contact_messages")
@Getter @Setter @NoArgsConstructor
public class ContactMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 120, nullable = false) private String nom;
    @Column(length = 200, nullable = false) private String email;
    @Column(length = 200, nullable = false) private String sujet;
    @Column(length = 5000, nullable = false) private String message;
    private boolean lu;
    private Instant creeLe = Instant.now();
}
