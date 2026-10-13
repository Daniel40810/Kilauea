package com.dan.kilauea.monitor;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Malt die Tafel des Messnetzes und die Skala des Wärmebilds (Phase 14) mit Java2D, im Stil der Infotafel:
 * dunkles Glas, Goldkante oben und links. Ohne Grafikkarte, darum auch für Prüfung und Werkzeuge.
 * Jeder Wert steht mit seiner Marke da (M, L, A, G); die Quelle steht klein unter jedem Instrument.
 */
public final class MonitorPainter {
    private MonitorPainter() { }

    public static final Color GLASS = new Color(15, 17, 20, 205), GOLD = new Color(236, 192, 100), LAVA = new Color(255, 122, 51),
            INK = new Color(232, 230, 227), MUTED = new Color(160, 164, 170), LINE = new Color(60, 66, 74), GRID = new Color(255, 255, 255, 22),
            SEA = new Color(95, 180, 190), STEAM = new Color(200, 194, 208), FERN = new Color(127, 184, 121), GLOW = new Color(240, 180, 74);

    /** Die Tafel des Messnetzes, w breit, höchstens maxH hoch (die Abschnitte schrumpfen dann). */
    public static BufferedImage paint(Monitor.View v, int w, int maxH) {
        float s = w / 440f;
        int pad = Math.round(14 * s);
        int head = Math.round(30 * s), seisH = Math.round((v.drum() ? 112 : 96) * s), tiltH = Math.round(64 * s), gpsH = Math.round(36 * s), gasH = Math.round(50 * s);
        int titleH = Math.round(20 * s), srcH = Math.round(14 * s), gapH = Math.round(9 * s), foot = Math.round(22 * s);
        int h = pad + head + 4 * (titleH + srcH + gapH) + seisH + tiltH + gpsH + gasH + foot + pad;
        if (h > maxH) {
            float k = Math.max(0.55f, (maxH - (pad * 2 + head + foot + 4 * (titleH + srcH + gapH))) / (float) (seisH + tiltH + gpsH + gasH));
            seisH = Math.round(seisH * k); tiltH = Math.round(tiltH * k); gpsH = Math.round(gpsH * k); gasH = Math.round(gasH * k);
            h = pad + head + 4 * (titleH + srcH + gapH) + seisH + tiltH + gpsH + gasH + foot + pad;
        }
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = glass(im, s);
        Font eyebrow = new Font(Font.MONOSPACED, Font.BOLD, Math.round(11 * s));
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(12 * s));
        Font val = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(12 * s));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(10 * s));
        Font tiny = new Font(Font.MONOSPACED, Font.PLAIN, Math.round(9 * s));
        int x0 = pad, cw = w - 2 * pad;
        int y = pad;
        g.setFont(eyebrow);
        g.setColor(LAVA);
        y += g.getFontMetrics().getAscent();
        com.dan.kilauea.ui.Glyphs.draw(g, "MESSNETZ", x0, y);
        g.setFont(small);
        g.setColor(MUTED);
        String sp = v.readings().get("span").formatted() + " · Simulationszeit";
        com.dan.kilauea.ui.Glyphs.draw(g, sp, x0 + cw - g.getFontMetrics().stringWidth(sp), y);
        y = pad + head;
        double tEnd = v.now(), tBeg = v.now() - v.span();

        // ---------------------------------------------------------------- Seismograf
        Reading st = v.readings().get("seis.station");
        y = title(g, title, val, x0, cw, y, titleH, "SEISMOGRAF · " + st.formatted() + " " + st.prov().tag(),
                v.drum() ? v.readings().get("seis.tremor") : v.readings().get("seis.rsam"), INK, s);
        if (v.drum()) drum(g, v, x0, y, cw, seisH, s, tiny);
        else rsam(g, v, x0, y, cw, seisH, tBeg, tEnd, s, tiny);
        y += seisH;
        Reading cnt = v.readings().get("seis.count"), mx = v.readings().get("seis.max");
        String q = "Beben " + cnt.formatted() + (mx != null ? ", größtes " + mx.formatted() : "") + " " + cnt.prov().tag();
        y = source(g, small, x0, cw, y, srcH, q + " · Ausschlag nach Hutton & Boore, Dauer nach Lee u. a. [L]; Tremor [G]");
        y += gapH;

        // ---------------------------------------------------------------- Neigung
        Reading ru = v.readings().get("tilt.uwd"), rs = v.readings().get("tilt.sdh");
        y = title2(g, title, val, x0, cw, y, titleH, "NEIGUNG · µrad", new Reading[]{ru, rs}, new Color[]{GOLD, SEA}, new String[]{"UWD", "SDH"}, s);
        lines(g, x0, y, cw, tiltH, tBeg, tEnd, new double[][][]{v.uwd(), v.sdh()}, new Color[]{GOLD, SEA}, false, s, tiny, false);
        y += tiltH;
        y = source(g, small, x0, cw, y, srcH, "Mogi-Quellen der Speicher [A] · aufwärts: Gipfel hebt sich · UWD Lage [L], SDH genähert [G]");
        y += gapH;

        // ---------------------------------------------------------------- GPS
        Reading rb = v.readings().get("gps.base");
        y = title(g, title, val, x0, cw, y, titleH, "GPS · ABSTAND UWEV–CRIM", rb, FERN, s);
        lines(g, x0, y, cw, gpsH, tBeg, tEnd, new double[][][]{v.base()}, new Color[]{FERN}, false, s, tiny, false);
        y += gpsH;
        y = source(g, small, x0, cw, y, srcH, "quer über die Caldera, länger = Gipfel bläht sich auf [L Lesart HVO, A Wert]");
        y += gapH;

        // ---------------------------------------------------------------- Gas
        Reading so2 = v.readings().get("so2");
        y = title(g, title, val, x0, cw, y, titleH, "SO₂-AUSSTOSS · t/d", so2, GLOW, s);
        lines(g, x0, y, cw, gasH, tBeg, tEnd, new double[][][]{v.so2()}, new Color[]{GLOW}, true, s, tiny, true);
        y += gasH;
        y = source(g, small, x0, cw, y, srcH, so2.source() + " " + so2.prov().tag() + " · gemessen 2018: 50 000–200 000 [L]");

        // ---------------------------------------------------------------- Fuß
        g.setFont(small);
        g.setColor(MUTED);
        String f = "Zeit läuft nach rechts · " + Prov.legend();
        com.dan.kilauea.ui.Glyphs.draw(g, clip(f, g.getFontMetrics(), cw), x0, h - pad - g.getFontMetrics().getDescent());
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ Teile

    static Graphics2D glass(BufferedImage im, float s) {
        int w = im.getWidth(), h = im.getHeight();
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setComposite(AlphaComposite.Src);
        float r = 8 * s;
        g.setColor(GLASS);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, r, r));
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new GradientPaint(0, 0, GOLD, w * 0.7f, 0, new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 0)));
        g.setStroke(new BasicStroke(2 * s));
        g.drawLine(Math.round(r / 2), 1, w, 1);
        g.setPaint(new GradientPaint(0, 0, GOLD, 0, h, new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40)));
        g.drawLine(1, Math.round(r / 2), 1, h - Math.round(r / 2));
        return g;
    }

    private static int title(Graphics2D g, Font tf, Font vf, int x, int cw, int y, int th, String name, Reading r, Color c, float s) {
        g.setFont(tf);
        g.setColor(GOLD);
        int base = y + g.getFontMetrics().getAscent();
        com.dan.kilauea.ui.Glyphs.draw(g, clip(name, g.getFontMetrics(), (int) (cw * 0.62)), x, base);
        if (r != null) {
            g.setFont(vf);
            String v = r.withTag();
            int vw = g.getFontMetrics().stringWidth(v);
            g.setColor(c);
            com.dan.kilauea.ui.Glyphs.draw(g, v, x + cw - vw, base);
        }
        return y + th;
    }

    private static int title2(Graphics2D g, Font tf, Font vf, int x, int cw, int y, int th, String name, Reading[] rs, Color[] cs, String[] names, float s) {
        g.setFont(tf);
        g.setColor(GOLD);
        int base = y + g.getFontMetrics().getAscent();
        com.dan.kilauea.ui.Glyphs.draw(g, name, x, base);
        g.setFont(vf);
        FontMetrics fm = g.getFontMetrics();
        int xr = x + cw;
        for (int i = rs.length - 1; i >= 0; i--) {
            String v = names[i] + " " + rs[i].withTag();
            int vw = fm.stringWidth(v);
            xr -= vw;
            g.setColor(cs[i]);
            com.dan.kilauea.ui.Glyphs.draw(g, v, xr, base);
            xr -= Math.round(10 * s);
        }
        return y + th;
    }

    private static int source(Graphics2D g, Font f, int x, int cw, int y, int sh, String text) {
        g.setFont(f);
        g.setColor(MUTED);
        com.dan.kilauea.ui.Glyphs.draw(g, clip(text, g.getFontMetrics(), cw), x, y + g.getFontMetrics().getAscent() + Math.round(sh * 0.15f));
        return y + sh;
    }

    /** Die Trommel: sechs Zeilen zu je einer Minute, abwechselnd zwei Farben wie auf Papier. */
    private static void drum(Graphics2D g, Monitor.View v, int x, int y, int w, int h, float s, Font tiny) {
        int n = Monitor.DRUM_LINES;
        float lh = h / (float) n;
        g.setFont(tiny);
        FontMetrics fm = g.getFontMetrics();
        int lab = fm.stringWidth("−5 min") + Math.round(6 * s);
        int tw = w - lab;
        // Maßstab: eine Zeile hat 2 mm, abgeschnitten bei 1,3 Zeilenabständen [G]
        double scale = lh / 2.0 / 2.0;
        g.setStroke(new BasicStroke(Math.max(1f, 0.9f * s)));
        for (int l = 0; l < n; l++) {
            float cy = y + lh * (l + 0.5f);
            g.setColor(MUTED);
            String t = l == n - 1 ? "jetzt" : String.format(Locale.GERMAN, "−%d min", n - 1 - l);
            com.dan.kilauea.ui.Glyphs.draw(g, t, x, cy + fm.getAscent() / 2f - 1);
            g.setColor(GRID);
            g.drawLine(x + lab, Math.round(cy), x + w, Math.round(cy));
            float[] lo = v.drumMin()[l], hi = v.drumMax()[l];
            g.setColor(l % 2 == 0 ? INK : SEA);
            int full = 1;
            for (float[] row : v.drumMin()) full = Math.max(full, row.length);
            for (int i = 0; i < lo.length; i++) {
                float xx = x + lab + tw * i / (float) full;
                float a = (float) Math.max(-1.3 * lh, Math.min(1.3 * lh, lo[i] * scale)), b = (float) Math.max(-1.3 * lh, Math.min(1.3 * lh, hi[i] * scale));
                g.drawLine(Math.round(xx), Math.round(cy - b), Math.round(xx), Math.round(cy - a - 0.5f));
            }
        }
    }

    /** RSAM als Säulen, darüber die Beben als Punkte nach Magnitude. */
    private static void rsam(Graphics2D g, Monitor.View v, int x, int y, int w, int h, double tBeg, double tEnd, float s, Font tiny) {
        frame(g, x, y, w, h, tBeg, tEnd, s, tiny, true);
        double mx = 200;
        for (double r : v.rsam()) mx = Math.max(mx, r);
        mx = niceCeil(mx);
        int bottom = y + h;
        double span = tEnd - tBeg;
        g.setColor(new Color(LAVA.getRed(), LAVA.getGreen(), LAVA.getBlue(), 120));
        for (int i = 0; i < v.rsam().length; i++) {
            double a = v.rsamT()[i], b = Math.min(tEnd, a + v.rsamBin());
            int xa = x + (int) Math.round(w * (a - tBeg) / span), xb = x + (int) Math.round(w * (b - tBeg) / span);
            int hh = (int) Math.round((h - 2) * Math.min(1, v.rsam()[i] / mx));
            g.fillRect(xa, bottom - hh, Math.max(1, xb - xa - 1), hh);
        }
        // Beben: Punkte, Höhe nach Magnitude (0 bis 5,5), Größe nach Magnitude
        for (int i = 0; i < v.quakeT().length; i++) {
            double m = v.quakeM()[i];
            double xx = x + w * (v.quakeT()[i] - tBeg) / span, yy = bottom - (h - 4) * Math.max(0, Math.min(1, m / 5.5)) - 2;
            double r = (0.8 + 0.9 * Math.max(0, m)) * s;
            g.setColor(m >= 4 ? LAVA : m >= 2.5 ? GLOW : new Color(232, 230, 227, 150));
            g.fill(new Ellipse2D.Double(xx - r, yy - r, 2 * r, 2 * r));
        }
        g.setFont(tiny);
        g.setColor(MUTED);
        String lab = String.format(Locale.GERMAN, "RSAM bis %,.0f · Punkte: Beben M 0–5,5", mx);
        com.dan.kilauea.ui.Glyphs.draw(g, lab, x + Math.round(4 * s), y + g.getFontMetrics().getAscent() + 1);
    }

    /** Zeitreihen in einem Rahmen; log: logarithmische Achse 10 … 300 000. */
    private static void lines(Graphics2D g, int x, int y, int w, int h, double tBeg, double tEnd, double[][][] series, Color[] cs, boolean log, float s, Font tiny, boolean axis) {
        frame(g, x, y, w, h, tBeg, tEnd, s, tiny, axis);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[][] sr : series) for (double val : sr[1]) { lo = Math.min(lo, val); hi = Math.max(hi, val); }
        if (lo > hi) { lo = -1; hi = 1; }
        if (log) {
            lo = 10;
            hi = 300_000;
        } else {
            double mid = 0.5 * (lo + hi), half = Math.max(0.5 * (hi - lo) * 1.15, 0.5);
            lo = mid - half;
            hi = mid + half;
        }
        g.setFont(tiny);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(MUTED);
        if (log) {
            for (double gv : new double[]{100, 1000, 10_000, 100_000}) {
                int yy = (int) Math.round(y + h - h * (Math.log10(gv) - 1) / (Math.log10(hi) - 1));
                g.setColor(GRID);
                g.drawLine(x, yy, x + w, yy);
                g.setColor(MUTED);
                String t = gv >= 1000 ? String.format(Locale.GERMAN, "%,.0f", gv) : String.format(Locale.GERMAN, "%.0f", gv);
                com.dan.kilauea.ui.Glyphs.draw(g, t, x + 3, yy - 2);
            }
        } else {
            com.dan.kilauea.ui.Glyphs.draw(g, fmt(hi), x + 3, y + fm.getAscent());
            com.dan.kilauea.ui.Glyphs.draw(g, fmt(lo), x + 3, y + h - 2);
        }
        double span = tEnd - tBeg;
        g.setStroke(new BasicStroke(Math.max(1.2f, 1.5f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        java.awt.Shape clip = g.getClip();
        g.clipRect(x, y - 1, w + 1, h + 2);
        for (int k = 0; k < series.length; k++) {
            double[] ts = series[k][0], vs = series[k][1];
            if (ts.length == 0) continue;
            Path2D.Double p = new Path2D.Double();
            for (int i = 0; i < ts.length; i++) {
                double xx = x + w * (ts[i] - tBeg) / span;
                double val = log ? (Math.log10(Math.max(10, vs[i])) - 1) / (Math.log10(hi) - 1) : (vs[i] - lo) / (hi - lo);
                double yy = y + h - h * Math.max(-0.02, Math.min(1.02, val));
                if (i == 0) p.moveTo(xx, yy); else p.lineTo(xx, yy);
            }
            g.setColor(cs[k]);
            g.draw(p);
        }
        g.setClip(clip);
    }

    /** Rahmen und senkrechte Hilfslinien in runden Abständen der Zeit. */
    private static void frame(Graphics2D g, int x, int y, int w, int h, double tBeg, double tEnd, float s, Font tiny, boolean labels) {
        g.setColor(new Color(255, 255, 255, 10));
        g.fillRect(x, y, w, h);
        g.setColor(LINE);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, w, h);
        double span = tEnd - tBeg;
        double step = span <= 3600 ? 600 : span <= 6 * 3600 ? 3600 : span <= 86400 ? 6 * 3600 : span <= 7 * 86400 ? 86400 : 7 * 86400;
        g.setColor(GRID);
        for (double t = tEnd - step; t > tBeg; t -= step) {
            int xx = x + (int) Math.round(w * (t - tBeg) / span);
            g.drawLine(xx, y, xx, y + h);
        }
    }

    private static String fmt(double v) {
        String t = Math.abs(v) >= 10 ? String.format(Locale.GERMAN, "%.0f", v) : String.format(Locale.GERMAN, "%.1f", v);
        return t.replace('-', '−');
    }

    private static double niceCeil(double v) {
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 5, 10}) if (m * p >= v) return m * p;
        return 10 * p;
    }

    static String clip(String s, FontMetrics fm, int width) {
        s = com.dan.kilauea.ui.Glyphs.fit(fm.getFont(), s);
        if (fm.stringWidth(s) <= width) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ------------------------------------------------------------------ Wärmebild

    /**
     * Skala und Messkreuz des Wärmebilds: Farbbalken mit Marken, darunter der Wert im Kreuz mit Herkunft und was
     * dort liegt.
     */
    public static BufferedImage paintThermal(Reading probe, Reading what, int w) {
        float s = w / 360f;
        int pad = Math.round(12 * s);
        Font eyebrow = new Font(Font.MONOSPACED, Font.BOLD, Math.round(11 * s));
        Font big = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(18 * s));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(10 * s));
        Font tiny = new Font(Font.MONOSPACED, Font.PLAIN, Math.round(9 * s));
        int barH = Math.round(12 * s);
        int h = pad + Math.round(16 * s) + barH + Math.round(16 * s) + Math.round(26 * s) + Math.round(30 * s) + pad;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = glass(im, s);
        int x0 = pad, cw = w - 2 * pad, y = pad;
        g.setFont(eyebrow);
        g.setColor(LAVA);
        y += g.getFontMetrics().getAscent();
        com.dan.kilauea.ui.Glyphs.draw(g, "WÄRMEBILD", x0, y);
        g.setFont(small);
        g.setColor(MUTED);
        String sc = "Skala −20 … 1 200 °C " + Prov.G.tag();
        com.dan.kilauea.ui.Glyphs.draw(g, sc, x0 + cw - g.getFontMetrics().stringWidth(sc), y);
        y += Math.round(6 * s);
        for (int i = 0; i < cw; i++) {
            float[] c = Thermal.palette(i / (float) (cw - 1));
            g.setColor(new Color(c[0], c[1], c[2]));
            g.drawLine(x0 + i, y, x0 + i, y + barH);
        }
        g.setFont(tiny);
        FontMetrics fm = g.getFontMetrics();
        for (double t : new double[]{0, 20, 40, 60, 200, 500, 1000}) {
            int xx = x0 + (int) Math.round(cw * Thermal.scale(t));
            g.setColor(INK);
            g.drawLine(xx, y + barH, xx, y + barH + Math.round(3 * s));
            String lab = String.format(Locale.GERMAN, "%.0f", t);
            com.dan.kilauea.ui.Glyphs.draw(g, lab, Math.min(x0 + cw - fm.stringWidth(lab), Math.max(x0, xx - fm.stringWidth(lab) / 2)), y + barH + Math.round(3 * s) + fm.getAscent());
        }
        y += barH + Math.round(16 * s);
        g.setFont(big);
        g.setColor(INK);
        y += g.getFontMetrics().getAscent();
        String pv = probe == null ? "—" : probe.formatted();
        com.dan.kilauea.ui.Glyphs.draw(g, "⌖ " + pv, x0, y);
        if (probe != null) {
            g.setFont(small);
            g.setColor(GOLD);
            com.dan.kilauea.ui.Glyphs.draw(g, probe.prov().tag(), x0 + g.getFontMetrics(big).stringWidth("⌖ " + pv) + Math.round(6 * s), y);
        }
        y += Math.round(8 * s);
        g.setFont(small);
        g.setColor(MUTED);
        if (what != null) {
            y += g.getFontMetrics().getAscent();
            com.dan.kilauea.ui.Glyphs.draw(g, clip(what.formatted() + " " + what.prov().tag(), g.getFontMetrics(), cw), x0, y);
        }
        y += Math.round(13 * s);
        com.dan.kilauea.ui.Glyphs.draw(g, clip("Helligkeitstemperatur im Kreuz; Kruste und Risse nach Stefan–Boltzmann gemischt [A]", g.getFontMetrics(), cw), x0, y);
        g.dispose();
        return im;
    }
}
