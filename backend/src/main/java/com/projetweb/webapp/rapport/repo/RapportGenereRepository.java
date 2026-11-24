package com.projetweb.webapp.rapport.repo;

import com.projetweb.webapp.rapport.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RapportGenereRepository extends JpaRepository<RapportGenere, Long> {
    List<RapportGenere> findByAnneeOrderByVersionDesc(Integer annee);
    List<RapportGenere> findAllByOrderByAnneeDescVersionDesc();
}
