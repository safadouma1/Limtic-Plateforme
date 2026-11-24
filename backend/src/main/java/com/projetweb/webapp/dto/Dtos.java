package com.projetweb.webapp.dto;

import com.projetweb.webapp.model.*;
import jakarta.validation.constraints.*;

import java.util.List;

/** Objets échangés avec le frontend (JSON). Regroupés dans un seul fichier pour la lisibilité. */
public final class Dtos {
    private Dtos() {}

    // ── Authentification ────────────────────────────────────────
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RegisterRequest(@NotBlank String nom, @NotBlank String prenom,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 6, message = "6 caractères minimum") String password) {}

    public record UserResponse(Long id, String nom, String prenom, String email, Role role, Long chercheurId, boolean actif, String token) {
        public static UserResponse of(Utilisateur u, String token) {
            return new UserResponse(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getRole(), u.getChercheurId(), u.isActif(), token);
        }
    }

    public record ForgotRequest(@NotBlank @Email String email) {}

    public record ResetRequest(@NotBlank String jeton,
                               @NotBlank @Size(min = 8, message = "8 caractères minimum") String password) {}

    public record ActifUpdate(boolean actif) {}

    public record RoleUpdate(@NotNull Role role) {}

    public record LienChercheur(Long chercheurId) {}

    // ── Domaines ────────────────────────────────────────────────
    public record DomaineRequest(@NotBlank String nom, @Size(max = 4000) String description, List<Long> responsableIds) {}

    public record DomaineResponse(Long id, String nom, String description, long nbChercheurs, long nbPublications,
                                  List<AuteurRef> responsables) {}

    // ── Chercheurs ──────────────────────────────────────────────
    public record ChercheurRequest(@NotBlank String nom, @NotBlank String prenom,
                                   @Email String email,
                                   String specialite, String etablissement, Long domaineId,
                                   String grade, String bureau, String telephone, String orcid, String googleScholarUrl,
                                   String researchGateUrl, String linkedinUrl, @Size(max = 4000) String biographie,
                                   Long photoMediaId, Chercheur.StatutMembre statutMembre) {}

    /** Ce qu'un chercheur peut modifier lui-même sur sa page (§3.3.2, §7.3). */
    public record MaFicheRequest(String specialite, String bureau, String telephone, String orcid, String googleScholarUrl,
                                 String researchGateUrl, String linkedinUrl, @Size(max = 4000) String biographie,
                                 Long photoMediaId, Long domaineId) {}

    public record DomaineRef(Long id, String nom) {
        public static DomaineRef of(Domaine d) { return d == null ? null : new DomaineRef(d.getId(), d.getNom()); }
    }

    /** Fiche publique : jamais de CIN. */
    public record ChercheurResponse(Long id, String nom, String prenom, String email, String specialite,
                                    String etablissement, DomaineRef domaine, long nbPublications, String grade,
                                    String photoUrl, Long photoMediaId, String bureau, String telephone, String orcid,
                                    String googleScholarUrl, String researchGateUrl, String linkedinUrl, String biographie,
                                    Chercheur.StatutMembre statutMembre, Chercheur.Categorie categorie) {
        public static ChercheurResponse of(Chercheur c, long nbPublications) {
            return new ChercheurResponse(c.getId(), c.getNom(), c.getPrenom(), c.getEmail(), c.getSpecialite(),
                    c.getEtablissement(), DomaineRef.of(c.getDomaine()), nbPublications, c.getGrade(),
                    mediaUrl(c.getPhotoMediaId()), c.getPhotoMediaId(), c.getBureau(), c.getTelephone(), c.getOrcid(),
                    c.getGoogleScholarUrl(), c.getResearchGateUrl(), c.getLinkedinUrl(), c.getBiographie(),
                    c.getStatutMembre(), c.getCategorie());
        }
    }

    public static String mediaUrl(Long id) { return id == null ? null : "/api/media/" + id; }

    // ── Publications ────────────────────────────────────────────
    public record PublicationRequest(@NotBlank String titre, String resume,
                                     @Min(1900) @Max(2100) Integer annee,
                                     String doi, String pdfUrl, List<String> motsCles,
                                     List<Long> chercheurIds, Long domaineId,
                                     Publication.Type type, String venue, Publication.Indexation indexation,
                                     Boolean estImpactee, String auteursTexte,
                                     String classement, Publication.SourceClassement sourceClassement, Double facteurImpact,
                                     Double snip, Integer citations, String volume, String numero, String pages, String lieu,
                                     String pays, String editeur, String isbn, String institution, String comiteOrganisation,
                                     Long pdfMediaId) {}

    public record AuteurRef(Long id, String nom, String prenom) {
        public static AuteurRef of(Chercheur c) { return new AuteurRef(c.getId(), c.getNom(), c.getPrenom()); }
    }

    public record PublicationResponse(Long id, String titre, String resume, Integer annee, String doi, String pdfUrl,
                                      List<String> motsCles, List<AuteurRef> chercheurs, DomaineRef domaine,
                                      Publication.Type type, String venue, Publication.Indexation indexation,
                                      Boolean estImpactee, String auteursTexte, StatutFiche statut,
                                      String classement, Publication.SourceClassement sourceClassement, Double facteurImpact,
                                      Double snip, Integer citations, String volume, String numero, String pages, String lieu,
                                      String pays, String editeur, String isbn, String institution, String comiteOrganisation,
                                      Long pdfMediaId, String pdfFichierUrl) {
        public static PublicationResponse of(Publication p) {
            return new PublicationResponse(p.getId(), p.getTitre(), p.getResume(), p.getAnnee(), p.getDoi(),
                    p.getPdfUrl(), List.copyOf(p.getMotsCles()),
                    p.getChercheurs().stream().map(AuteurRef::of).toList(), DomaineRef.of(p.getDomaine()),
                    p.getType(), p.getVenue(), p.getIndexation(), p.getEstImpactee(), p.getAuteursTexte(), p.getStatut(),
                    p.getClassement(), p.getSourceClassement(), p.getFacteurImpact(), p.getSnip(), p.getCitations(),
                    p.getVolume(), p.getNumero(), p.getPages(), p.getLieu(), p.getPays(), p.getEditeur(), p.getIsbn(),
                    p.getInstitution(), p.getComiteOrganisation(), p.getPdfMediaId(), mediaUrl(p.getPdfMediaId()));
        }
    }

    // ── Statistiques ────────────────────────────────────────────
    public record CountItem(String nom, long count) {}

    public record StatsResponse(long totalChercheurs, long totalPublications, long totalDomaines,
                                long totalUtilisateurs, List<CountItem> publicationsParDomaine,
                                List<CountItem> publicationsParAnnee, List<CountItem> motsClesFrequents) {}

    // ── IA (Gemini) ─────────────────────────────────────────────
    public record ChatMessage(@NotBlank String role, @NotBlank String content) {}

    public record ChatRequest(@NotBlank @Size(max = 4000) String message, List<ChatMessage> historique) {}

    public record ChatResponse(String reponse) {}

    public record SearchRequest(@NotBlank @Size(max = 500) String query) {}

    public record SearchResult(Long id, String titre, String resume, double score, List<String> motsCles,
                               List<String> auteurs, Integer annee) {}

    /** mode = "semantique" (embeddings Gemini) ou "lexical" (repli sans clé API). */
    public record SearchResponse(String mode, List<SearchResult> resultats) {}

    public record TexteRequest(@NotBlank @Size(max = 20000) String texte) {}

    public record KeywordsResponse(List<String> motsCles) {}

    public record ResumeResponse(String resume) {}

    public record IaStatus(boolean geminiConfigure, String modele, String modeleEmbedding) {}
}
