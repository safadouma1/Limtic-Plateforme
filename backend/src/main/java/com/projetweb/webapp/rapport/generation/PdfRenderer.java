package com.projetweb.webapp.rapport.generation;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.projetweb.webapp.rapport.service.RapportData;
import com.projetweb.webapp.rapport.service.RapportData.*;
import com.projetweb.webapp.rapport.service.RapportData.Section;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.List;

/** Rapport au format PDF : même structure et même rendu que le fichier Word. */
public final class PdfRenderer {
    private static final Color BLEU = new Color(0x1F, 0x3A, 0x5F);
    private static final Color GRIS = new Color(0xE8, 0xEE, 0xF5);
    private static final Font F_TITRE = new Font(Font.HELVETICA, 20, Font.BOLD, BLEU);
    private static final Font F_H1 = new Font(Font.HELVETICA, 13, Font.BOLD, BLEU);
    private static final Font F_H2 = new Font(Font.HELVETICA, 11, Font.BOLD, BLEU);
    private static final Font F_TXT = new Font(Font.HELVETICA, 10);
    private static final Font F_B = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font F_CELL = new Font(Font.HELVETICA, 8);
    private static final Font F_CELL_B = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font F_HEAD = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

    private PdfRenderer() {}

    public static byte[] rendre(RapportData d, Rendu r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 45);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        w.setPageEvent(new Evenements(d, r));
        doc.addTitle(r.fiche() ? r.titreFiche() : "LIMTIC – " + d.titre());
        doc.addAuthor("LIMTIC");
        doc.open();
        if (r.fiche()) {
            p(doc, r.titreFiche(), F_TITRE, Element.ALIGN_CENTER, 0, 6);
            p(doc, r.mention(), F_TXT, Element.ALIGN_CENTER, 0, 12);
        } else {
            for (String l : d.entete()) p(doc, l, F_B, Element.ALIGN_CENTER, 0, 2);
            p(doc, d.titre(), F_TITRE, Element.ALIGN_CENTER, 40, 24);
            Identite i = d.identite();
            PdfPTable t = new PdfPTable(new float[]{2, 3});
            t.setWidthPercentage(100);
            String[][] lignes = {{"Université / Centre de recherche / EPS", i.universite()}, {"Établissement", i.etablissement()},
                    {"Dénomination du LR/UR", i.denomination()}, {"Code de la structure", i.code()}, {"Chef du LR/UR", i.chef()},
                    {"Grade", i.grade()}, {"Fonction administrative", i.fonction()}, {"Téléphone fixe", i.telephone()},
                    {"Fax", i.fax()}, {"E-mail", i.email()}, {"Site web du LR/UR", i.siteWeb()}};
            for (String[] l : lignes) { t.addCell(cell(l[0], F_CELL_B, GRIS)); t.addCell(cell(l[1], F_CELL, null)); }
            doc.add(t);
            doc.newPage();

            p(doc, "Résumé des activités du LR/UR", F_H1, Element.ALIGN_LEFT, 0, 6);
            p(doc, "Historique du LR/UR", F_B, Element.ALIGN_LEFT, 6, 2);
            p(doc, d.resume().historique(), F_TXT, Element.ALIGN_JUSTIFIED, 0, 4);
            p(doc, "Thématiques essentielles de recherche", F_B, Element.ALIGN_LEFT, 6, 2);
            for (String th : d.resume().thematiques()) p(doc, "•  " + th, F_TXT, Element.ALIGN_LEFT, 0, 1);
            p(doc, "Bilan global des activités de recherche", F_B, Element.ALIGN_LEFT, 6, 2);
            p(doc, d.resume().bilan(), F_TXT, Element.ALIGN_JUSTIFIED, 0, 10);

            p(doc, "Récapitulatif du rapport " + d.annee(), F_H1, Element.ALIGN_LEFT, 10, 6);
            boolean n1 = d.anneePrecedenteDisponible();
            PdfPTable rt = new PdfPTable(n1 ? new float[]{6, 1.2f, 1.2f} : new float[]{6, 1.2f});
            rt.setWidthPercentage(100);
            rt.setHeaderRows(1);
            rt.addCell(cell("Rubrique", F_HEAD, BLEU));
            rt.addCell(cell("Nombre " + d.annee(), F_HEAD, BLEU));
            if (n1) rt.addCell(cell("Rappel " + (d.annee() - 1), F_HEAD, BLEU));
            for (Groupe g : d.groupes()) {
                PdfPCell gc = cell(g.numero() + " – " + g.titre(), F_CELL_B, GRIS);
                gc.setColspan(n1 ? 3 : 2);
                rt.addCell(gc);
                for (Section s : g.sections()) {
                    rt.addCell(cell(s.numero() + " – " + s.libelleRecap(), F_CELL, null));
                    rt.addCell(cell(Rendu.recapValeur(d, s), F_CELL_B, null));
                    if (n1) rt.addCell(cell(s.valeurN1() == null ? "—" : s.valeurN1(), F_CELL, null));
                }
            }
            doc.add(rt);
            p(doc, "Toutes les valeurs sont calculées à partir des enregistrements validés ; aucune n'est saisie à la main.",
                    new Font(Font.HELVETICA, 7, Font.ITALIC), Element.ALIGN_LEFT, 2, 0);
            doc.newPage();
        }

