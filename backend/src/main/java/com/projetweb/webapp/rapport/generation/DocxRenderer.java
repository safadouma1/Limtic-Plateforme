package com.projetweb.webapp.rapport.generation;

import com.projetweb.webapp.rapport.service.RapportData;
import com.projetweb.webapp.rapport.service.RapportData.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * Rapport au format Word (.docx), conforme à la structure du modèle 2025 : page de garde, résumé,
 * récapitulatif chiffré, tableaux détaillés (en-têtes répétés d'une page à l'autre, aucune troncature),
 * phrases de totaux, filigrane « PROVISOIRE – NON VALIDÉ » sur chaque page des versions provisoires.
 */
public final class DocxRenderer {
    private static final String BLEU = "1F3A5F";
    private static final String GRIS = "E8EEF5";

    private DocxRenderer() {}

    public static byte[] rendre(RapportData d, Rendu r) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            mettreEnPage(doc);
            enTetePied(doc, d, r);
            if (r.fiche()) {
                titre(doc, r.titreFiche(), 16, true, ParagraphAlignment.CENTER);
                texte(doc, r.mention(), false, ParagraphAlignment.CENTER);
            } else {
                pageDeGarde(doc, d);
                resume(doc, d);
                recapitulatif(doc, d);
            }
            for (Groupe g : d.groupes()) {
                if (g.sections().isEmpty()) continue;
                titre(doc, (g.numero().isEmpty() ? "" : g.numero() + " – ") + g.titre(), 13, true, ParagraphAlignment.LEFT);
                for (Section s : g.sections()) section(doc, s, r);
            }
            if (r.provisoire() && !r.fiche()) annexe(doc, d);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void mettreEnPage(XWPFDocument doc) {
        CTSectPr sp = doc.getDocument().getBody().addNewSectPr();
        CTPageSz sz = sp.addNewPgSz();
        sz.setW(BigInteger.valueOf(11906));
        sz.setH(BigInteger.valueOf(16838));
        CTPageMar m = sp.addNewPgMar();
        m.setTop(BigInteger.valueOf(1134)); m.setBottom(BigInteger.valueOf(1134));
        m.setLeft(BigInteger.valueOf(1020)); m.setRight(BigInteger.valueOf(1020));
        m.setHeader(BigInteger.valueOf(567)); m.setFooter(BigInteger.valueOf(567));
    }

    private static void enTetePied(XWPFDocument doc, RapportData d, Rendu r) {
        XWPFHeaderFooterPolicy policy = doc.createHeaderFooterPolicy();
        if (r.provisoire()) policy.createWatermark(Rendu.FILIGRANE);
        XWPFHeader h = policy.getDefaultHeader() != null ? policy.getDefaultHeader() : doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph p = h.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun run = p.createRun();
        run.setFontSize(8);
        run.setColor("666666");
        run.setText(r.mention());
        if (r.provisoire()) {
            XWPFRun w = p.createRun();
            w.setFontSize(8); w.setBold(true); w.setColor("B00020");
            w.setText("  —  " + Rendu.FILIGRANE);
        }
        XWPFFooter f = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph fp = f.createParagraph();
        fp.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun fr = fp.createRun();
        fr.setFontSize(8);
        fr.setText("LIMTIC – " + (r.fiche() ? r.titreFiche() : d.titre()) + " – page ");
        CTSimpleField page = fp.getCTP().addNewFldSimple();
        page.setInstr("PAGE");
        page.addNewR().addNewT().setStringValue("1");
    }

