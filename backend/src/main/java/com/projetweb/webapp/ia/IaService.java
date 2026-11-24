package com.projetweb.webapp.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.model.Chercheur;
import com.projetweb.webapp.model.Publication;
import com.projetweb.webapp.repository.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fonctionnalités IA de la plateforme, propulsées par Google Gemini :
 *  - assistant conversationnel qui connaît les publications de la plateforme,
 *  - recherche sémantique et recommandations (embeddings + similarité cosinus),
 *  - résumé automatique et extraction de mots-clés,
 *  - analyse des tendances.
 * Sans clé Gemini, la recherche et les recommandations basculent sur un TF-IDF local
 * (mode "lexical") pour que l'application reste utilisable.
 */
@Service
@Transactional(readOnly = true)
public class IaService {

    private static final Logger log = LoggerFactory.getLogger(IaService.class);
    private static final int MAX_CONTEXTE_CARACTERES = 60_000;

    private static final String SYSTEME_ASSISTANT = """
            Tu es l'assistant de recherche de ResAIrch, une plateforme qui recense des chercheurs et leurs \
            publications scientifiques. Réponds en français (ou dans la langue de l'utilisateur), de façon \
            claire et structurée (Markdown léger : listes, **gras**).
            Règles :
            - Pour toute question sur les publications, chercheurs ou domaines de LA PLATEFORME, appuie-toi \
            uniquement sur le catalogue fourni ci-dessous et cite les titres exacts.
            - Si l'information n'est pas dans le catalogue, dis-le clairement au lieu d'inventer.
            - Pour les questions générales (expliquer un concept scientifique), tu peux utiliser tes \
            connaissances générales, en le précisant.
            """;

    private final GeminiClient gemini;
    private final PublicationRepository publications;

    /** Cache des embeddings des publications : id -> (empreinte du texte, vecteur). */
    private final Map<Long, CacheEmbedding> cache = new ConcurrentHashMap<>();
    private record CacheEmbedding(int empreinte, float[] vecteur) {}

    public IaService(GeminiClient gemini, PublicationRepository publications) {
        this.gemini = gemini;
        this.publications = publications;
    }

    public IaStatus statut() {
        return new IaStatus(gemini.estConfigure(), gemini.getModel(), gemini.getEmbeddingModel());
    }

    // ── Assistant ───────────────────────────────────────────────

    public String chat(String message, List<ChatMessage> historique) {
        String systeme = SYSTEME_ASSISTANT + "\n\n=== CATALOGUE DES PUBLICATIONS ===\n" + catalogue(publiees());
        List<GeminiClient.Tour> tours = new ArrayList<>();
        if (historique != null) {
            // On ne garde que les 12 derniers échanges pour limiter la taille de la requête
            historique.stream().skip(Math.max(0, historique.size() - 12)).forEach(m ->
                    tours.add(new GeminiClient.Tour("assistant".equals(m.role()) ? "model" : "user", m.content())));
        }
        tours.add(new GeminiClient.Tour("user", message));
        return gemini.generer(systeme, tours, 0.5);
    }

    // ── Résumé & mots-clés ──────────────────────────────────────

    public String resumer(String texte) {
        return gemini.generer(
                "Tu es un assistant scientifique. Résume le texte fourni en français, en 3 à 5 phrases, "
                        + "en conservant l'objectif, la méthode et les résultats principaux. N'invente rien.",
                List.of(new GeminiClient.Tour("user", texte)), 0.3);
    }

    public String resumerPublication(Long id) {
        Publication p = publications.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Publication introuvable"));
        return resumer(ficheTexte(p));
    }

