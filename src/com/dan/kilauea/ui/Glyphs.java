package com.dan.kilauea.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.text.Normalizer;

/**
 * Schrift mit Ersatzzeichen: Nicht jede Schrift kennt das ʻokina (U+02BB) oder die Makrons der
 * hawaiischen Namen. Georgia auf Windows zeichnet das ʻokina als leeres Kästchen (Statusbild Phase 14:
 * „Halema□uma□u“). Vor dem Zeichnen wird jedes Zeichen, das die Schrift nicht hat, ersetzt: ʻokina durch
 * das öffnende Hochkomma ‘, der Apostroph ʼ durch ’, sonst der Buchstabe ohne Makron (ā → a). Was die
 * Schrift kann, bleibt unverändert.
 */
public final class Glyphs {
    private Glyphs() { }

    /** Text so umschreiben, dass die Schrift f jedes Zeichen zeigen kann. */
    public static String fit(Font f, String s) {
        if (s == null || s.isEmpty() || f.canDisplayUpTo(s) < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (f.canDisplay(cp)) { b.appendCodePoint(cp); continue; }
            String alt = switch (cp) {
                case 0x02BB, 0x02BD -> "‘";   // ʻokina → ‘
                case 0x02BC -> "’";           // ʼ → ’
                case 0x2011 -> "-";
                case 0x2212 -> "-";
                case 0x2009, 0x202F -> " ";
                default -> null;
            };
            if (alt != null && f.canDisplayUpTo(alt) < 0) { b.append(alt); continue; }
            if (alt != null && (cp == 0x02BB || cp == 0x02BD || cp == 0x02BC)) { b.append('\''); continue; }
            // Buchstabe mit Makron oder Akzent: Grundbuchstabe
            String base = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            if (!base.isEmpty() && f.canDisplayUpTo(base) < 0) b.append(base);
            else b.append('?');
        }
        return b.toString();
    }

    /** Wie {@code g.drawString}, aber mit Ersatzzeichen für die aktuelle Schrift. */
    public static void draw(Graphics2D g, String s, int x, int y) { g.drawString(fit(g.getFont(), s), x, y); }

    public static void draw(Graphics2D g, String s, float x, float y) { g.drawString(fit(g.getFont(), s), x, y); }
}
