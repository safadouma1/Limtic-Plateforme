package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CampagneRepository extends JpaRepository<Campagne, Long> {
    Optional<Campagne> findByAnnee(Integer annee);
    List<Campagne> findAllByOrderByAnneeDesc();
}
