package com.projetweb.webapp.site;

import com.projetweb.webapp.dto.Dtos;
import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.repository.*;
import com.projetweb.webapp.securite.CinCrypto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Front-office public (Partie 1, chapitre 3) : accueil, directeur, axes, publications paginées et exportables,
 * événements, doctorants, mastériens, contact. Aucune de ces routes ne renvoie de CIN.
 */
@RestController
@RequestMapping("/api/public")
public class SiteController {

    // ── Objets renvoyés ─────────────────────────────────────────
    public record SitePublic(String nomCourt, String nomComplet, String presentation, String couleurPrincipale,
                             String adresse, String telephone, String fax, String email, String carteRequete,
                             String facebookUrl, String linkedinUrl, String youtubeUrl, String siteIsiUrl,
                             String seoTitre, String seoDescription, String seoMotsCles,
                             String universite, String etablissement, String code) {}
    public record Statistiques(long chercheurs, long publications, long doctorants, long masteriens, long projetsEnCours, long evenements) {}
    public record Accueil(SitePublic site, Statistiques stats, List<EvenementResume> prochainsEvenements,
                          List<PublicationResponse> dernieresPublications, List<AxeResume> axes) {}
    public record EvenementResume(Long id, String titre, Evenement.Type type, LocalDate dateDebut, LocalDate dateFin,
                                  Integer annee, String lieu, String etat, String photoUrl) {}
    public record PhotoDto(String url, String legende) {}
    public record EvenementDetail(Long id, String titre, Evenement.Type type, LocalDate dateDebut, LocalDate dateFin,
                                  Integer annee, String lieu, String organisateurs, String description, String programme,
                                  String programmeUrl, List<Evenement.Intervenant> intervenants, List<PhotoDto> photos, String etat) {}
    public record Etudiant(Long id, String nom, String prenom, String sujet, List<AuteurRef> encadrants, String encadrantsTexte,
                           Integer anneeInscription, String promotion, String statut, LocalDate dateSoutenance, String mention,
                           String photoUrl, String etablissement) {}
    public record Encadrements(List<Etudiant> doctorants, List<Etudiant> masteriens) {}
    public record AxeResume(Long id, String nom, String description, List<AuteurRef> responsables, long nbChercheurs, long nbPublications) {}
    public record ProjetDto(Long id, String intitule, Projet.Type type, String financeur, LocalDate dateDebut, LocalDate dateFin) {}
    public record AxeDetail(AxeResume axe, List<ChercheurResponse> membres, List<PublicationResponse> publications, List<ProjetDto> projets) {}
    public record Directeur(ChercheurResponse chercheur, String titre, String fonction, String motDirecteur, String axesPrioritaires,
                            String email, String telephone, List<PublicationResponse> publicationsRecentes) {}
    public record Page<T>(List<T> contenu, int page, int taille, long total, int pages, List<Integer> annees) {}
    public record Captcha(String question, String jeton) {}
    public record ContactRequest(@NotBlank @Size(max = 120) String nom, @NotBlank @Email @Size(max = 200) String email,
                                 @NotBlank @Size(max = 200) String sujet, @NotBlank @Size(min = 10, max = 5000) String message,
                                 @NotBlank String captcha, @NotBlank String jeton, String siteWeb) {}

    private final SiteParametresRepository parametres;
    private final StructureIdentiteRepository identites;
    private final ChercheurRepository chercheurs;
    private final PublicationRepository publications;
    private final DoctorantRepository doctorants;
    private final MasterienRepository masteriens;
    private final EvenementRepository evenements;
    private final ProjetRepository projets;
    private final DomaineRepository domaines;
    private final ContactMessageRepository messages;
    private final SecureRandom aleatoire = new SecureRandom();

    public SiteController(SiteParametresRepository parametres, StructureIdentiteRepository identites, ChercheurRepository chercheurs,
                          PublicationRepository publications, DoctorantRepository doctorants, MasterienRepository masteriens,
                          EvenementRepository evenements, ProjetRepository projets, DomaineRepository domaines,
                          ContactMessageRepository messages) {
        this.parametres = parametres; this.identites = identites; this.chercheurs = chercheurs; this.publications = publications;
        this.doctorants = doctorants; this.masteriens = masteriens; this.evenements = evenements; this.projets = projets;
        this.domaines = domaines; this.messages = messages;
    }

