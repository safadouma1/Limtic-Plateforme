package com.projetweb.webapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.projetweb.webapp.rapport.service.GenerationService;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Recette du module « Rapport d'activité » sur le jeu d'essai 2025 (cahier des charges, Annexe E).
 * Les tests s'enchaînent dans l'ordre (même base) : contrôles → correction → clôture → version finale.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:rapport;DB_CLOSE_DELAY=-1", "gemini.api-key=", "app.rate-limit.per-minute=100000"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RapportActiviteTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GenerationService generation;

    static final Map<String, String> jetons = new HashMap<>();

    String jeton(String compte) throws Exception {
        if (jetons.containsKey(compte)) return jetons.get(compte);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + compte + "@limtic.local\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String t = json.readTree(body).get("token").asText();
        jetons.put(compte, t);
        return t;
    }

    ResultActions en(String compte, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + jeton(compte)));
    }

    JsonNode lire(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    Map<String, String> recap(int annee) throws Exception {
        JsonNode s = lire(en("directeur", get("/api/report/" + annee + "/summary")).andExpect(status().isOk()));
        Map<String, String> m = new HashMap<>();
        s.get("groupes").forEach(g -> g.get("rubriques").forEach(r -> m.put(r.get("code").asText(), r.get("valeur").asText())));
        return m;
    }

    JsonNode controle(String code) throws Exception {
        for (JsonNode c : lire(en("directeur", get("/api/report/2025/checks")))) if (c.get("code").asText().equals(code)) return c;
        return null;
    }

    byte[] telecharger(String compte, long versionId, String format) throws Exception {
        JsonNode lien = lire(en(compte, get("/api/report/files/" + versionId + "/download").param("format", format)).andExpect(status().isOk()));
        return mvc.perform(get(lien.get("url").asText())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    @Test @Order(1)
    void recapitulatifCalculeRetrouveLesValeursDuRapport2025() throws Exception {
        Map<String, String> r = recap(2025);
        assertThat(r).containsEntry("CORPS_A", "12").containsEntry("CORPS_B", "30").containsEntry("DOCTORANTS", "27")
                .containsEntry("ETUDIANTS", "15").containsEntry("CADRES", "15")
                .containsEntry("PUBLICATIONS", "47")          // liste importée exacte (le bilan textuel dit 46)
                .containsEntry("OUVRAGES", "0").containsEntry("CHAPITRES", "3")
                .containsEntry("BREVETS", "0").containsEntry("OBTENTIONS", "0").containsEntry("HABILITATIONS", "0")
                .containsEntry("THESES", "7").containsEntry("MASTERS", "14")
                .containsEntry("SEMINAIRES", "1").containsEntry("CONVENTIONS", "5")
                .containsEntry("COOPERATION", "5");           // règle III-3 paramétrable (point §15.6)
    }

    @Test @Order(2)
    void lesAnomaliesDuRapport2025SontSignalees() throws Exception {
        JsonNode cin = controle("CIN_INVALIDE");
        assertThat(cin).isNotNull();
        assertThat(cin.get("niveau").asText()).isEqualTo("BLOQUANT");
        assertThat(cin.get("elements").toString()).contains("BOUGHANEM");
        JsonNode bilan = controle("BILAN_CONTRADICTOIRE");
        assertThat(bilan.get("elements").toString()).contains("46").contains("47");
        assertThat(controle("IMPACTEE_HORS_TABLE")).isNotNull();
        assertThat(controle("AUTEUR_REPETE").get("elements").toString()).contains("Smart Fuel Replenishment");
        assertThat(controle("BUDGET_VIDE")).isNotNull();
    }

    @Test @Order(3)
    void laRegleIII3EstParametrable() throws Exception {
        en("directeur", put("/api/report/campaigns/2025/parametres").contentType(MediaType.APPLICATION_JSON)
                .content("{\"regleIII3\":\"CONVENTIONS_ET_PROJETS_INTERNATIONAUX\",\"bloquerNonValides\":true,\"inclureDoctorantsSoutenus\":true}"))
                .andExpect(status().isOk());
        assertThat(recap(2025)).containsEntry("COOPERATION", "2");
        en("directeur", put("/api/report/campaigns/2025/parametres").contentType(MediaType.APPLICATION_JSON)
                .content("{\"regleIII3\":\"CONVENTIONS_ET_PROJETS\",\"bloquerNonValides\":true,\"inclureDoctorantsSoutenus\":true}"))
                .andExpect(status().isOk());
    }

    @Test @Order(4)
    void unNonAdministrateurNeVoitJamaisUnCin() throws Exception {
        JsonNode membres = lire(en("admin", get("/api/forms/membres")).andExpect(status().isOk()));
        assertThat(membres.toString()).doesNotContain("\"cin\"").doesNotContain("99000001");
        long id = membres.get(0).get("id").asLong();
        en("chercheur", get("/api/forms/membres/" + id + "/cin")).andExpect(status().isForbidden());
        en("moderateur", get("/api/forms/membres")).andExpect(status().isForbidden());
        en("admin", get("/api/forms/membres/" + id + "/cin")).andExpect(status().isOk()).andExpect(jsonPath("$.cin", matchesPattern("\\d{8}")));
        mvc.perform(get("/api/forms/membres")).andExpect(status().isUnauthorized());
    }

    @Test @Order(5)
    void versionProvisoireAvecFiligraneTelechargeableAToutMoment() throws Exception {
        JsonNode v = lire(en("directeur", post("/api/report/2025/generate").param("final", "false").param("withCin", "false")).andExpect(status().isOk()));
        assertThat(v.get("nature").asText()).isEqualTo("PROVISOIRE");
        long id = v.get("id").asLong();
        JsonNode docx = null;
        for (JsonNode f : v.get("fichiers")) if (f.get("format").asText().equals("DOCX")) docx = f;
        assertThat(docx.get("nomFichier").asText()).isEqualTo("LIMTIC_Rapport_activite_2025_v1_provisoire.docx");

        byte[] word = telecharger("admin", id, "docx");
        assertThat(GenerationService.sha256(word)).isEqualTo(docx.get("sha256").asText());
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(word))) {
            boolean filigrane = d.getHeaderList().stream().anyMatch(h -> h._getHdrFtr().xmlText().contains("PROVISOIRE"));
            assertThat(filigrane).isTrue();
            String texte = new XWPFWordExtractor(d).getText();
            assertThat(texte).contains("I-1 – Enseignants-chercheurs permanents — corps A")
                    .contains("Nombre total des enseignants-chercheurs corps A : 12")
                    .contains("Annexe – Points à corriger")
                    .doesNotContain("99000001");            // version sans CIN
        }
        byte[] pdf = telecharger("admin", id, "pdf");
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor ex = new PdfTextExtractor(reader);
        for (int p = 1; p <= reader.getNumberOfPages(); p++) assertThat(ex.getTextFromPage(p)).contains("PROVISOIRE");
    }

    @Test @Order(6)
    void droitsDeTelechargementEtLiensSignes() throws Exception {
        JsonNode v = lire(en("directeur", post("/api/report/2025/generate").param("withCin", "true")).andExpect(status().isOk()));
        long id = v.get("id").asLong();
        en("chercheur", get("/api/report/files/" + id + "/download")).andExpect(status().isForbidden());
        en("admin", get("/api/report/files/" + id + "/download")).andExpect(status().isForbidden());   // version avec CIN
        byte[] word = telecharger("directeur", id, "docx");
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(word))) {
            assertThat(new XWPFWordExtractor(d).getText()).contains("99000001");
        }
        // Lien falsifié ou expiré : refusé
        mvc.perform(get("/api/report/telechargement/AAAA.BBBB")).andExpect(status().isForbidden());
        ReflectionTestUtils.setField(generation, "minutesLien", 0);
        try {
            JsonNode lien = lire(en("directeur", get("/api/report/files/" + id + "/download").param("format", "pdf")));
            Thread.sleep(1200);
            mvc.perform(get(lien.get("url").asText())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", containsString("expiré")));
        } finally {
            ReflectionTestUtils.setField(generation, "minutesLien", 10);
        }
        JsonNode hist = lire(en("directeur", get("/api/report/telechargements")).andExpect(status().isOk()));
        assertThat(hist.toString()).contains("directeur@limtic.local").contains("admin@limtic.local").contains("_v2_provisoire.docx");
        en("admin", get("/api/report/telechargements")).andExpect(status().isForbidden());
    }

    @Test @Order(7)
    void generationFinaleRefuseeTantQuUnControleBloquantEstActif() throws Exception {
        en("directeur", patch("/api/report/campaigns/2025/status").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"CLOTUREE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("bloquant")));
        en("directeur", post("/api/report/2025/generate").param("final", "true")).andExpect(status().isConflict());

        // Correction : le CIN manquant est saisi par l'administrateur
        JsonNode membres = lire(en("admin", get("/api/forms/membres")));
        long id = 0;
        for (JsonNode m : membres) if (m.get("nom").asText().equals("BOUGHANEM")) id = m.get("id").asLong();
        en("admin", put("/api/forms/membres/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"cin\":\"12\"}"))
                .andExpect(status().isBadRequest());
        en("admin", put("/api/forms/membres/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"cin\":\"99000001\"}"))
                .andExpect(status().isConflict());   // CIN déjà attribué
        en("admin", put("/api/forms/membres/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"cin\":\"99000999\"}"))
                .andExpect(status().isOk());
        assertThat(controle("CIN_INVALIDE")).isNull();

        en("directeur", patch("/api/report/campaigns/2025/status").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"CLOTUREE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut", is("CLOTUREE")));
    }

    @Test @Order(8)
    void versionFinaleSansFiligraneEtCopieImmuable() throws Exception {
        JsonNode v = lire(en("directeur", post("/api/report/2025/generate").param("final", "true")).andExpect(status().isOk()));
        assertThat(v.get("nature").asText()).isEqualTo("FINALE");
        long id = v.get("id").asLong();
        String sha = "";
        for (JsonNode f : v.get("fichiers")) if (f.get("format").asText().equals("DOCX")) {
            sha = f.get("sha256").asText();
            assertThat(f.get("nomFichier").asText()).isEqualTo("LIMTIC_Rapport_activite_2025_v3_final.docx");
        }
        byte[] word = telecharger("directeur", id, "docx");
        assertThat(GenerationService.sha256(word)).isEqualTo(sha);
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(word))) {
            assertThat(d.getHeaderList().stream().noneMatch(h -> h._getHdrFtr().xmlText().contains("PROVISOIRE"))).isTrue();
            assertThat(new XWPFWordExtractor(d).getText()).contains("BARHOUMI Walid").doesNotContain("Annexe – Points à corriger");
        }

        // Publications de 2025 verrouillées
        JsonNode pubs = lire(en("admin", get("/api/forms/publications").param("annee", "2025")));
        long pubId = pubs.get(0).get("id").asLong();
        assertThat(pubs.get(0).get("statut").asText()).isEqualTo("VERROUILLE");
        en("admin", put("/api/forms/publications/" + pubId).contentType(MediaType.APPLICATION_JSON).content("{\"titre\":\"X\"}"))
                .andExpect(status().isConflict());

        // Modifier une fiche membre après verrouillage ne change pas le rapport archivé
        long barhoumi = 0;
        for (JsonNode m : lire(en("admin", get("/api/forms/membres")))) if (m.get("nom").asText().equals("BARHOUMI")) barhoumi = m.get("id").asLong();
        en("admin", put("/api/forms/membres/" + barhoumi).contentType(MediaType.APPLICATION_JSON).content("{\"prenom\":\"Walid-Modifié\"}"))
                .andExpect(status().isOk());
        assertThat(GenerationService.sha256(telecharger("directeur", id, "docx"))).isEqualTo(sha);
        JsonNode v2 = lire(en("directeur", post("/api/report/2025/generate").param("final", "true")).andExpect(status().isOk()));
        byte[] regen = telecharger("directeur", v2.get("id").asLong(), "docx");
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(regen))) {
            assertThat(new XWPFWordExtractor(d).getText()).contains("BARHOUMI Walid").doesNotContain("Walid-Modifié");
        }
    }

    @Test @Order(9)
    void reouvertureJournaliseeEtNouvelleVersion() throws Exception {
        en("directeur", patch("/api/report/campaigns/2025/status").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"REVUE\"}"))
                .andExpect(status().isOk());
        JsonNode pubs = lire(en("admin", get("/api/forms/publications").param("annee", "2025")));
        assertThat(pubs.get(0).get("statut").asText()).isEqualTo("VALIDE");
        JsonNode audit = lire(en("directeur", get("/api/report/audit")));
        assertThat(audit.toString()).contains("CAMPAGNE_REOUVERTURE").contains("GENERATION").contains("TELECHARGEMENT").contains("CIN_CONSULTATION");
        en("directeur", patch("/api/report/campaigns/2025/status").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"CLOTUREE\"}"))
                .andExpect(status().isOk());
        lire(en("directeur", post("/api/report/2025/generate").param("final", "true")).andExpect(status().isOk()));
        JsonNode versions = lire(en("admin", get("/api/report/2025/versions")));
        int remplacees = 0;
        for (JsonNode v : versions) if (v.get("remplacee").asBoolean()) remplacees++;
        assertThat(remplacees).isEqualTo(2);   // les deux finales de la 1re clôture
    }

    @Test @Order(10)
    void workflowDuChercheurBrouillonSoumisRejeteValide() throws Exception {
        // Saisie refusée hors campagne ouverte (2025 est clôturée)
        en("chercheur", post("/api/forms/publications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Test\",\"annee\":2025}")).andExpect(status().is4xxClientError());
        // 2026 : saisie ouverte
        JsonNode r = lire(en("chercheur", post("/api/forms/publications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Deep learning pour la mammographie\",\"annee\":2026,\"type\":\"ARTICLE_JOURNAL\"}")).andExpect(status().isCreated()));
        long id = r.get("fiche").get("id").asLong();
        assertThat(r.get("fiche").get("statut").asText()).isEqualTo("BROUILLON");
        // Invisible sur le site public tant qu'il n'est pas validé
        mvc.perform(get("/api/publications")).andExpect(content().string(not(containsString("Deep learning pour la mammographie"))));
        en("chercheur", post("/api/forms/publications/" + id + "/soumettre")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("indexation")));
        JsonNode moi = lire(en("chercheur", get("/api/auth/me")));
        en("chercheur", put("/api/forms/publications/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"venue\":\"Pattern Recognition\",\"indexation\":\"WOS\",\"auteursTexte\":\"BARHOUMI Walid\",\"auteurIds\":[" + moi.get("chercheurId").asLong() + "]}"))
                .andExpect(status().isOk());
        en("chercheur", post("/api/forms/publications/" + id + "/soumettre")).andExpect(status().isOk()).andExpect(jsonPath("$.fiche.statut", is("SOUMIS")));
        en("chercheur", post("/api/forms/publications/" + id + "/valider")).andExpect(status().isForbidden());
        en("admin", post("/api/forms/publications/" + id + "/rejeter").contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"\"}"))
                .andExpect(status().isBadRequest());
        en("admin", post("/api/forms/publications/" + id + "/rejeter").contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"DOI manquant\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fiche.statut", is("BROUILLON"))).andExpect(jsonPath("$.fiche.motifRejet", is("DOI manquant")));
        en("chercheur", post("/api/forms/publications/" + id + "/soumettre")).andExpect(status().isOk());
        en("admin", post("/api/forms/publications/" + id + "/valider")).andExpect(status().isOk()).andExpect(jsonPath("$.fiche.statut", is("VALIDE")));
        mvc.perform(get("/api/publications")).andExpect(content().string(containsString("Deep learning pour la mammographie")));
        // Récapitulatif 2026 : la publication est comptée, l'année précédente est rappelée
        Map<String, String> r26 = recap(2026);
        assertThat(r26).containsEntry("PUBLICATIONS", "1");
    }

    @Test @Order(11)
    void doctorantSoutenuCreeUneThesePreRemplie() throws Exception {
        JsonNode moi = lire(en("chercheur", get("/api/auth/me")));
        JsonNode r = lire(en("chercheur", post("/api/forms/doctorants").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Test\",\"prenom\":\"Doctorant\",\"cin\":\"99111111\",\"etablissement\":\"ISI\",\"universite\":\"UTM\","
                        + "\"sujet\":\"Segmentation\",\"directeurIds\":[" + moi.get("chercheurId").asLong() + "],\"anneePremiereInscription\":2022,"
                        + "\"statutThese\":\"SOUTENU\",\"dateSoutenance\":\"2026-06-30\"}")).andExpect(status().isCreated()));
        assertThat(r.get("message").asText()).contains("F13");
        JsonNode theses = lire(en("chercheur", get("/api/forms/theses").param("annee", "2026")));
        assertThat(theses.toString()).contains("TEST Doctorant").contains("Segmentation");
    }

    @Test @Order(12)
    void ficheActiviteIndividuelleSansCin() throws Exception {
        byte[] pdf = en("chercheur", get("/api/me/activity").param("year", "2025").param("format", "pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        PdfReader reader = new PdfReader(pdf);
        String texte = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(texte).contains("Fiche d'activité 2025").doesNotContain("99000");
    }

    @Test @Order(13)
    void importCsvAvecCompteRenduLigneParLigne() throws Exception {
        String csv = "titre;venue;annee;indexation;auteurs;type\n"
                + "Nouvelle publication importée;Journal X;2026;Scopus;BARHOUMI Walid;ARTICLE_JOURNAL\n"
                + "Sans année;Journal Y;;WOS;BARHOUMI Walid;ARTICLE_JOURNAL\n"
                + "Mammography Lexicon-Based Explainable Artificial Intelligence for Diagnosis and Visual Interpretation of Breast Cancer;J;2025;WOS;X;ARTICLE_JOURNAL\n";
        var fichier = new org.springframework.mock.web.MockMultipartFile("fichier", "pubs.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode cr = lire(en("admin", multipart("/api/import/publications").file(fichier)).andExpect(status().isOk()));
        assertThat(cr.get("importees").asInt()).isEqualTo(1);
        assertThat(cr.get("rejetees").asInt()).isEqualTo(1);
        assertThat(cr.get("doublons").asInt()).isEqualTo(1);
        en("chercheur", multipart("/api/import/publications").file(fichier)).andExpect(status().isForbidden());
    }
}
