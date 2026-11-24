package com.projetweb.webapp.rapport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** F18 – Une ligne (rubrique) du reliquat de budget. */
@Entity @Table(name = "lignes_budget", uniqueConstraints = @UniqueConstraint(columnNames = {"annee", "rubrique"}))
@Getter @Setter @NoArgsConstructor
public class LigneBudget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer annee;
    private int ordre;
    private String rubrique;
    @Column(precision = 14, scale = 3)
    private BigDecimal creditsDisponibles;
    @Column(precision = 14, scale = 3)
    private BigDecimal creditsEngages;

    public BigDecimal getReliquat() {
        if (creditsDisponibles == null && creditsEngages == null) return null;
        BigDecimal d = creditsDisponibles == null ? BigDecimal.ZERO : creditsDisponibles;
        BigDecimal e = creditsEngages == null ? BigDecimal.ZERO : creditsEngages;
        return d.subtract(e);
    }
}
