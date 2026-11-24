package com.projetweb.webapp.config;

import com.projetweb.webapp.model.*;
import com.projetweb.webapp.rapport.model.Campagne;
import com.projetweb.webapp.rapport.repo.CampagneRepository;
import com.projetweb.webapp.rapport.service.*;
import com.projetweb.webapp.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Premier démarrage :
 *  - toujours : tables de correspondance (§15.4) et gabarit « Modèle 2025 » (§17.1) si absents ;
 *  - base vide et SEED_DEMO_DATA=true : axes de recherche LIMTIC, jeu d'essai du rapport 2025
 *    (CIN FICTIFS), campagnes 2025 (revue) et 2026 (saisie ouverte), comptes de démonstration.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UtilisateurRepository users;
    private final DomaineRepository domaines;
    private final ChercheurRepository chercheurs;
    private final PublicationRepository publications;
    private final CampagneRepository campagnes;
    private final Correspondances correspondances;
    private final Gabarit gabarit;
    private final ImportService imports;
    private final CampagneService campagneService;
    private final PasswordEncoder encoder;
    private final boolean actif;
    private final String motDePasse;
    @org.springframework.beans.factory.annotation.Autowired
    private com.projetweb.webapp.site.SiteParametresRepository siteParametres;

    public DataInitializer(UtilisateurRepository users, DomaineRepository domaines, ChercheurRepository chercheurs,
                           PublicationRepository publications, CampagneRepository campagnes, Correspondances correspondances,
                           Gabarit gabarit, ImportService imports, CampagneService campagneService, PasswordEncoder encoder,
                           @Value("${app.seed.enabled}") boolean actif,
                           @Value("${app.seed.admin-password}") String motDePasse) {
        this.users = users; this.domaines = domaines; this.chercheurs = chercheurs; this.publications = publications;
        this.campagnes = campagnes; this.correspondances = correspondances; this.gabarit = gabarit; this.imports = imports;
        this.campagneService = campagneService; this.encoder = encoder; this.actif = actif; this.motDePasse = motDePasse;
    }

    @Override
    @Transactional
    public void run(String... args) {
        correspondances.initialiser();
        gabarit.initialiser();
        if (!actif) return;

        if (domaines.count() == 0 && chercheurs.count() == 0 && publications.count() == 0) {
            Map<String, Domaine> axes = new LinkedHashMap<>();
            axes.put("optim", domaines.save(new Domaine("Modélisation et optimisation des problèmes informatiques",
                    "Optimisation, systèmes multi-agents, cloud, IoT, sécurité et architectures matérielles")));
            axes.put("image", domaines.save(new Domaine("Reconnaissance de la parole, imagerie et extraction des connaissances",
                    "Vision par ordinateur et imagerie 3D, imagerie médicale et télédétection, reconnaissance des formes")));
            axes.put("index", domaines.save(new Domaine("Indexation d'information multi-source",
                    "Indexation et recherche d'images et de vidéos par le contenu, fusion d'informations multi-sources")));
            axes.put("ia", domaines.save(new Domaine("Intelligence artificielle",
                    "Représentation des connaissances, apprentissage automatique et aide à la décision")));

            imports.importerJeuEssai("initialisation");
            publications.findAll().forEach(p -> { p.setDomaine(axes.get(axe(p.getTitre() + " " + p.getVenue()))); publications.save(p); });
            // Axe principal d'un membre : celui de la majorité de ses publications (modifiable ensuite)
            for (Chercheur c : chercheurs.findAll()) {
                publications.findByChercheur(c.getId()).stream().map(Publication::getDomaine).filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.groupingBy(d -> d, java.util.stream.Collectors.counting()))
                        .entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> { c.setDomaine(e.getKey()); chercheurs.save(c); });
            }
            if (campagnes.findByAnnee(2026).isEmpty()) {
                Campagne c = campagneService.creerSansControle(new CampagneService.Creation(2026, LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 12, 15), LocalDate.of(2026, 10, 31)));
                c.setStatut(Campagne.Statut.SAISIE_OUVERTE);
                campagnes.save(c);
            }
            log.info("Jeu d'essai du rapport 2025 chargé ({} membres, {} publications). Les numéros de CIN sont FICTIFS.",
                    chercheurs.count(), publications.count());
        }

        if (siteParametres.findById(1L).isEmpty()) {
            var sp = new com.projetweb.webapp.site.SiteParametres();
            sp.setNomComplet("Laboratoire de recherche en Informatique, Modélisation et Traitement de l'Information et de la Connaissance");
            sp.setPresentation("Le LIMTIC (LR16ES06) est un laboratoire de recherche de l'Institut Supérieur d'Informatique, "
                    + "Université de Tunis El Manar. Issu de l'équipe SIIVA fondée en 2003, il fédère des enseignants-chercheurs, "
                    + "des doctorants et des étudiants de mastère autour de quatre axes : modélisation et optimisation des problèmes "
                    + "informatiques ; reconnaissance de la parole, imagerie et extraction des connaissances ; indexation d'information "
                    + "multi-source ; intelligence artificielle.");
            sp.setAdresse("Institut Supérieur d'Informatique, 2 rue Abou Raihane Bayrouni, 2080 L'Ariana, Tunisie");
            sp.setTelephone("+216 71 706 164");
            sp.setFax("+216 71 706 698");
            sp.setEmail("ezzeddine.zagrouba@isi.utm.tn");
            sp.setCarteRequete("Institut Supérieur d'Informatique, Ariana, Tunisie");
            sp.setSiteIsiUrl("https://isi.rnu.tn");
            sp.setSeoTitre("LIMTIC – Laboratoire de recherche de l'ISI, Université de Tunis El Manar");
            sp.setSeoDescription("Chercheurs, publications, axes de recherche, événements et encadrements du laboratoire LIMTIC (ISI, Université de Tunis El Manar).");
            sp.setSeoMotsCles("LIMTIC, ISI, Université de Tunis El Manar, laboratoire, recherche, vision par ordinateur, intelligence artificielle, optimisation");
            siteParametres.save(sp);
        }

        if (users.count() == 0) {
            Long zagrouba = membre("ZAGROUBA", "Ezzeddine");
            Long barhoumi = membre("BARHOUMI", "Walid");
            compte("Ezzeddine", "ZAGROUBA", "directeur@limtic.local", Role.DIRECTEUR, zagrouba);
            compte("Plateforme", "Administrateur", "admin@limtic.local", Role.ADMIN, null);
            compte("Plateforme", "Modérateur", "moderateur@limtic.local", Role.MODERATEUR, null);
            compte("Walid", "BARHOUMI", "chercheur@limtic.local", Role.CHERCHEUR, barhoumi);
            compte("Démo", "Visiteur", "user@limtic.local", Role.UTILISATEUR, null);
            log.info("Comptes de démonstration : directeur@, admin@, moderateur@, chercheur@, user@limtic.local "
                    + "(mot de passe : variable ADMIN_PASSWORD, 'admin123' par défaut)");
        }
    }

    /** Rattachement indicatif d'une publication à un axe, par mots du titre (modifiable ensuite). */
    static String axe(String t) {
        String s = Texte.norm(t);
        if (s.matches(".*\\b(retrieval|indexing|indexation|search|recommend\\w*|multi source|fusion)\\b.*")) return "index";
        if (s.matches(".*\\b(optimi[sz]\\w*|scheduling|cloud|blockchain|iot|fpga|traffic|trafic|multi agent\\w*|resource\\w*|security|securite|routing|network\\w*)\\b.*")) return "optim";
        if (s.matches(".*\\b(image\\w*|video\\w*|facial|face|emotion\\w*|mammogra\\w*|mri|irm|segmentation|medical|speech|parole|vision|3d|recognition|gaussian|lesion\\w*|cancer|tumou?r\\w*)\\b.*")) return "image";
        return "ia";
    }

    private Long membre(String nom, String prenom) {
        return chercheurs.findAll().stream().filter(c -> nom.equals(c.getNom()) && c.getPrenom().startsWith(prenom))
                .map(Chercheur::getId).findFirst().orElse(null);
    }

    private void compte(String prenom, String nom, String email, Role role, Long chercheurId) {
        Utilisateur u = new Utilisateur();
        u.setPrenom(prenom);
        u.setNom(nom);
        u.setEmail(email);
        u.setMotDePasse(encoder.encode(motDePasse));
        u.setRole(role);
        u.setChercheurId(chercheurId);
        users.save(u);
    }
}
