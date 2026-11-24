package com.projetweb.webapp.rapport.web;

import com.projetweb.webapp.model.Chercheur;
import com.projetweb.webapp.model.Utilisateur;
import com.projetweb.webapp.rapport.generation.CsvRenderer;
import com.projetweb.webapp.rapport.generation.Rendu;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.*;
import com.projetweb.webapp.rapport.service.RapportData.*;
import com.projetweb.webapp.repository.ChercheurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** API du module Rapport d'activité (§17.7). */
@RestController
@RequestMapping("/api/report")
public class RapportController {

    private final CampagneService campagnes;
    private final CampagneRepository campagneRepo;
    private final RapportService rapports;
    private final GenerationService generation;
    private final RapportGenereRepository versions;
    private final TelechargementRapportRepository telechargements;
    private final AuditLogRepository journal;
    private final StructureIdentiteRepository identites;
    private final ResumeRapportRepository resumes;
    private final LigneBudgetRepository budgets;
    private final Correspondances corr;
    private final Gabarit gabarit;
    private final ChercheurRepository membres;
    private final Acces acces;
    private final AuditService audit;
    private final String urlPublique;

    public RapportController(CampagneService campagnes, CampagneRepository campagneRepo, RapportService rapports,
                             GenerationService generation, RapportGenereRepository versions,
                             TelechargementRapportRepository telechargements, AuditLogRepository journal,
                             StructureIdentiteRepository identites, ResumeRapportRepository resumes, LigneBudgetRepository budgets,
                             Correspondances corr, Gabarit gabarit, ChercheurRepository membres, Acces acces, AuditService audit,
                             @Value("${app.public-url:}") String urlPublique) {
        this.campagnes = campagnes; this.campagneRepo = campagneRepo; this.rapports = rapports; this.generation = generation;
        this.versions = versions; this.telechargements = telechargements; this.journal = journal; this.identites = identites;
        this.resumes = resumes; this.budgets = budgets; this.corr = corr; this.gabarit = gabarit; this.membres = membres;
        this.acces = acces; this.audit = audit; this.urlPublique = urlPublique;
    }

    // ── Campagnes ───────────────────────────────────────────────

    @GetMapping("/campaigns")
    public List<Campagne> campagnes() { acces.module(); return campagneRepo.findAllByOrderByAnneeDesc(); }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public Campagne ouvrir(@RequestBody CampagneService.Creation req) { return campagnes.creer(req); }

