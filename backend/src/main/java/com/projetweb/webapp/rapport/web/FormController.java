package com.projetweb.webapp.rapport.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.projetweb.webapp.model.FicheRapport;
import com.projetweb.webapp.model.StatutFiche;
import com.projetweb.webapp.rapport.service.FormService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** CRUD /api/forms/{categorie} et workflow (§17.7). Les droits sont vérifiés dans FormService. */
@RestController
@RequestMapping("/api/forms/{categorie}")
public class FormController {
    private final FormService forms;

    public FormController(FormService forms) { this.forms = forms; }

    @GetMapping
    public List<FicheRapport> lister(@PathVariable String categorie, @RequestParam(required = false) Integer annee,
                                     @RequestParam(required = false) StatutFiche statut) {
        return forms.lister(categorie, annee, statut);
    }

    @GetMapping("/{id}")
    public FicheRapport lire(@PathVariable String categorie, @PathVariable Long id) { return forms.lire(categorie, id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormService.Resultat creer(@PathVariable String categorie, @RequestBody JsonNode corps) { return forms.creer(categorie, corps); }

    @PutMapping("/{id}")
    public FormService.Resultat modifier(@PathVariable String categorie, @PathVariable Long id, @RequestBody JsonNode corps) {
        return forms.modifier(categorie, id, corps);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable String categorie, @PathVariable Long id) { forms.supprimer(categorie, id); }

    @PostMapping("/{id}/soumettre")
    public FormService.Resultat soumettre(@PathVariable String categorie, @PathVariable Long id) { return forms.soumettre(categorie, id); }

    @PostMapping("/{id}/valider")
    public FormService.Resultat valider(@PathVariable String categorie, @PathVariable Long id) { return forms.valider(categorie, id); }

    @PostMapping("/{id}/rejeter")
    public FormService.Resultat rejeter(@PathVariable String categorie, @PathVariable Long id, @RequestBody Map<String, String> corps) {
        return forms.rejeter(categorie, id, corps.get("motif"));
    }

    /** Consultation d'un CIN par un administrateur (journalisée). */
    @GetMapping("/{id}/cin")
    public Map<String, String> cin(@PathVariable String categorie, @PathVariable Long id) {
        String c = forms.cin(categorie, id);
        return Map.of("cin", c == null ? "" : c);
    }
}
