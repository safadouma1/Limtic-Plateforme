package com.projetweb.webapp.web;

import com.projetweb.webapp.dto.Dtos.*;
import com.projetweb.webapp.ia.IaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Module IA (Google Gemini). Toutes les routes, sauf /status, exigent d'être connecté. */
@RestController
@RequestMapping("/api/ia")
public class IaController {

    private final IaService ia;
    private final StatsController stats;

    public IaController(IaService ia, StatsController stats) {
        this.ia = ia;
        this.stats = stats;
    }

    @GetMapping("/status")
    public IaStatus status() { return ia.statut(); }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req) {
        return new ChatResponse(ia.chat(req.message(), req.historique()));
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest req) {
        return ia.rechercher(req.query(), 10);
    }

    @GetMapping("/recommend/{publicationId}")
    public SearchResponse recommend(@PathVariable Long publicationId) {
        return ia.recommander(publicationId, 5);
    }

    @PostMapping("/keywords")
    public KeywordsResponse keywords(@Valid @RequestBody TexteRequest req) {
        return new KeywordsResponse(ia.motsCles(req.texte()));
    }

    @PostMapping("/summarize")
    public ResumeResponse summarize(@Valid @RequestBody TexteRequest req) {
        return new ResumeResponse(ia.resumer(req.texte()));
    }

    @PostMapping("/summarize/{publicationId}")
    public ResumeResponse summarizePublication(@PathVariable Long publicationId) {
        return new ResumeResponse(ia.resumerPublication(publicationId));
    }

    @PostMapping("/trends")
    public ResumeResponse trends() {
        return new ResumeResponse(ia.tendances(stats.stats()));
    }
}
