package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RegleCorrespondanceRepository extends JpaRepository<RegleCorrespondance, Long> {
    List<RegleCorrespondance> findByTableCode(RegleCorrespondance.Table t);
}
