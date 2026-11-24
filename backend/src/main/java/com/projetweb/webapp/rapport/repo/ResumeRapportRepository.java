package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ResumeRapportRepository extends JpaRepository<ResumeRapport, Long> {
    Optional<ResumeRapport> findByAnnee(Integer annee);
}
