package com.projetweb.webapp.site;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** Assainissement du texte riche (éditeur WYSIWYG) avant stockage : protection XSS (§5.2.2). */
public final class Html {
    private Html() {}

    private static final Safelist LISTE = Safelist.relaxed()
            .removeTags("img")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto");

    public static String nettoyer(String html) {
        if (html == null || html.isBlank()) return null;
        Document.OutputSettings o = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, "", LISTE, o).trim();
    }

    /** Texte brut (aperçus, balises meta). */
    public static String texte(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }
}