    public List<String> motsCles(String texte) {
        JsonNode arr = gemini.genererJson(
                "Tu extrais les mots-clés scientifiques d'un texte. Réponds uniquement avec un tableau JSON.",
                "Extrais entre 4 et 8 mots-clés ou expressions-clés pertinents (dans la langue du texte), "
                        + "du plus au moins important, pour indexer ce texte :\n\n" + texte,
                Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
        List<String> out = new ArrayList<>();
        arr.forEach(n -> {
            String s = n.asText("").trim();
            if (!s.isEmpty() && out.stream().noneMatch(s::equalsIgnoreCase)) out.add(s);
        });
        return out;
    }

    public String tendances(StatsResponse stats) {
        String donnees = """
                Chercheurs : %d, publications : %d, domaines : %d.
                Publications par domaine : %s
                Publications par année : %s
                Mots-clés les plus fréquents : %s
                """.formatted(stats.totalChercheurs(), stats.totalPublications(), stats.totalDomaines(),
                fmt(stats.publicationsParDomaine()), fmt(stats.publicationsParAnnee()), fmt(stats.motsClesFrequents()));
        return gemini.generer(
                "Tu es analyste de la production scientifique d'un laboratoire. À partir des statistiques "
                        + "fournies (et uniquement elles), rédige en français une analyse courte en Markdown : "
                        + "3 à 5 puces sur les tendances observées, puis 2 recommandations concrètes. "
                        + "Ne cite aucun chiffre absent des données.",
                List.of(new GeminiClient.Tour("user", donnees)), 0.4);
    }

    // ── Recherche & recommandations ─────────────────────────────

    public SearchResponse rechercher(String requete, int limite) {
        List<Publication> corpus = publiees();
        if (corpus.isEmpty()) return new SearchResponse(mode(), List.of());

        if (gemini.estConfigure()) {
            try {
                Map<Long, float[]> docs = embeddingsPublications(corpus);
                float[] q = gemini.embeddings(List.of(requete), "RETRIEVAL_QUERY").get(0);
                return new SearchResponse("semantique", classer(corpus, p -> cosinus(q, docs.get(p.getId())), limite, 0.0));
            } catch (IaIndisponibleException e) {
                log.warn("Recherche sémantique indisponible, repli lexical : {}", e.getMessage());
            }
        }
        Tfidf tfidf = new Tfidf(corpus);
        Map<String, Double> q = tfidf.vecteur(requete);
        return new SearchResponse("lexical", classer(corpus, p -> tfidf.cosinus(q, tfidf.vecteurDe(p)), limite, 0.0001));
    }

    public SearchResponse recommander(Long publicationId, int limite) {
        List<Publication> corpus = publiees();
        Publication ref = corpus.stream().filter(p -> p.getId().equals(publicationId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Publication introuvable"));
        List<Publication> autres = corpus.stream().filter(p -> !p.getId().equals(publicationId)).toList();
        if (autres.isEmpty()) return new SearchResponse(mode(), List.of());

        if (gemini.estConfigure()) {
            try {
                Map<Long, float[]> docs = embeddingsPublications(corpus);
                float[] r = docs.get(ref.getId());
                return new SearchResponse("semantique", classer(autres, p -> cosinus(r, docs.get(p.getId())), limite, 0.0));
            } catch (IaIndisponibleException e) {
                log.warn("Recommandations sémantiques indisponibles, repli lexical : {}", e.getMessage());
            }
        }
        Tfidf tfidf = new Tfidf(corpus);
        Map<String, Double> r = tfidf.vecteurDe(ref);
        return new SearchResponse("lexical", classer(autres, p -> tfidf.cosinus(r, tfidf.vecteurDe(p)), limite, 0.0001));
    }

    // ────────────────────────────────────────────────────────────

    private String mode() { return gemini.estConfigure() ? "semantique" : "lexical"; }

    private Map<Long, float[]> embeddingsPublications(List<Publication> corpus) {
        List<Publication> manquants = new ArrayList<>();
        for (Publication p : corpus) {
            CacheEmbedding c = cache.get(p.getId());
            if (c == null || c.empreinte() != ficheTexte(p).hashCode()) manquants.add(p);
        }
        if (!manquants.isEmpty()) {
            List<String> textes = manquants.stream().map(this::ficheTexte).toList();
            List<float[]> vecteurs = gemini.embeddings(textes, "RETRIEVAL_DOCUMENT");
            for (int i = 0; i < manquants.size(); i++) {
                cache.put(manquants.get(i).getId(), new CacheEmbedding(textes.get(i).hashCode(), vecteurs.get(i)));
            }
        }
        Set<Long> ids = corpus.stream().map(Publication::getId).collect(Collectors.toSet());
        cache.keySet().retainAll(ids);
        Map<Long, float[]> out = new HashMap<>();
        cache.forEach((id, c) -> out.put(id, c.vecteur()));
        return out;
    }

    private List<SearchResult> classer(List<Publication> pubs, java.util.function.ToDoubleFunction<Publication> score,
                                       int limite, double seuil) {
        return pubs.stream()
                .map(p -> Map.entry(p, score.applyAsDouble(p)))
                .filter(e -> e.getValue() > seuil)
                .sorted(Map.Entry.<Publication, Double>comparingByValue().reversed())
                .limit(limite)
                .map(e -> versResultat(e.getKey(), e.getValue()))
                .toList();
    }

    private SearchResult versResultat(Publication p, double score) {
        return new SearchResult(p.getId(), p.getTitre(), p.getResume(),
                Math.round(Math.max(0, Math.min(1, score)) * 1000) / 1000.0,
                List.copyOf(p.getMotsCles()), auteurs(p), p.getAnnee());
    }

    private static List<String> auteurs(Publication p) {
        return p.getChercheurs().stream().map(c -> c.getPrenom() + " " + c.getNom()).toList();
    }

    private String ficheTexte(Publication p) {
        return "Titre : " + p.getTitre()
                + (p.getDomaine() != null ? "\nDomaine : " + p.getDomaine().getNom() : "")
                + (p.getMotsCles().isEmpty() ? "" : "\nMots-clés : " + String.join(", ", p.getMotsCles()))
                + "\nRésumé : " + Objects.requireNonNullElse(p.getResume(), "");
    }

    private String catalogue(List<Publication> pubs) {
        if (pubs.isEmpty()) return "(aucune publication enregistrée pour le moment)";
        StringBuilder sb = new StringBuilder();
        for (Publication p : pubs) {
            String fiche = "- [" + p.getId() + "] « " + p.getTitre() + " » (" + Objects.toString(p.getAnnee(), "année ?") + ")"
                    + " — auteurs : " + (p.getChercheurs().isEmpty() ? "non renseignés" : String.join(", ", auteurs(p)))
                    + affiliations(p)
                    + (p.getDomaine() != null ? " — domaine : " + p.getDomaine().getNom() : "")
                    + (p.getMotsCles().isEmpty() ? "" : " — mots-clés : " + String.join(", ", p.getMotsCles()))
                    + "\n  Résumé : " + Objects.requireNonNullElse(p.getResume(), "—") + "\n";
            if (sb.length() + fiche.length() > MAX_CONTEXTE_CARACTERES) {
                sb.append("(catalogue tronqué)\n");
                break;
            }
            sb.append(fiche);
        }
        return sb.toString();
    }

    private static String affiliations(Publication p) {
        String e = p.getChercheurs().stream().map(Chercheur::getEtablissement).filter(Objects::nonNull)
                .distinct().collect(Collectors.joining(", "));
        return e.isEmpty() ? "" : " (" + e + ")";
    }

    private static String fmt(List<CountItem> items) {
        return items.stream().map(i -> i.nom() + " = " + i.count()).collect(Collectors.joining(", "));
    }

    static double cosinus(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** TF-IDF minimal utilisé quand Gemini n'est pas disponible. */
    static final class Tfidf {
        private static final Set<String> STOP = Set.of(
                "les", "des", "une", "un", "le", "la", "de", "du", "et", "en", "pour", "par", "sur", "dans", "au", "aux",
                "avec", "est", "sont", "ce", "ces", "cette", "qui", "que", "quoi", "dont", "ou", "plus", "pas", "son",
                "ses", "leur", "leurs", "nous", "vous", "titre", "resume", "domaine", "mots", "cles",
                "the", "and", "for", "with", "from", "this", "that", "are", "was", "of", "to", "in", "on", "by", "an", "is");
        private final Map<String, Double> idf = new HashMap<>();
        private final Map<Long, Map<String, Double>> docs = new HashMap<>();

        Tfidf(List<Publication> corpus) {
            Map<String, Integer> df = new HashMap<>();
            Map<Long, List<String>> tokens = new HashMap<>();
            for (Publication p : corpus) {
                List<String> t = tokeniser(p.getTitre() + " " + p.getTitre() + " "      // titre pondéré x2
                        + String.join(" ", p.getMotsCles()) + " " + String.join(" ", p.getMotsCles()) + " "
                        + Objects.requireNonNullElse(p.getResume(), ""));
                tokens.put(p.getId(), t);
                new HashSet<>(t).forEach(w -> df.merge(w, 1, Integer::sum));
            }
            int n = corpus.size();
            df.forEach((w, d) -> idf.put(w, Math.log(1.0 + (double) n / d)));
            tokens.forEach((id, t) -> docs.put(id, ponderer(t)));
        }

        Map<String, Double> vecteur(String texte) { return ponderer(tokeniser(texte)); }
        Map<String, Double> vecteurDe(Publication p) { return docs.getOrDefault(p.getId(), Map.of()); }

        private Map<String, Double> ponderer(List<String> t) {
            Map<String, Double> tf = new HashMap<>();
            t.forEach(w -> tf.merge(w, 1.0, Double::sum));
            tf.replaceAll((w, f) -> f * idf.getOrDefault(w, 0.0));
            return tf;
        }

        double cosinus(Map<String, Double> a, Map<String, Double> b) {
            double dot = 0;
            for (var e : a.entrySet()) dot += e.getValue() * b.getOrDefault(e.getKey(), 0.0);
            double na = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
            double nb = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
            return (na == 0 || nb == 0) ? 0 : dot / (na * nb);
        }

        static List<String> tokeniser(String s) {
            String norm = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            return Arrays.stream(norm.split("[^a-z0-9]+"))
                    .filter(w -> w.length() > 2 && !STOP.contains(w))
                    .map(w -> w.length() > 5 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w)
                    .toList();
        }
    }

    /** Seules les publications validées (visibles sur le site) alimentent l'assistant et la recherche. */
    private List<Publication> publiees() {
        return publications.findAll().stream().filter(p -> p.getStatut() == null || p.getStatut().pris()).toList();
    }
}
