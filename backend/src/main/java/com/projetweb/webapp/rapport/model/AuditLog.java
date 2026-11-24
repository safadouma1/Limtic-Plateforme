package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Journal d'audit : générations, exports, téléchargements, consultations de CIN, réouvertures… */
@Entity @Table(name = "journal_audit")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant date;
    private String utilisateur;
    @Column(length = 50)
    private String action;
    @Column(length = 2000)
    private String details;
}
