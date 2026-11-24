package com.projetweb.webapp.site;

import com.projetweb.webapp.model.Role;
import com.projetweb.webapp.model.Utilisateur;
import com.projetweb.webapp.rapport.service.Acces;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/** Dépôt et diffusion des fichiers du site (photos, PDF). */
@RestController
@RequestMapping("/api/media")
public class MediaController {
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif", "application/pdf", "pdf");
    private final MediaRepository medias;
    private final Acces acces;

    public MediaController(MediaRepository medias, Acces acces) { this.medias = medias; this.acces = acces; }

    public record MediaDto(Long id, String url, String nom, String contentType, long taille) {}

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<MediaDto> deposer(@RequestParam("fichiers") List<MultipartFile> fichiers) throws IOException {
        Utilisateur u = acces.courant();
        if (u.getRole() != Role.ADMIN && u.getRole() != Role.DIRECTEUR && u.getRole() != Role.CHERCHEUR && u.getRole() != Role.MODERATEUR)
            Acces.refuser("Dépôt de fichiers réservé aux membres du laboratoire.");
        if (fichiers.size() > 20) throw new IllegalArgumentException("20 fichiers au plus par envoi.");
        List<MediaDto> r = new ArrayList<>();
        for (MultipartFile f : fichiers) {
            byte[] b = f.getBytes();
            String type = detecter(b);
            if (type == null) throw new IllegalArgumentException("« " + f.getOriginalFilename() + " » : format non accepté (JPEG, PNG, WebP, GIF ou PDF).");
            if (b.length > 10 * 1024 * 1024) throw new IllegalArgumentException("« " + f.getOriginalFilename() + " » dépasse 10 Mo.");
            Media m = new Media();
            String nom = Objects.toString(f.getOriginalFilename(), "fichier").replaceAll("[\\\\/\\r\\n\"]", "_");
            m.setNom(nom.length() > 200 ? nom.substring(0, 200) : nom);
            m.setContentType(type);
            m.setTaille(b.length);
            m.setContenu(b);
            m.setDeposeParId(u.getId());
            medias.save(m);
            r.add(new MediaDto(m.getId(), "/api/media/" + m.getId(), m.getNom(), type, b.length));
        }
        return r;
    }

    /** Le type est déduit des premiers octets, pas de l'extension ni de l'en-tête envoyé par le client. */
    private static String detecter(byte[] b) {
        if (b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "image/gif";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        return null;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> lire(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean telecharger) {
        Media m = medias.findById(id).orElseThrow(() -> new NoSuchElementException("Fichier introuvable"));
        if (!TYPES.containsKey(m.getContentType())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ContentDisposition cd = (telecharger ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(m.getNom(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(m.getContenu());
    }
}
