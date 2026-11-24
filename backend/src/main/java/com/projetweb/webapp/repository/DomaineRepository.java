package com.projetweb.webapp.repository;

import com.projetweb.webapp.model.Domaine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomaineRepository extends JpaRepository<Domaine, Long> {
    boolean existsByNomIgnoreCase(String nom);
}
