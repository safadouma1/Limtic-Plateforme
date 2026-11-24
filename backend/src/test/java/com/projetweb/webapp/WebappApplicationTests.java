package com.projetweb.webapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:partie1;DB_CLOSE_DELAY=-1",
        "gemini.api-key=",
        "app.rate-limit.per-minute=100000"
})
@AutoConfigureMockMvc
class WebappApplicationTests {

    @Autowired MockMvc mvc;

    @Test
    void contextLoads() {}

    @Test
    void consultationPubliqueEtStats() throws Exception {
        mvc.perform(get("/api/publications")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(greaterThan(0))));
        mvc.perform(get("/api/stats")).andExpect(status().isOk()).andExpect(jsonPath("$.totalPublications", greaterThan(0)));
        mvc.perform(get("/api/ia/status")).andExpect(jsonPath("$.geminiConfigure", is(false)));
    }

    @Test
    void ficheChercheurPubliqueSansCin() throws Exception {
        mvc.perform(get("/api/chercheurs")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(57)))
                .andExpect(jsonPath("$[0].cin").doesNotExist())
                .andExpect(content().string(not(containsString("99000"))));
    }

    @Test
    void ecritureRefuseeSansToken() throws Exception {
        mvc.perform(post("/api/domaines").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginPuisRechercheLexicaleSansCleGemini() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@limtic.local\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/ia/search").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"mammography breast cancer explainable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode", is("lexical")))
                .andExpect(jsonPath("$.resultats[0].titre", containsStringIgnoringCase("mammogra")));

        // Sans clé, les fonctions génératives renvoient 503 avec un message explicite
        mvc.perform(post("/api/ia/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("GEMINI_API_KEY")));
    }

    @Test
    void mauvaisMotDePasse() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@limtic.local\",\"password\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
    }
}