        for (Groupe g : d.groupes()) {
            if (g.sections().isEmpty()) continue;
            p(doc, (g.numero().isEmpty() ? "" : g.numero() + " – ") + g.titre(), F_H1, Element.ALIGN_LEFT, 10, 4);
            for (Section s : g.sections()) {
                p(doc, s.numero() + " – " + s.titre(), F_H2, Element.ALIGN_LEFT, 8, 4);
                List<String> cols = r.colonnes(s);
                PdfPTable t = new PdfPTable(largeurs(s.code(), cols.size()));
                t.setWidthPercentage(100);
                t.setHeaderRows(1);
                t.setSplitLate(false);
                for (String c : cols) t.addCell(cell(c, F_HEAD, BLEU));
                if (s.lignes().isEmpty()) {
                    PdfPCell nc = cell("Néant", F_CELL, null);
                    nc.setColspan(cols.size());
                    t.addCell(nc);
                }
                for (List<String> l : s.lignes()) for (String v : r.ligne(s, l)) t.addCell(cell(v, F_CELL, null));
                if (s.ligneTotal() != null) for (String v : s.ligneTotal()) t.addCell(cell(v, F_CELL_B, GRIS));
                doc.add(t);
                if (s.ligneTotal() == null) p(doc, s.libelleTotal() + " : " + s.valeur(), F_B, Element.ALIGN_LEFT, 3, 6);
            }
        }

