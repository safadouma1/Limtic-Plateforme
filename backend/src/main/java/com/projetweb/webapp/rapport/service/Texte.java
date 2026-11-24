package com.projetweb.webapp.rapport.service;

import java.text.Normalizer;

/** Petites fonctions de normalisation de texte (comparaison sans accents ni casse). */
public final class Texte {
    private Texte() {}

    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static boolean vide(String s) { return s == null || s.isBlank(); }

    public static String nn(Object o) { return o == null ? "" : o.toString(); }
}
