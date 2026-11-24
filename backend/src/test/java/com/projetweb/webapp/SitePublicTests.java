package com.projetweb.webapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Partie 1 (site web public) : pages publiques, exports, contact, mot de passe oublié, comptes, médias. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:site;DB_CLOSE_DELAY=-1",
        "gemini.api-key=",
        "app.rate-limit.per-minute=100000"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SitePublicTests {

    @Autowired MockMvc mvc;

    private String jeton(String email, String mdp) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + mdp + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void pagesPubliquesSansAuthentificationNiCin() throws Exception {
        for (String u : new String[]{"/api/public/site", "/api/public/accueil", "/api/public/axes", "/api/public/doctorants",
                "/api/public/masteriens", "/api/public/evenements", "/api/public/publications", "/api/public/directeur"}) {
            mvc.perform(get(u)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("\"cin\""))))
                    .andExpect(content().string(not(containsString("99000"))));
        }
        mvc.perform(get("/api/public/accueil")).andExpect(jsonPath("$.stats.chercheurs", greaterThan(0)));
        mvc.perform(get("/api/public/publications").param("taille", "5"))
                .andExpect(jsonPath("$.contenu", hasSize(5))).andExpect(jsonPath("$.total", greaterThan(5)));
    }

    @Test
    void filtresEtExportBibtexCsv() throws Exception {
        mvc.perform(get("/api/public/publications").param("q", "mammography"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].titre", containsStringIgnoringCase("mammogra")));
        mvc.perform(get("/api/public/publications/export").param("format", "bibtex"))
                .andExpect(status().isOk()).andExpect(content().string(matchesPattern("(?s).*@(article|inproceedings|misc)\\{.*")))
                .andExpect(content().string(containsString("title")));
        mvc.perform(get("/api/public/publications/export").param("format", "csv"))
                .andExpect(status().isOk()).andExpect(content().string(startsWith("\uFEFF")));
    }

    @Test
    void contactAvecCaptchaEtPotDeMiel() throws Exception {
        String c = mvc.perform(get("/api/public/captcha")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("Combien font (\\d+) \\+ (\\d+)").matcher(c);
        assertTrue(m.find());
        int somme = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
        String jeton = c.replaceAll(".*\"jeton\":\"([^\"]+)\".*", "$1");
        String base = "{\"nom\":\"Test\",\"email\":\"t@example.org\",\"sujet\":\"Stage\",\"message\":\"Bonjour, je souhaite un stage.\",\"jeton\":\"" + jeton + "\"";

        mvc.perform(post("/api/public/contact").contentType(MediaType.APPLICATION_JSON).content(base + ",\"captcha\":\"" + (somme + 1) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/public/contact").contentType(MediaType.APPLICATION_JSON).content(base + ",\"captcha\":\"" + somme + "\"}"))
                .andExpect(status().is2xxSuccessful());

        String admin = jeton("admin@limtic.local", "admin123");
        mvc.perform(get("/api/contact").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sujet", is("Stage")));
        mvc.perform(get("/api/contact")).andExpect(status().isUnauthorized());
    }

    @Test
    void motDePasseOublieEtCompteDesactive(CapturedOutput sortie) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Reset\",\"prenom\":\"Test\",\"email\":\"reset@example.org\",\"password\":\"ancien123\"}"))
                .andExpect(status().isCreated());

        // Réponse identique que le compte existe ou non (pas d'énumération des comptes)
        mvc.perform(post("/api/auth/forgot").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"inconnu@example.org\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/forgot").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"reset@example.org\"}"))
                .andExpect(status().isOk());
        Matcher m = Pattern.compile("reinitialiser\\?jeton=([A-Za-z0-9_-]+)").matcher(sortie.getOut());
        assertTrue(m.find(), "lien de réinitialisation journalisé quand SMTP n'est pas configuré");
        String jeton = m.group(1);

        mvc.perform(post("/api/auth/reset").contentType(MediaType.APPLICATION_JSON).content("{\"jeton\":\"faux\",\"password\":\"nouveau123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset").contentType(MediaType.APPLICATION_JSON).content("{\"jeton\":\"" + jeton + "\",\"password\":\"nouveau123\"}"))
                .andExpect(status().isOk());
        // Lien à usage unique
        mvc.perform(post("/api/auth/reset").contentType(MediaType.APPLICATION_JSON).content("{\"jeton\":\"" + jeton + "\",\"password\":\"autre1234\"}"))
                .andExpect(status().isBadRequest());
        jeton("reset@example.org", "nouveau123");

        // Désactivation par l'administrateur : la connexion est refusée
        String admin = jeton("admin@limtic.local", "admin123");
        String liste = mvc.perform(get("/api/utilisateurs").header("Authorization", admin)).andReturn().getResponse().getContentAsString();
        Matcher id = Pattern.compile("\\{\"id\":(\\d+),[^}]*\"email\":\"reset@example.org\"").matcher(liste);
        assertTrue(id.find());
        mvc.perform(put("/api/utilisateurs/" + id.group(1) + "/actif").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif", is(false)));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@example.org\",\"password\":\"nouveau123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void depotDeMediaVerifieLeContenuReel() throws Exception {
        String admin = jeton("admin@limtic.local", "admin123");
        MockMultipartFile fauxPng = new MockMultipartFile("fichiers", "photo.png", "image/png", "<script>alert(1)</script>".getBytes());
        mvc.perform(multipart("/api/media").file(fauxPng).header("Authorization", admin)).andExpect(status().isBadRequest());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
        String r = mvc.perform(multipart("/api/media").file(new MockMultipartFile("fichiers", "x.png", "text/plain", png)).header("Authorization", admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = r.replaceAll("(?s).*\"id\":(\\d+).*", "$1");
        mvc.perform(get("/api/media/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/png")))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));

        mvc.perform(multipart("/api/media").file(new MockMultipartFile("fichiers", "x.png", "image/png", png))).andExpect(status().isUnauthorized());
    }
}
