package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LigneBudgetRepository extends JpaRepository<LigneBudget, Long> {
    List<LigneBudget> findByAnneeOrderByOrdre(Integer annee);
}