    private static boolean publie(FicheRapport f) { return f.getStatut() == null || f.getStatut().pris(); }

    // ── Accueil et identité ─────────────────────────────────────
    @GetMapping("/site")
    @Transactional(readOnly = true)
    public SitePublic site() {
        SiteParametres p = parametres.findById(1L).orElseGet(SiteParametres::new);
        StructureIdentite i = identites.findById(1L).orElseGet(StructureIdentite::new);
        return new SitePublic(p.getNomCourt(), p.getNomComplet() != null ? p.getNomComplet() : i.getDenomination(), p.getPresentation(),
                p.getCouleurPrincipale(), p.getAdresse(), p.getTelephone() != null ? p.getTelephone() : i.getTelephone(),
                p.getFax() != null ? p.getFax() : i.getFax(), p.getEmail(), p.getCarteRequete(), p.getFacebookUrl(), p.getLinkedinUrl(),
                p.getYoutubeUrl(), p.getSiteIsiUrl(), p.getSeoTitre(), p.getSeoDescription(), p.getSeoMotsCles(),
                i.getUniversite(), i.getEtablissement(), i.getCode());
    }

    @GetMapping("/accueil")
    @Transactional(readOnly = true)
    public Accueil accueil() {
        List<Chercheur> membres = chercheurs.findAll();
        int an = Year.now(ZoneId.of("Africa/Tunis")).getValue();
        Statistiques st = new Statistiques(
                membres.stream().filter(c -> publie(c) && c.getCategorie() == Chercheur.Categorie.ENSEIGNANT_CHERCHEUR && c.getStatutMembre() == Chercheur.StatutMembre.ACTIF).count(),
                publications.findAll().stream().filter(SiteController::publie).count(),
                doctorants.findAll().stream().filter(d -> publie(d) && d.getStatutThese() == Doctorant.Statut.EN_COURS).count(),
                masteriens.findAll().stream().filter(m -> publie(m) && m.getStatutMastere() == Masterien.Statut.EN_COURS).count(),
                projets.findAll().stream().filter(p -> publie(p) && p.enCoursEn(an)).count(),
                evenements.findAll().stream().filter(SiteController::publie).count());
        List<EvenementResume> prochains = evenementsPublies().stream().filter(e -> !"TERMINE".equals(e.getEtat()))
                .sorted(Comparator.comparing(Evenement::getDateDebut, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(3).map(SiteController::resume).toList();
        if (prochains.isEmpty()) prochains = evenementsPublies().stream().limit(3).map(SiteController::resume).toList();
        List<PublicationResponse> recentes = publicationsPubliees().stream().limit(6).map(PublicationResponse::of).toList();
        return new Accueil(site(), st, prochains, recentes, axes());
    }

    @GetMapping("/directeur")
    @Transactional(readOnly = true)
    public Directeur directeur() {
        StructureIdentite i = identites.findById(1L).orElseGet(StructureIdentite::new);
        SiteParametres p = parametres.findById(1L).orElseGet(SiteParametres::new);
        Chercheur c = i.getChefId() == null ? null : chercheurs.findById(i.getChefId()).orElse(null);
        if (c == null) throw new NoSuchElementException("Directeur non renseigné (Rapport d'activité → Identification).");
        List<PublicationResponse> pubs = publications.findByChercheur(c.getId()).stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Publication::getAnnee, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8).map(PublicationResponse::of).toList();
        return new Directeur(ChercheurResponse.of(c, publications.countByChercheur(c.getId())),
                p.getTitreDirecteur() != null ? p.getTitreDirecteur() : c.getGrade(), i.getFonction(), p.getMotDirecteur(),
                p.getAxesPrioritairesDirecteur(), i.getEmail() != null ? i.getEmail() : c.getEmail(),
                i.getTelephone(), pubs);
    }

    // ── Axes de recherche ───────────────────────────────────────
    @GetMapping("/axes")
    @Transactional(readOnly = true)
    public List<AxeResume> axes() {
        return domaines.findAll().stream().map(this::axe).toList();
    }

    @GetMapping("/axes/{id}")
    @Transactional(readOnly = true)
    public AxeDetail axe(@PathVariable Long id) {
        Domaine d = domaines.findById(id).orElseThrow(() -> new NoSuchElementException("Axe introuvable"));
        List<ChercheurResponse> membres = chercheurs.findByDomaineId(id).stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Chercheur::getNom)).map(c -> ChercheurResponse.of(c, publications.countByChercheur(c.getId()))).toList();
        List<PublicationResponse> pubs = publications.findByDomaineId(id).stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Publication::getAnnee, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(PublicationResponse::of).toList();
        List<ProjetDto> pr = projets.findAll().stream().filter(p -> publie(p) && Objects.equals(p.getAxeId(), id))
                .map(p -> new ProjetDto(p.getId(), p.getIntitule(), p.getType(), p.getFinanceur(), p.getDateDebut(), p.getDateFin())).toList();
        return new AxeDetail(axe(d), membres, pubs, pr);
    }

    private AxeResume axe(Domaine d) {
        return new AxeResume(d.getId(), d.getNom(), d.getDescription(),
                chercheurs.findAllById(d.getResponsableIds()).stream().map(AuteurRef::of).toList(),
                chercheurs.countByDomaineId(d.getId()), publications.countByDomaineId(d.getId()));
    }

    // ── Publications : liste paginée côté serveur, recherche, tri, export ──
    @GetMapping("/publications")
    @Transactional(readOnly = true)
    public Page<PublicationResponse> publications(@RequestParam(required = false) String q, @RequestParam(required = false) Publication.Type type,
                                                  @RequestParam(required = false) Integer annee, @RequestParam(required = false) Long auteur,
                                                  @RequestParam(required = false) Long axe, @RequestParam(required = false) String classement,
                                                  @RequestParam(defaultValue = "annee") String tri,
                                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int taille) {
        List<Publication> tout = publicationsPubliees();
        List<Integer> annees = tout.stream().map(Publication::getAnnee).filter(Objects::nonNull).distinct().sorted(Comparator.reverseOrder()).toList();
        List<Publication> l = filtrer(tout, q, type, annee, auteur, axe, classement, tri);
        int t = Math.max(1, Math.min(taille, 100));
        int pages = Math.max(1, (l.size() + t - 1) / t);
        int p = Math.max(0, Math.min(page, pages - 1));
        List<PublicationResponse> contenu = l.subList(Math.min(l.size(), p * t), Math.min(l.size(), (p + 1) * t)).stream().map(PublicationResponse::of).toList();
        return new Page<>(contenu, p, t, l.size(), pages, annees);
    }

    @GetMapping("/publications/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exporter(@RequestParam(defaultValue = "bibtex") String format, @RequestParam(required = false) String q,
                                           @RequestParam(required = false) Publication.Type type, @RequestParam(required = false) Integer annee,
                                           @RequestParam(required = false) Long auteur, @RequestParam(required = false) Long axe,
                                           @RequestParam(required = false) String classement, @RequestParam(defaultValue = "annee") String tri) {
        List<Publication> l = filtrer(publicationsPubliees(), q, type, annee, auteur, axe, classement, tri);
        boolean csv = "csv".equalsIgnoreCase(format);
        String corps = csv ? Exports.csv(l) : Exports.bibtex(l);
        byte[] octets = (csv ? "﻿" + corps : corps).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("LIMTIC_publications." + (csv ? "csv" : "bib")).build().toString())
                .contentType(MediaType.parseMediaType(csv ? "text/csv;charset=UTF-8" : "application/x-bibtex;charset=UTF-8"))
                .body(octets);
    }

    private List<Publication> publicationsPubliees() {
        return publications.findAll().stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Publication::getAnnee, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Publication::getTitre))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String norm(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private List<Publication> filtrer(List<Publication> l, String q, Publication.Type type, Integer annee, Long auteur, Long axe,
                                      String classement, String tri) {
        Predicate<Publication> f = p -> true;
        if (q != null && !q.isBlank()) {
            List<String> mots = Arrays.stream(norm(q).split("\\s+")).filter(m -> !m.isBlank()).toList();
            f = f.and(p -> {
                String texte = norm(p.getTitre() + " " + Objects.toString(p.getAuteursTexte(), "") + " "
                        + p.getChercheurs().stream().map(c -> c.getNom() + " " + c.getPrenom()).collect(Collectors.joining(" ")) + " "
                        + String.join(" ", p.getMotsCles()) + " " + Objects.toString(p.getVenue(), ""));
                return mots.stream().allMatch(texte::contains);
            });
        }
        if (type != null) f = f.and(p -> p.getType() == type);
        if (annee != null) f = f.and(p -> Objects.equals(p.getAnnee(), annee));
        if (auteur != null) f = f.and(p -> p.getChercheurs().stream().anyMatch(c -> c.getId().equals(auteur)));
        if (axe != null) f = f.and(p -> p.getDomaine() != null && p.getDomaine().getId().equals(axe));
        if (classement != null && !classement.isBlank()) f = f.and(p -> classement.equalsIgnoreCase(p.getClassement()));
        List<Publication> r = l.stream().filter(f).collect(Collectors.toCollection(ArrayList::new));
        Comparator<Publication> c = switch (tri) {
            case "annee_asc" -> Comparator.comparing(Publication::getAnnee, Comparator.nullsLast(Comparator.naturalOrder()));
            case "titre" -> Comparator.comparing(p -> norm(p.getTitre()));
            case "type" -> Comparator.comparing(Publication::getType, Comparator.nullsLast(Comparator.naturalOrder()));
            case "citations" -> Comparator.comparing(Publication::getCitations, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Publication::getAnnee, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        r.sort(c.thenComparing(p -> norm(p.getTitre())));
        return r;
    }

    // ── Événements ──────────────────────────────────────────────
    private List<Evenement> evenementsPublies() {
        return evenements.findAll().stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Evenement::getDateDebut, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private static EvenementResume resume(Evenement e) {
        String photo = e.getPhotos().isEmpty() ? null : Dtos.mediaUrl(e.getPhotos().get(0).getMediaId());
        return new EvenementResume(e.getId(), e.getTitre(), e.getType(), e.getDateDebut(), e.getDateFin(), e.anneeEffective(),
                e.getLieu(), e.getEtat(), photo);
    }

    @GetMapping("/evenements")
    @Transactional(readOnly = true)
    public List<EvenementResume> evenements() {
        return evenementsPublies().stream().map(SiteController::resume).toList();
    }

    @GetMapping("/evenements/{id}")
    @Transactional(readOnly = true)
    public EvenementDetail evenement(@PathVariable Long id) {
        Evenement e = evenements.findById(id).filter(SiteController::publie).orElseThrow(() -> new NoSuchElementException("Événement introuvable"));
        return new EvenementDetail(e.getId(), e.getTitre(), e.getType(), e.getDateDebut(), e.getDateFin(), e.anneeEffective(), e.getLieu(),
                e.getOrganisateurs(), e.getDescription(), e.getProgramme(), Dtos.mediaUrl(e.getProgrammeMediaId()),
                List.copyOf(e.getIntervenants()),
                e.getPhotos().stream().map(p -> new PhotoDto(Dtos.mediaUrl(p.getMediaId()), p.getLegende())).toList(), e.getEtat());
    }

    // ── Doctorants, mastériens, encadrements ────────────────────
    private Map<Long, Chercheur> membresParId() {
        return chercheurs.findAll().stream().collect(Collectors.toMap(Chercheur::getId, c -> c));
    }

    private static Etudiant etudiant(Doctorant d, Map<Long, Chercheur> m) {
        return new Etudiant(d.getId(), d.getNom(), d.getPrenom(), d.getSujet(), refs(d.getDirecteurIds(), m), d.getDirecteursTexte(),
                d.getAnneePremiereInscription(), null, d.getStatutThese().name(), d.getDateSoutenance(), d.getMention(),
                Dtos.mediaUrl(d.getPhotoMediaId()), d.getEtablissement());
    }

    private static Etudiant etudiant(Masterien x, Map<Long, Chercheur> m) {
        return new Etudiant(x.getId(), x.getNom(), x.getPrenom(), x.getSujet(), refs(x.getEncadrantIds(), m), x.getEncadrantsTexte(),
                x.getAnneePremiereInscription(), x.getPromotion() != null ? x.getPromotion()
                : x.getAnneePremiereInscription() == null ? null : x.getAnneePremiereInscription() + "–" + (x.getAnneePremiereInscription() + 1),
                x.getStatutMastere().name(), x.getDateSoutenance(), null, Dtos.mediaUrl(x.getPhotoMediaId()), x.getEtablissement());
    }

    private static List<AuteurRef> refs(List<Long> ids, Map<Long, Chercheur> m) {
        return ids.stream().map(m::get).filter(Objects::nonNull).map(AuteurRef::of).toList();
    }

    @GetMapping("/doctorants")
    @Transactional(readOnly = true)
    public List<Etudiant> doctorants() {
        var m = membresParId();
        return doctorants.findAll().stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Doctorant::getNom).thenComparing(Doctorant::getPrenom)).map(d -> etudiant(d, m)).toList();
    }

    @GetMapping("/masteriens")
    @Transactional(readOnly = true)
    public List<Etudiant> masteriens() {
        var m = membresParId();
        return masteriens.findAll().stream().filter(SiteController::publie)
                .sorted(Comparator.comparing(Masterien::getNom).thenComparing(Masterien::getPrenom)).map(x -> etudiant(x, m)).toList();
    }

    @GetMapping("/chercheurs/{id}/encadrements")
    @Transactional(readOnly = true)
    public Encadrements encadrements(@PathVariable Long id) {
        var m = membresParId();
        return new Encadrements(
                doctorants.findAll().stream().filter(d -> publie(d) && d.getDirecteurIds().contains(id)).map(d -> etudiant(d, m)).toList(),
                masteriens.findAll().stream().filter(x -> publie(x) && x.getEncadrantIds().contains(id)).map(x -> etudiant(x, m)).toList());
    }

    // ── Contact ─────────────────────────────────────────────────
    /** Captcha arithmétique signé (pas de service tiers) : la réponse n'est jamais renvoyée en clair. */
    @GetMapping("/captcha")
    public Captcha captcha() {
        int a = 2 + aleatoire.nextInt(9), b = 1 + aleatoire.nextInt(9);
        long exp = Instant.now().plus(Duration.ofMinutes(20)).getEpochSecond();
        String nonce = Long.toHexString(aleatoire.nextLong());
        String charge = (a + b) + ":" + exp + ":" + nonce;
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(CinCrypto.get().signer(charge.getBytes(StandardCharsets.UTF_8)));
        String publique = exp + ":" + nonce;
        return new Captcha("Combien font " + a + " + " + b + " ?",
                Base64.getUrlEncoder().withoutPadding().encodeToString(publique.getBytes(StandardCharsets.UTF_8)) + "." + sig);
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, String> contact(@Valid @RequestBody ContactRequest req) {
        // Champ piège invisible : rempli uniquement par les robots
        if (req.siteWeb() != null && !req.siteWeb().isBlank()) return Map.of("message", "Message envoyé.");
        verifierCaptcha(req.captcha().trim(), req.jeton());
        ContactMessage m = new ContactMessage();
        m.setNom(req.nom().trim());
        m.setEmail(req.email().trim());
        m.setSujet(req.sujet().trim());
        m.setMessage(req.message().trim());
        messages.save(m);
        return Map.of("message", "Merci, votre message a bien été transmis au laboratoire.");
    }

    private void verifierCaptcha(String reponse, String jeton) {
        try {
            String[] parts = jeton.split("\\.");
            String publique = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(publique.split(":")[0]);
            if (Instant.now().getEpochSecond() > exp) throw new IllegalArgumentException("Captcha expiré : rechargez la question.");
            byte[] attendu = CinCrypto.get().signer((Integer.parseInt(reponse) + ":" + publique).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(attendu, Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException("Réponse au captcha incorrecte.");
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Réponse au captcha incorrecte.");
        }
    }
}
