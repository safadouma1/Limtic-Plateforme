package com.projetweb.webapp.rapport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projetweb.webapp.model.Utilisateur;
import com.projetweb.webapp.rapport.generation.*;
import com.projetweb.webapp.rapport.model.*;
import com.projetweb.webapp.rapport.repo.*;
import com.projetweb.webapp.rapport.service.RapportData.Section;
import com.projetweb.webapp.repository.UtilisateurRepository;
import com.projetweb.webapp.securite.CinCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Génération, archivage et téléchargement du rapport (§17.4 – §17.5).
 * Chaque version regroupe Word, PDF, un CSV par tableau et une archive ZIP ; chaque fichier porte son
 * empreinte SHA-256. Le téléchargement passe par un lien signé, valable quelques minutes, journalisé.
 */
@Service
public class GenerationService {

    public record Lien(String url, Instant expireLe, String nomFichier, String sha256) {}
    public record Telechargement(FichierRapport fichier, byte[] octets) {}

    private static final DateTimeFormatter HORO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZoneId.of("Africa/Tunis"));

    private final RapportService rapports;
    private final RapportGenereRepository versions;
    private final FichierRapportRepository fichiers;
    private final ContenuFichierRepository contenus;
    private final TelechargementRapportRepository telechargements;
    private final CampagneRepository campagnes;
    private final UtilisateurRepository users;
    private final Acces acces;
    private final AuditService audit;
    private final CinCrypto crypto;
    private final ObjectMapper mapper;
    private final int minutesLien;

    public GenerationService(RapportService rapports, RapportGenereRepository versions, FichierRapportRepository fichiers,
                             ContenuFichierRepository contenus, TelechargementRapportRepository telechargements,
                             CampagneRepository campagnes, UtilisateurRepository users, Acces acces, AuditService audit,
                             CinCrypto crypto, ObjectMapper mapper, @Value("${app.rapport.lien-minutes:10}") int minutesLien) {
        this.rapports = rapports; this.versions = versions; this.fichiers = fichiers; this.contenus = contenus;
        this.telechargements = telechargements; this.campagnes = campagnes; this.users = users; this.acces = acces;
        this.audit = audit; this.crypto = crypto; this.mapper = mapper; this.minutesLien = minutesLien;
    }

    // ── Copie immuable (snapshot) ───────────────────────────────

    public byte[] scellerSnapshot(RapportData d) {
        try {
            return crypto.chiffrerOctets(mapper.writeValueAsBytes(d));
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot impossible", e);
        }
    }

    public RapportData lireSnapshot(byte[] s) {
        try {
            return mapper.readValue(crypto.dechiffrerOctets(s), RapportData.class);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot illisible", e);
        }
    }

    // ── Génération ──────────────────────────────────────────────

    @Transactional
    public RapportGenere generer(int annee, boolean finale, boolean avecCin, boolean inclureSoumis) {
        Utilisateur u = acces.directeur();
        Campagne c = campagnes.findByAnnee(annee).orElseThrow(() -> new NoSuchElementException("Aucune campagne pour " + annee));
        RapportData d;
        if (finale) {
            if (c.getStatut() != Campagne.Statut.CLOTUREE || c.getSnapshot() == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La version finale exige une campagne clôturée (contrôles bloquants levés et données verrouillées).");
            d = lireSnapshot(c.getSnapshot());
            if (d.nbBloquants() > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Génération refusée : " + d.nbBloquants() + " contrôle(s) bloquant(s) actif(s).");
            inclureSoumis = false;
        } else {
            d = rapports.construire(annee, inclureSoumis);
        }
        int v = versions.findByAnneeOrderByVersionDesc(annee).stream().mapToInt(RapportGenere::getVersion).max().orElse(0) + 1;
        String base = "LIMTIC_Rapport_activite_" + annee + "_v" + v + "_" + (finale ? "final" : "provisoire");
        Instant now = Instant.now();
        String mention = "LIMTIC – Rapport d'activité " + annee + " – v" + v + " " + (finale ? "finale" : "provisoire")
                + (avecCin ? " – CONFIDENTIEL (contient des CIN)" : " – version sans CIN") + " – générée le " + HORO.format(now);
        Rendu r = new Rendu(!finale, avecCin, mention, false, null);

        RapportGenere g = new RapportGenere();
        g.setAnnee(annee);
        g.setVersion(v);
        g.setNature(finale ? RapportGenere.Nature.FINALE : RapportGenere.Nature.PROVISOIRE);
        g.setAvecCin(avecCin);
        g.setInclureSoumis(inclureSoumis);
        g.setGabaritVersion(d.gabaritVersion());
        g.setGenerePar(u.getEmail());
        g.setGenereLe(now);
        g.setNbBloquants((int) d.nbBloquants());
        g.setNbAvertissements((int) (d.controles().size() - d.nbBloquants()));
        g.setNumeroCloture(finale ? c.getNbClotures() : 0);

        Map<String, byte[]> tous = new LinkedHashMap<>();
        try {
            tous.put(base + ".docx", DocxRenderer.rendre(d, r));
        } catch (Exception e) {
            throw new IllegalStateException("Génération Word impossible : " + e.getMessage(), e);
        }
        tous.put(base + ".pdf", PdfRenderer.rendre(d, r));
        ajouter(g, FichierRapport.Format.DOCX, null, base + ".docx", tous.get(base + ".docx"));
        ajouter(g, FichierRapport.Format.PDF, null, base + ".pdf", tous.get(base + ".pdf"));
        for (var grp : d.groupes())
            for (Section s : grp.sections()) {
                String nom = base + "_" + s.numero() + "_" + s.code().toLowerCase() + ".csv";
                byte[] csv = CsvRenderer.tableau(s, r);
                tous.put("tableaux/" + nom, csv);
                ajouter(g, FichierRapport.Format.CSV, s.numero(), nom, csv);
            }
        ajouter(g, FichierRapport.Format.ZIP, null, base + ".zip", zip(tous));

        if (finale)
            versions.findByAnneeOrderByVersionDesc(annee).stream()
                    .filter(x -> x.getNature() == RapportGenere.Nature.FINALE && x.getNumeroCloture() < c.getNbClotures())
                    .forEach(x -> { x.setRemplacee(true); versions.save(x); });
        g = versions.save(g);
        audit.log(u.getEmail(), "GENERATION", "Rapport " + annee + " v" + v + " " + g.getNature() + (avecCin ? " avec CIN" : " sans CIN")
                + (inclureSoumis ? ", enregistrements soumis inclus" : "") + " — " + g.getNbBloquants() + " bloquant(s)");
        return g;
    }

    private void ajouter(RapportGenere g, FichierRapport.Format f, String tableau, String nom, byte[] octets) {
        FichierRapport fr = new FichierRapport();
        fr.setRapport(g);
        fr.setFormat(f);
        fr.setTableau(tableau);
        fr.setNomFichier(nom);
        fr.setTaille(octets.length);
        fr.setSha256(sha256(octets));
        fr.setContenuId(contenus.save(new ContenuFichier(octets)).getId());
        g.getFichiers().add(fr);
    }

    private static byte[] zip(Map<String, byte[]> fichiers) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : fichiers.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
            z.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Droits de téléchargement (§17.4) ────────────────────────

    /** Version avec CIN : directeur uniquement. Sans CIN : administrateurs et directeur. Chercheur : jamais. */
    public static boolean peutTelecharger(Utilisateur u, RapportGenere g) {
        if (g.isAvecCin()) return Acces.directeur(u);
        return Acces.admin(u);
    }

    @Transactional(readOnly = true)
    public Lien lien(Long versionId, String format, String tableau) {
        Utilisateur u = acces.courant();
        RapportGenere g = versions.findById(versionId).orElseThrow(() -> new NoSuchElementException("Version introuvable"));
        if (!peutTelecharger(u, g))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, g.isAvecCin()
                    ? "La version contenant les numéros de CIN est réservée au directeur du laboratoire."
                    : "Le téléchargement du rapport est réservé aux administrateurs.");
        FichierRapport.Format f;
        try {
            f = FichierRapport.Format.valueOf(format.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format inconnu : " + format);
        }
        FichierRapport fr = g.getFichiers().stream()
                .filter(x -> x.getFormat() == f && (f != FichierRapport.Format.CSV || Objects.equals(x.getTableau(), tableau)))
                .findFirst().orElseThrow(() -> new NoSuchElementException("Fichier introuvable pour ce format"));
        Instant exp = Instant.now().plusSeconds(60L * minutesLien);
        String token = jeton(fr.getId(), u.getId(), exp.getEpochSecond());
        audit.log(u.getEmail(), "LIEN_TELECHARGEMENT", fr.getNomFichier() + " (valide " + minutesLien + " min)");
        return new Lien("/api/report/telechargement/" + token, exp, fr.getNomFichier(), fr.getSha256());
    }

    private String jeton(long fichierId, long userId, long exp) {
        byte[] charge = ByteBuffer.allocate(24).putLong(fichierId).putLong(userId).putLong(exp).array();
        Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
        return b.encodeToString(charge) + "." + b.encodeToString(crypto.signer(charge));
    }

    /** Vérifie le lien signé (signature, expiration, droits actuels) et journalise le téléchargement. */
    @Transactional
    public Telechargement telecharger(String token) {
        String[] p = token.split("\\.");
        byte[] charge, sig;
        try {
            charge = Base64.getUrlDecoder().decode(p[0]);
            sig = Base64.getUrlDecoder().decode(p[1]);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lien de téléchargement invalide.");
        }
        if (charge.length != 24 || !MessageDigest.isEqual(sig, crypto.signer(charge)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lien de téléchargement invalide.");
        ByteBuffer bb = ByteBuffer.wrap(charge);
        long fichierId = bb.getLong(), userId = bb.getLong(), exp = bb.getLong();
        if (Instant.now().getEpochSecond() > exp)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lien de téléchargement expiré : demandez-en un nouveau depuis la page « Rapports ».");
        Utilisateur u = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Utilisateur inconnu."));
        FichierRapport fr = fichiers.findById(fichierId).orElseThrow(() -> new NoSuchElementException("Fichier introuvable"));
        RapportGenere g = fr.getRapport();
        if (!peutTelecharger(u, g)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé.");
        byte[] octets = contenus.findById(fr.getContenuId()).orElseThrow().getOctets();
        TelechargementRapport t = new TelechargementRapport();
        t.setRapportId(g.getId());
        t.setAnnee(g.getAnnee());
        t.setVersion(g.getVersion());
        t.setFichierId(fr.getId());
        t.setNomFichier(fr.getNomFichier());
        t.setFormat(fr.getFormat().name() + (fr.getTableau() != null ? " " + fr.getTableau() : ""));
        t.setUtilisateur(u.getEmail());
        t.setTelechargeLe(Instant.now());
        telechargements.save(t);
        audit.log(u.getEmail(), "TELECHARGEMENT", fr.getNomFichier() + " (SHA-256 " + fr.getSha256().substring(0, 12) + "…)");
        return new Telechargement(fr, octets);
    }

    // ── Fiche d'activité individuelle (§17.6) ───────────────────

    public byte[] ficheActivite(RapportData d, String titre, String mention, boolean pdf) {
        Rendu r = new Rendu(false, false, mention, true, titre);
        try {
            return pdf ? PdfRenderer.rendre(d, r) : DocxRenderer.rendre(d, r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
