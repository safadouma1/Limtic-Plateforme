package com.projetweb.webapp.rapport.web;

import com.projetweb.webapp.rapport.service.Acces;
import com.projetweb.webapp.rapport.service.ImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Import CSV / Excel avec compte rendu ligne par ligne (§17.8). Administrateurs uniquement. */
@RestController
@RequestMapping("/api/import")
public class ImportController {
    private final ImportService imports;
    private final Acces acces;

    public ImportController(ImportService imports, Acces acces) { this.imports = imports; this.acces = acces; }

    @GetMapping("/categories")
    public List<String> categories() { acces.admin(); return ImportService.CATEGORIES; }

    @PostMapping("/{categorie}")
    public ImportService.CompteRendu importer(@PathVariable String categorie, @RequestParam("fichier") MultipartFile fichier,
                                              @RequestParam(required = false) Integer annee) throws IOException {
        var u = acces.admin();
        return imports.importer(categorie, fichier.getOriginalFilename(), fichier.getBytes(), annee, u.getEmail());
    }

    /** Recharge le jeu d'essai du rapport 2025 (CIN fictifs) : les doublons sont signalés, rien n'est écrasé. */
    @PostMapping("/jeu-essai-2025")
    public List<ImportService.CompteRendu> jeuEssai() {
        var u = acces.directeur();
        return imports.importerJeuEssai(u.getEmail());
    }
}