    @PatchMapping("/campaigns/{annee}/status")
    public Campagne statut(@PathVariable int annee, @RequestBody Map<String, String> corps) {
        Campagne.Statut s;
        try { s = Campagne.Statut.valueOf(corps.getOrDefault("statut", "")); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu."); }
        return campagnes.changerStatut(annee, s);
    }

    @PutMapping("/campaigns/{annee}/parametres")
    public Campagne parametres(@PathVariable int annee, @RequestBody Campagne p) { return campagnes.parametres(annee, p); }

    @GetMapping("/{annee}/completude")
    public Map<String, Object> completude(@PathVariable int annee) { return campagnes.completude(annee); }

    // ── Récapitulatif, contrôles, aperçu ────────────────────────

    @GetMapping("/{annee}/summary")
    public Map<String, Object> recapitulatif(@PathVariable int annee) {
        acces.admin();
        RapportData d = rapports.construire(annee, false);
        List<Map<String, Object>> groupes = new ArrayList<>();
        for (Groupe g : d.groupes()) {
            List<Map<String, Object>> lignes = new ArrayList<>();
            for (Section s : g.sections()) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("numero", s.numero()); l.put("code", s.code()); l.put("libelle", s.libelleRecap());
                l.put("valeur", s.valeur()); l.put("valeurN1", s.valeurN1());
                lignes.add(l);
            }
            groupes.add(Map.of("numero", g.numero(), "titre", g.titre(), "rubriques", lignes));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("annee", annee);
        r.put("groupes", groupes);
        r.put("anneePrecedenteDisponible", d.anneePrecedenteDisponible());
        r.put("nbBloquants", d.nbBloquants());
        r.put("nbAvertissements", d.controles().size() - d.nbBloquants());
        return r;
    }

    @GetMapping("/{annee}/checks")
    public List<Controle> controles(@PathVariable int annee) {
        acces.admin();
        return rapports.construire(annee, false).controles();
    }

    /** Aperçu web complet. Les CIN y sont masqués (seuls les fichiers générés « avec CIN » les contiennent). */
    @GetMapping("/{annee}/preview")
    public RapportData apercu(@PathVariable int annee, @RequestParam(defaultValue = "false") boolean inclureSoumis) {
        acces.admin();
        return masquer(rapports.construire(annee, inclureSoumis));
    }

    private static RapportData masquer(RapportData d) {
        List<Groupe> gs = d.groupes().stream().map(g -> new Groupe(g.numero(), g.titre(), g.sections().stream().map(s -> {
            if (s.colonneCin() < 0) return s;
            List<List<String>> l = s.lignes().stream().map(row -> {
                List<String> c = new ArrayList<>(row);
                String v = c.get(s.colonneCin());
                c.set(s.colonneCin(), Texte.vide(v) ? "" : "••••••" + v.substring(Math.max(0, v.length() - 2)));
                return (List<String>) c;
            }).toList();
            return new Section(s.code(), s.numero(), s.titre(), s.libelleRecap(), s.colonnes(), l, s.colonneCin(), s.libelleTotal(), s.valeur(), s.valeurN1(), s.ligneTotal());
        }).toList())).toList();
        return new RapportData(d.annee(), d.gabaritVersion(), d.entete(), d.titre(), d.dateArrete(), d.identite(), d.resume(), gs,
                d.controles(), d.calculeLe(), d.inclureSoumis(), d.anneePrecedenteDisponible());
    }

    /** Export d'un tableau en CSV (sans CIN), journalisé. */
    @GetMapping(value = "/{annee}/tables/{numero}.csv")
    public ResponseEntity<byte[]> tableau(@PathVariable int annee, @PathVariable String numero) {
        Utilisateur u = acces.admin();
        RapportData d = rapports.construire(annee, false);
        Section s = d.groupes().stream().flatMap(g -> g.sections().stream())
                .filter(x -> x.numero().equalsIgnoreCase(numero) || x.code().equalsIgnoreCase(numero)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Tableau inconnu : " + numero));
        byte[] csv = CsvRenderer.tableau(s, new Rendu(true, false, "", false, null));
        audit.log(u.getEmail(), "EXPORT_CSV", "Tableau " + s.numero() + " (" + annee + ")");
        return fichier(csv, "LIMTIC_" + annee + "_" + s.numero() + "_" + s.code().toLowerCase() + ".csv", "text/csv; charset=UTF-8");
    }

    // ── Génération et téléchargement ────────────────────────────

    @PostMapping("/{annee}/generate")
    public RapportGenere generer(@PathVariable int annee, @RequestParam(name = "final", defaultValue = "false") boolean finale,
                                 @RequestParam(defaultValue = "false") boolean withCin,
                                 @RequestParam(defaultValue = "false") boolean includeSoumis) {
        return generation.generer(annee, finale, withCin, includeSoumis);
    }

    @GetMapping("/{annee}/versions")
    public List<RapportGenere> versions(@PathVariable int annee) { acces.admin(); return versions.findByAnneeOrderByVersionDesc(annee); }

    @GetMapping("/versions")
    public List<RapportGenere> toutesVersions() { acces.admin(); return versions.findAllByOrderByAnneeDescVersionDesc(); }

    @GetMapping("/files/{versionId}/download")
    public GenerationService.Lien lien(@PathVariable Long versionId, @RequestParam(defaultValue = "docx") String format,
                                      @RequestParam(required = false) String tableau) {
        return generation.lien(versionId, format, tableau);
    }

    /** Lien signé à durée limitée : seule route du module accessible sans en-tête Authorization. */
    @GetMapping("/telechargement/{token}")
    public ResponseEntity<byte[]> telecharger(@PathVariable String token) {
        GenerationService.Telechargement t = generation.telecharger(token);
        String type = switch (t.fichier().getFormat()) {
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case PDF -> "application/pdf";
            case ZIP -> "application/zip";
            case CSV -> "text/csv; charset=UTF-8";
        };
        ResponseEntity<byte[]> r = fichier(t.octets(), t.fichier().getNomFichier(), type);
        return ResponseEntity.status(r.getStatusCode()).headers(h -> { h.addAll(r.getHeaders()); h.add("X-Content-SHA256", t.fichier().getSha256()); })
                .body(r.getBody());
    }

    @GetMapping("/telechargements")
    public List<TelechargementRapport> historique() { acces.directeur(); return telechargements.findAllByOrderByTelechargeLeDesc(); }

    @GetMapping("/audit")
    public List<AuditLog> audit() { acces.directeur(); return journal.findTop500ByOrderByDateDesc(); }

    static ResponseEntity<byte[]> fichier(byte[] b, String nom, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(type))
                .body(b);
    }

    // ── F1 identité, F2 résumé, F18 budget ──────────────────────

    @GetMapping("/identite")
    public StructureIdentite identite() {
        acces.admin();
        StructureIdentite i = identites.findById(1L).orElseGet(StructureIdentite::new);
        if (Texte.vide(i.getUniversite())) i.setUniversite("UNIVERSITE DE TUNIS EL MANAR");
        if (Texte.vide(i.getSiteWeb()) && !Texte.vide(urlPublique)) i.setSiteWeb(urlPublique);
        return i;
    }

    @PutMapping("/identite")
    @Transactional
    public StructureIdentite identite(@RequestBody StructureIdentite i) {
        Utilisateur u = acces.directeur();
        if (!Texte.vide(i.getEmail()) && !i.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse e-mail invalide.");
        if (!Texte.vide(i.getCode()) && !i.getCode().trim().matches("(?i)(LR|UR)\\d{2}[A-Z]{2,4}\\d{2,3}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code de structure attendu au format LRnnESnn.");
        i.setId(1L);
        if (i.getChefId() != null) membres.findById(i.getChefId()).ifPresent(c -> { i.setChefNom(c.getNomComplet()); i.setChefGrade(c.getGrade()); });
        audit.log(u.getEmail(), "IDENTITE", "Mise à jour de l'identité de la structure (F1)");
        return identites.save(i);
    }

    @GetMapping("/{annee}/resume")
    public ResumeRapport resume(@PathVariable int annee) {
        acces.admin();
        return resumes.findByAnnee(annee).orElseGet(() -> { ResumeRapport r = new ResumeRapport(); r.setAnnee(annee); return r; });
    }

    @PutMapping("/{annee}/resume")
    @Transactional
    public ResumeRapport resume(@PathVariable int annee, @RequestBody ResumeRapport corps) {
        Utilisateur u = acces.directeur();
        exigerOuverte(annee);
        if (corps.getHistorique() != null && corps.getHistorique().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'historique dépasse 500 caractères.");
        if (corps.getBilan() != null && corps.getBilan().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le bilan dépasse 500 caractères.");
        ResumeRapport r = resumes.findByAnnee(annee).orElseGet(() -> { ResumeRapport x = new ResumeRapport(); x.setAnnee(annee); return x; });
        r.setHistorique(corps.getHistorique());
        r.setThematiques(corps.getThematiques());
        r.setBilan(corps.getBilan());
        audit.log(u.getEmail(), "RESUME", "Résumé (F2) " + annee);
        return resumes.save(r);
    }

    @GetMapping("/{annee}/resume/proposition")
    public Map<String, String> proposition(@PathVariable int annee) {
        acces.admin();
        return Map.of("bilan", rapports.propositionBilan(annee), "thematiques", rapports.propositionThematiques());
    }

    @GetMapping("/{annee}/budget")
    public List<LigneBudget> budget(@PathVariable int annee) { acces.admin(); return budgets.findByAnneeOrderByOrdre(annee); }

    @PutMapping("/{annee}/budget")
    @Transactional
    public List<LigneBudget> budget(@PathVariable int annee, @RequestBody List<LigneBudget> lignes) {
        Utilisateur u = acces.directeur();
        exigerOuverte(annee);
        Map<String, LigneBudget> existantes = new HashMap<>();
        budgets.findByAnneeOrderByOrdre(annee).forEach(l -> existantes.put(l.getRubrique(), l));
        for (LigneBudget l : lignes) {
            LigneBudget e = existantes.get(l.getRubrique());
            if (e == null) continue; // rubriques fixes
            if ((l.getCreditsDisponibles() != null && l.getCreditsDisponibles().compareTo(BigDecimal.ZERO) < 0)
                    || (l.getCreditsEngages() != null && l.getCreditsEngages().compareTo(BigDecimal.ZERO) < 0))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les montants doivent être positifs ou nuls (" + l.getRubrique() + ").");
            e.setCreditsDisponibles(l.getCreditsDisponibles());
            e.setCreditsEngages(l.getCreditsEngages());
            budgets.save(e);
        }
        audit.log(u.getEmail(), "BUDGET", "Budget (F18) " + annee);
        return budgets.findByAnneeOrderByOrdre(annee);
    }

    private void exigerOuverte(int annee) {
        campagneRepo.findByAnnee(annee).ifPresent(c -> {
            if (c.getStatut() == Campagne.Statut.CLOTUREE)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Campagne " + annee + " clôturée : rouvrez-la pour modifier.");
        });
    }

    // ── Paramètres : correspondances, gabarit ───────────────────

    @GetMapping("/correspondances")
    public List<RegleCorrespondance> correspondances() { acces.admin(); return corr.toutes(); }

    @PutMapping("/correspondances/{table}")
    public List<RegleCorrespondance> correspondances(@PathVariable RegleCorrespondance.Table table, @RequestBody Map<String, String> valeurs) {
        Utilisateur u = acces.directeur();
        var r = corr.remplacer(table, valeurs);
        audit.log(u.getEmail(), "CORRESPONDANCES", table + " : " + valeurs);
        return r;
    }

    @GetMapping("/gabarit")
    public Map<String, Object> gabarit() {
        acces.admin();
        return Map.of("actif", gabarit.json(gabarit.actif()), "versions", gabarit.tous().stream().map(g -> Map.of("version", g.getVersion(), "actif", g.isActif())).toList());
    }

    @PutMapping(value = "/gabarit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> gabarit(@RequestBody String structure) {
        Utilisateur u = acces.directeur();
        var g = gabarit.enregistrer(structure);
        audit.log(u.getEmail(), "GABARIT", "Gabarit « " + g.getVersion() + " » enregistré et activé");
        return gabarit();
    }

    // ── Référentiels (autocomplétion) ───────────────────────────

    @GetMapping("/referentiels")
    public Map<String, Object> referentiels() {
        acces.module();
        List<Chercheur> m = membres.findAll();
        Map<String, String> etab = new TreeMap<>();
        m.forEach(c -> { if (!Texte.vide(c.getEtablissement())) etab.putIfAbsent(c.getEtablissement(), Texte.nn(c.getUniversite())); });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("membres", m.stream().sorted(Comparator.comparing(Chercheur::getNom).thenComparing(Chercheur::getPrenom))
                .map(c -> Map.of("id", c.getId(), "nom", c.getNom(), "prenom", c.getPrenom(), "grade", Texte.nn(c.getGrade()),
                        "categorie", c.getCategorie() == null ? "" : c.getCategorie().name(), "corps", Texte.nn(corr.corps(c.getGrade())))).toList());
        r.put("etablissements", etab.entrySet().stream().map(e -> Map.of("nom", e.getKey(), "universite", e.getValue())).toList());
        r.put("grades", corr.grades());
        Map<String, String> corps = new TreeMap<>();
        corr.toutes().stream().filter(x -> x.getTableCode() == RegleCorrespondance.Table.GRADE_CORPS).forEach(x -> corps.put(x.getCle(), x.getValeur()));
        r.put("corpsParGrade", corps);
        r.put("rubriquesBudget", RapportService.RUBRIQUES_BUDGET);
        return r;
    }
}