    private static void pageDeGarde(XWPFDocument doc, RapportData d) {
        for (String l : d.entete()) texte(doc, l, true, ParagraphAlignment.CENTER);
        espace(doc, 2);
        titre(doc, d.titre(), 22, true, ParagraphAlignment.CENTER);
        espace(doc, 1);
        Identite i = d.identite();
        String[][] lignes = {
                {"Université / Centre de recherche / EPS", i.universite()},
                {"Établissement", i.etablissement()},
                {"Dénomination du LR/UR", i.denomination()},
                {"Code de la structure", i.code()},
                {"Chef du LR/UR", i.chef()},
                {"Grade", i.grade()},
                {"Fonction administrative", i.fonction()},
                {"Téléphone fixe", i.telephone()},
                {"Fax", i.fax()},
                {"E-mail", i.email()},
                {"Site web du LR/UR", i.siteWeb()}};
        XWPFTable t = doc.createTable(lignes.length, 2);
        t.setWidth("100%");
        for (int k = 0; k < lignes.length; k++) {
            cellule(t.getRow(k).getCell(0), lignes[k][0], true, GRIS);
            cellule(t.getRow(k).getCell(1), lignes[k][1] == null ? "" : lignes[k][1], false, null);
        }
        saut(doc);
    }

    private static void resume(XWPFDocument doc, RapportData d) {
        titre(doc, "Résumé des activités du LR/UR", 14, true, ParagraphAlignment.LEFT);
        sousTitre(doc, "Historique du LR/UR");
        texte(doc, d.resume().historique(), false, ParagraphAlignment.BOTH);
        sousTitre(doc, "Thématiques essentielles de recherche");
        for (String t : d.resume().thematiques()) texte(doc, "•  " + t, false, ParagraphAlignment.LEFT);
        sousTitre(doc, "Bilan global des activités de recherche");
        texte(doc, d.resume().bilan(), false, ParagraphAlignment.BOTH);
    }

    private static void recapitulatif(XWPFDocument doc, RapportData d) {
        titre(doc, "Récapitulatif du rapport " + d.annee(), 14, true, ParagraphAlignment.LEFT);
        int nb = d.groupes().stream().mapToInt(g -> g.sections().size() + 1).sum();
        boolean n1 = d.anneePrecedenteDisponible();
        XWPFTable t = doc.createTable(nb + 1, n1 ? 3 : 2);
        t.setWidth("100%");
        XWPFTableRow h = t.getRow(0);
        h.setRepeatHeader(true);
        cellule(h.getCell(0), "Rubrique", true, BLEU, true);
        cellule(h.getCell(1), "Nombre " + d.annee(), true, BLEU, true);
        if (n1) cellule(h.getCell(2), "Rappel " + (d.annee() - 1), true, BLEU, true);
        int k = 1;
        for (Groupe g : d.groupes()) {
            XWPFTableRow gr = t.getRow(k++);
            cellule(gr.getCell(0), g.numero() + " – " + g.titre(), true, GRIS);
            cellule(gr.getCell(1), "", false, GRIS);
            if (n1) cellule(gr.getCell(2), "", false, GRIS);
            for (Section s : g.sections()) {
                XWPFTableRow row = t.getRow(k++);
                cellule(row.getCell(0), s.numero() + " – " + s.libelleRecap(), false, null);
                cellule(row.getCell(1), Rendu.recapValeur(d, s), true, null);
                if (n1) cellule(row.getCell(2), s.valeurN1() == null ? "—" : s.valeurN1(), false, null);
            }
        }
        texte(doc, "Toutes les valeurs sont calculées à partir des enregistrements validés ; aucune n'est saisie à la main.", false, ParagraphAlignment.LEFT)
                .getRuns().forEach(x -> { x.setItalic(true); x.setFontSize(8); });
        saut(doc);
    }

