package com.projetweb.webapp.rapport.generation;

import com.projetweb.webapp.rapport.service.RapportData.Section;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Export d'un tableau en CSV (UTF-8 avec BOM, séparateur « ; » : s'ouvre directement dans Excel). */
public final class CsvRenderer {
    private CsvRenderer() {}

    public static byte[] tableau(Section s, Rendu r) {
        StringBuilder sb = new StringBuilder("﻿");
        ligne(sb, r.colonnes(s));
        for (List<String> l : s.lignes()) ligne(sb, r.ligne(s, l));
        if (s.ligneTotal() != null) ligne(sb, s.ligneTotal());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void ligne(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(';');
            String c = cells.get(i) == null ? "" : cells.get(i);
            if (c.contains(";") || c.contains("\"") || c.contains("\n") || c.contains("\r"))
                c = '"' + c.replace("\"", "\"\"") + '"';
            sb.append(c);
        }
        sb.append("\r\n");
    }
}
