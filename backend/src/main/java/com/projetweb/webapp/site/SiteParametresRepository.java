package com.projetweb.webapp.site;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteParametresRepository extends JpaRepository<SiteParametres, Long> {
    default SiteParametres actuels() { return findById(1L).orElseGet(() -> save(new SiteParametres())); }
}