        if (r.provisoire() && !r.fiche()) {
            doc.newPage();
            p(doc, "Annexe – Points à corriger avant la version finale", F_H1, Element.ALIGN_LEFT, 0, 6);
            if (d.controles().isEmpty()) p(doc, "Aucun point bloquant ni avertissement.", F_TXT, Element.ALIGN_LEFT, 0, 0);
            for (Controle c : d.controles()) {
                Paragraph para = new Paragraph();
                para.add(new Chunk(c.bloquant() ? "[Bloquant] " : "[Avertissement] ",
                        new Font(Font.HELVETICA, 10, Font.BOLD, c.bloquant() ? new Color(0xB0, 0, 0x20) : new Color(0x8A, 0x61, 0))));
                para.add(new Chunk(sur(c.message()), F_TXT));
                para.setSpacingBefore(6);
                doc.add(para);
                for (String e : c.elements().stream().limit(40).toList()) p(doc, "   –  " + e, F_CELL, Element.ALIGN_LEFT, 0, 0);
                if (c.elements().size() > 40) p(doc, "   … et " + (c.elements().size() - 40) + " autre(s).", F_CELL, Element.ALIGN_LEFT, 0, 0);
            }
        }
        doc.close();
        return out.toByteArray();
    }

    private static float[] largeurs(String code, int n) {
        float[] w = new float[n];
        java.util.Arrays.fill(w, 1);
        switch (code) {
            case "PUBLICATIONS" -> { if (n == 5) w = new float[]{4, 3, 0.8f, 1, 3.5f}; }
            case "THESES" -> { if (n == 5) w = new float[]{3, 0.8f, 1, 6, 1.8f}; }
            case "MASTERS" -> { if (n == 4) w = new float[]{0.8f, 1, 5, 1.8f}; }
            case "CORPS_A", "CORPS_B", "CADRES" -> { if (n == 5) w = new float[]{1.6f, 2, 1, 3, 2.4f}; else if (n == 4) w = new float[]{1.6f, 2, 3, 2.4f}; }
            case "DOCTORANTS", "ETUDIANTS" -> { if (n == 4) w = new float[]{2, 1, 3, 2.4f}; else if (n == 3) w = new float[]{2, 3, 2.4f}; }
            case "OUVRAGES", "CHAPITRES" -> { if (n == 5) w = new float[]{3.5f, 0.7f, 1.4f, 2.5f, 2.8f}; }
            case "BUDGET" -> w = new float[]{3, 1.5f, 1.5f, 1.8f};
            default -> {}
        }
        return w;
    }

    private static void p(Document doc, String t, Font f, int align, float avant, float apres) {
        Paragraph p = new Paragraph(sur(t), f);
        p.setAlignment(align);
        p.setSpacingBefore(avant);
        p.setSpacingAfter(apres);
        doc.add(p);
    }

    private static PdfPCell cell(String t, Font f, Color fond) {
        PdfPCell c = new PdfPCell(new Phrase(sur(t), f));
        c.setPadding(3);
        if (fond != null) c.setBackgroundColor(fond);
        return c;
    }

    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** Les polices standard PDF couvrent le jeu Windows-1252 : on remplace les rares autres caractères. */
    static String sur(String s) {
        if (s == null) return "";
        CharsetEncoder enc = CP1252.newEncoder();
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (enc.canEncode(ch)) b.append(ch);
            else if (cp == 0x2212 || cp == 0x2010 || cp == 0x2011) b.append('-');
            else if (cp == 0x202F || cp == 0x2009) b.append(' ');
            else b.append('?');
        });
        return b.toString();
    }

    /** Filigrane « PROVISOIRE – NON VALIDÉ » sur chaque page, mention d'en-tête et numéro de page. */
    private static final class Evenements extends PdfPageEventHelper {
        private final RapportData d;
        private final Rendu r;

        Evenements(RapportData d, Rendu r) { this.d = d; this.r = r; }

        @Override
        public void onEndPage(PdfWriter w, Document doc) {
            Rectangle page = doc.getPageSize();
            PdfContentByte haut = w.getDirectContent();
            ColumnText.showTextAligned(haut, Element.ALIGN_RIGHT, new Phrase(sur(r.mention()), new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY)),
                    page.getRight() - 40, page.getTop() - 30, 0);
            ColumnText.showTextAligned(haut, Element.ALIGN_CENTER,
                    new Phrase(sur("LIMTIC – " + (r.fiche() ? r.titreFiche() : d.titre()) + " – page " + w.getPageNumber()), new Font(Font.HELVETICA, 7)),
                    (page.getLeft() + page.getRight()) / 2, page.getBottom() + 25, 0);
            if (r.provisoire()) {
                ColumnText.showTextAligned(haut, Element.ALIGN_LEFT, new Phrase(sur(Rendu.FILIGRANE), new Font(Font.HELVETICA, 7, Font.BOLD, new Color(0xB0, 0, 0x20))),
                        page.getLeft() + 40, page.getTop() - 30, 0);
                PdfContentByte dessous = w.getDirectContentUnder();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.18f);
                dessous.saveState();
                dessous.setGState(gs);
                ColumnText.showTextAligned(dessous, Element.ALIGN_CENTER,
                        new Phrase(sur(Rendu.FILIGRANE), new Font(Font.HELVETICA, 54, Font.BOLD, new Color(0xB0, 0, 0x20))),
                        (page.getLeft() + page.getRight()) / 2, (page.getTop() + page.getBottom()) / 2, 45);
                dessous.restoreState();
            }
        }
    }
}
