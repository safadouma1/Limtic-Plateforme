package com.projetweb.webapp.repository;

import com.projetweb.webapp.model.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PublicationRepository extends JpaRepository<Publication, Long> {

    @Query("select p from Publication p join p.chercheurs c where c.id = :chercheurId order by p.annee desc")
    List<Publication> findByChercheur(@Param("chercheurId") Long chercheurId);

    @Query("select count(p) from Publication p join p.chercheurs c where c.id = :chercheurId")
    long countByChercheur(@Param("chercheurId") Long chercheurId);

    @Query("select x from Publication x where x.domaine.id = :domaineId")
    List<Publication> findByDomaineId(@Param("domaineId") Long domaineId);

    @Query("select count(x) from Publication x where x.domaine.id = :domaineId")
    long countByDomaineId(@Param("domaineId") Long domaineId);
}