    private static void section(XWPFDocument doc, Section s, Rendu r) {
        titre(doc, s.numero() + " – " + s.titre(), 11, true, ParagraphAlignment.LEFT);
        List<String> cols = r.colonnes(s);
        List<List<String>> lignes = s.lignes();
        int extra = s.ligneTotal() != null ? 1 : 0;
        XWPFTable t = doc.createTable(1 + Math.max(lignes.size(), 1) + extra, cols.size());
        t.setWidth("100%");
        XWPFTableRow h = t.getRow(0);
        h.setRepeatHeader(true);
        h.setCantSplitRow(true);
        for (int c = 0; c < cols.size(); c++) cellule(h.getCell(c), cols.get(c), true, BLEU, true);
        if (lignes.isEmpty()) {
            XWPFTableRow row = t.getRow(1);
            cellule(row.getCell(0), "Néant", false, null);
            for (int c = 1; c < cols.size(); c++) cellule(row.getCell(c), "", false, null);
        }
        for (int i = 0; i < lignes.size(); i++) {
            List<String> l = r.ligne(s, lignes.get(i));
            XWPFTableRow row = t.getRow(i + 1);
            for (int c = 0; c < cols.size(); c++) cellule(row.getCell(c), c < l.size() ? l.get(c) : "", false, null);
        }
        if (s.ligneTotal() != null) {
            XWPFTableRow row = t.getRow(t.getNumberOfRows() - 1);
            for (int c = 0; c < cols.size(); c++) cellule(row.getCell(c), s.ligneTotal().get(c), true, GRIS);
        } else {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(240);
            XWPFRun run = p.createRun();
            run.setBold(true);
            run.setFontSize(10);
            run.setText(s.libelleTotal() + " : " + s.valeur());
        }
    }

    private static void annexe(XWPFDocument doc, RapportData d) {
        saut(doc);
        titre(doc, "Annexe – Points à corriger avant la version finale", 13, true, ParagraphAlignment.LEFT);
        if (d.controles().isEmpty()) texte(doc, "Aucun point bloquant ni avertissement.", false, ParagraphAlignment.LEFT);
        for (Controle c : d.controles()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun a = p.createRun();
            a.setBold(true);
            a.setColor(c.bloquant() ? "B00020" : "8A6100");
            a.setText(c.bloquant() ? "[Bloquant] " : "[Avertissement] ");
            XWPFRun b = p.createRun();
            b.setText(c.message());
            for (String e : c.elements().stream().limit(40).toList()) texte(doc, "   –  " + e, false, ParagraphAlignment.LEFT).getRuns().forEach(x -> x.setFontSize(9));
            if (c.elements().size() > 40) texte(doc, "   … et " + (c.elements().size() - 40) + " autre(s).", false, ParagraphAlignment.LEFT);
        }
    }

    // ── Aides ──
    private static XWPFParagraph titre(XWPFDocument doc, String t, int taille, boolean gras, ParagraphAlignment a) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(a);
        p.setSpacingBefore(240);
        p.setSpacingAfter(120);
        p.setKeepNext(true);
        XWPFRun r = p.createRun();
        r.setBold(gras);
        r.setFontSize(taille);
        r.setColor(BLEU);
        r.setText(t == null ? "" : t);
        return p;
    }

    private static void sousTitre(XWPFDocument doc, String t) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(160);
        p.setKeepNext(true);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(11);
        r.setText(t);
    }

    private static XWPFParagraph texte(XWPFDocument doc, String t, boolean gras, ParagraphAlignment a) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(a);
        XWPFRun r = p.createRun();
        r.setBold(gras);
        r.setFontSize(10);
        r.setText(t == null ? "" : t);
        return p;
    }

    private static void espace(XWPFDocument doc, int n) { for (int i = 0; i < n; i++) doc.createParagraph(); }

    private static void saut(XWPFDocument doc) { doc.createParagraph().createRun().addBreak(BreakType.PAGE); }

    private static void cellule(XWPFTableCell c, String t, boolean gras, String fond) { cellule(c, t, gras, fond, false); }

    private static void cellule(XWPFTableCell c, String t, boolean gras, String fond, boolean blanc) {
        if (fond != null) c.setColor(fond);
        c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph p = c.getParagraphs().get(0);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontSize(9);
        r.setBold(gras);
        if (blanc) r.setColor("FFFFFF");
        r.setText(t == null ? "" : t);
    }
}
