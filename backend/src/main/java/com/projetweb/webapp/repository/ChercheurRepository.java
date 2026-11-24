package com.projetweb.webapp.repository;

import com.projetweb.webapp.model.Chercheur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChercheurRepository extends JpaRepository<Chercheur, Long> {

    @Query("select x from Chercheur x where x.domaine.id = :domaineId")
    List<Chercheur> findByDomaineId(@Param("domaineId") Long domaineId);

    @Query("""
           select c from Chercheur c
           where lower(c.nom) like lower(concat('%', :q, '%'))
              or lower(c.prenom) like lower(concat('%', :q, '%'))
              or lower(concat(c.prenom, ' ', c.nom)) like lower(concat('%', :q, '%'))
           """)
    List<Chercheur> rechercher(@Param("q") String q);

    @Query("select count(x) from Chercheur x where x.domaine.id = :domaineId")
    long countByDomaineId(@Param("domaineId") Long domaineId);

    boolean existsByEmailIgnoreCase(String email);
}
