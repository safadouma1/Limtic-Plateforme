package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface GabaritRapportRepository extends JpaRepository<GabaritRapport, Long> {
    Optional<GabaritRapport> findFirstByActifTrue();
}
