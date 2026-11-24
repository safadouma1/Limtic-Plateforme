package com.projetweb.webapp.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/**
 * Client minimal de l'API REST Google Gemini (generateContent + embeddings).
 * Documentation : https://ai.google.dev/api
 *
 * La clé est lue depuis la variable d'environnement GEMINI_API_KEY (via application.properties)
 * et envoyée dans l'en-tête x-goog-api-key : elle ne transite jamais par le navigateur.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final int EMBED_BATCH = 100;

    private final String apiKey;
    private final String model;
    private final String embeddingModel;
    private final RestClient http;
    private final ObjectMapper json;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model}") String model,
                        @Value("${gemini.embedding-model}") String embeddingModel,
                        @Value("${gemini.base-url}") String baseUrl,
                        @Value("${gemini.timeout-seconds}") int timeoutSeconds,
                        ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.json = json;
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.getDefault())
                .build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        if (this.apiKey.isEmpty()) {
            log.warn("GEMINI_API_KEY non définie : les fonctionnalités IA génératives sont désactivées "
                    + "(la recherche utilisera le mode lexical).");
        } else {
            log.info("Gemini configuré (modèle {}, embeddings {})", model, embeddingModel);
        }
    }

    public boolean estConfigure() { return !apiKey.isEmpty(); }
    public String getModel() { return model; }
    public String getEmbeddingModel() { return embeddingModel; }

    /** Un tour de conversation : role = "user" ou "model". */
    public record Tour(String role, String texte) {}

    /** Génère une réponse texte. */
    public String generer(String instructionSysteme, List<Tour> conversation, double temperature) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (instructionSysteme != null && !instructionSysteme.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instructionSysteme))));
        }
        body.put("contents", conversation.stream()
                .map(t -> Map.of("role", t.role(), "parts", List.of(Map.of("text", t.texte()))))
                .toList());
        body.put("generationConfig", Map.of("temperature", temperature));
        return extraireTexte(appeler("/models/" + model + ":generateContent", body));
    }

    /** Génère une réponse JSON contrainte par un schéma (structured output). */
    public JsonNode genererJson(String instructionSysteme, String prompt, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (instructionSysteme != null) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instructionSysteme))));
        }
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", Map.of(
                "temperature", 0.2,
                "responseMimeType", "application/json",
                "responseSchema", schema));
        String texte = extraireTexte(appeler("/models/" + model + ":generateContent", body));
        try {
            return json.readTree(texte);
        } catch (Exception e) {
            throw new IaIndisponibleException("Réponse JSON invalide de Gemini", e);
        }
    }

    /**
     * Calcule les embeddings d'une liste de textes.
     * @param taskType RETRIEVAL_DOCUMENT pour les publications, RETRIEVAL_QUERY pour une requête,
     *                 SEMANTIC_SIMILARITY pour comparer des documents entre eux.
     */
    public List<float[]> embeddings(List<String> textes, String taskType) {
        List<float[]> out = new ArrayList<>(textes.size());
        for (int i = 0; i < textes.size(); i += EMBED_BATCH) {
            List<String> lot = textes.subList(i, Math.min(i + EMBED_BATCH, textes.size()));
            List<Map<String, Object>> requests = lot.stream().map(t -> Map.<String, Object>of(
                    "model", "models/" + embeddingModel,
                    "content", Map.of("parts", List.of(Map.of("text", t))),
                    "taskType", taskType)).toList();
            JsonNode res = appeler("/models/" + embeddingModel + ":batchEmbedContents", Map.of("requests", requests));
            for (JsonNode e : res.path("embeddings")) {
                JsonNode values = e.path("values");
                float[] v = new float[values.size()];
                for (int k = 0; k < v.length; k++) v[k] = (float) values.get(k).asDouble();
                out.add(v);
            }
        }
        if (out.size() != textes.size()) {
            throw new IaIndisponibleException("Nombre d'embeddings inattendu renvoyé par Gemini");
        }
        return out;
    }

    // ────────────────────────────────────────────────────────────

    private JsonNode appeler(String chemin, Object body) {
        if (!estConfigure()) {
            throw new IaIndisponibleException(
                    "Gemini n'est pas configuré : définissez la variable d'environnement GEMINI_API_KEY puis redémarrez le backend.");
        }
        try {
            return http.post()
                    .uri(chemin)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            log.warn("Erreur Gemini {} : {}", e.getStatusCode().value(), detail);
            String msg = switch (e.getStatusCode().value()) {
                case 400 -> "Requête refusée par Gemini (clé invalide ou paramètre incorrect).";
                case 401, 403 -> "Clé GEMINI_API_KEY refusée par Google.";
                case 404 -> "Modèle Gemini introuvable : vérifiez GEMINI_MODEL / GEMINI_EMBEDDING_MODEL.";
                case 429 -> "Quota Gemini dépassé, réessayez dans un instant.";
                default -> "Le service Gemini a renvoyé une erreur (" + e.getStatusCode().value() + ").";
            };
            throw new IaIndisponibleException(msg, e);
        } catch (Exception e) {
            log.warn("Gemini injoignable : {}", e.getMessage());
            throw new IaIndisponibleException("Impossible de contacter Gemini : " + e.getMessage(), e);
        }
    }

    private String extraireTexte(JsonNode res) {
        JsonNode cand = res == null ? null : res.path("candidates").path(0);
        if (cand == null || cand.isMissingNode()) {
            String raison = res == null ? "" : res.path("promptFeedback").path("blockReason").asText("");
            throw new IaIndisponibleException(raison.isEmpty()
                    ? "Gemini n'a renvoyé aucune réponse."
                    : "Requête bloquée par les filtres de sécurité Gemini (" + raison + ").");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : cand.path("content").path("parts")) {
            // On ignore les "pensées" éventuelles des modèles à raisonnement
            if (!part.path("thought").asBoolean(false)) sb.append(part.path("text").asText(""));
        }
        if (sb.isEmpty()) {
            throw new IaIndisponibleException("Réponse vide de Gemini (raison : "
                    + cand.path("finishReason").asText("inconnue") + ").");
        }
        return sb.toString().trim();
    }
}
