package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProductionRepository extends JpaRepository<Production, Long> {
    List<Production> findByNature(Production.Nature nature);
}
