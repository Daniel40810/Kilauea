package com.dan.kilauea.render;

import com.dan.kilauea.script.Player;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Infotafel der Drehbücher im Bild (Phase 12): unten links, dunkles Glas mit Goldkante wie das
 * Programmsymbol, darauf Drehbuch und Kapitel, Datum, Überschrift, Text, Fortschritt und Zeitraffer. Sie wird
 * mit Java2D gemalt, wenn sich etwas ändert (höchstens viermal je Sekunde), und als Textur über das fertige
 * Bild gelegt; Fotos (F12) und Standbilder haben sie mit drin.
 */
final class CaptionOverlay {
    static final Color GLASS = new Color(15, 17, 20, 205), GOLD = new Color(236, 192, 100), LAVA = new Color(255, 122, 51),
            INK = new Color(232, 230, 227), MUTED = new Color(160, 164, 170), LINE = new Color(60, 66, 74);
    private int tex, texW, texH;
    private BufferedImage img;
    private Player.Caption shown;
    private int shownWidth;
    private long lastPaint;
    int width, height;

    /** Neu malen, wenn nötig; false, wenn es nichts zu zeigen gibt. */
    boolean update(GL4 gl, Player.Caption c, int surfW, int surfH) {
        if (c == null) return false;
        int w = (int) Math.max(360, Math.min(620, surfW * 0.36));
        long now = System.nanoTime();
        boolean textChanged = shown == null || !c.title().equals(shown.title()) || !c.date().equals(shown.date()) || c.state() != shown.state()
                || c.waiting() != shown.waiting() || w != shownWidth;
        boolean due = now - lastPaint > 250_000_000L;
        if (tex != 0 && !textChanged && !(due && (Math.abs(c.progress() - shown.progress()) > 0.002 || Math.round(c.outro()) != Math.round(shown.outro()))))
            return true;
        lastPaint = now;
        shown = c;
        shownWidth = w;
        img = paint(c, w, Math.max(1, surfH));
        upload(gl, img);
        return true;
    }

    /** Die Tafel als Bild (auch für Prüfungen ohne Grafikkarte). */
    static BufferedImage paint(Player.Caption c, int w, int surfH) {
        float s = w / 520f;
        Font eyebrow = new Font(Font.MONOSPACED, Font.BOLD, Math.round(11 * s));
        Font date = serif(Math.round(25 * s));
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(16 * s));
        Font body = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(13 * s));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(11 * s));
        int pad = Math.round(18 * s);
        // Text umbrechen (mit einem Probebild für die Maße)
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        List<String> lines = wrap(com.dan.kilauea.ui.Glyphs.fit(body, c.text()), pg.getFontMetrics(body), w - 2 * pad);
        int lineH = Math.round(body.getSize() * 1.42f);
        // Höhe aus denselben Schritten wie beim Malen
        FontMetrics fe = pg.getFontMetrics(eyebrow), fd = pg.getFontMetrics(date), ft = pg.getFontMetrics(title), fs = pg.getFontMetrics(small);
        int textTop = pad + fe.getAscent() + Math.round(10 * s) + fd.getAscent() + Math.round(date.getSize() * 0.3f)
                + ft.getAscent() + Math.round(4 * s) + Math.round(title.getSize() * 0.55f);
        int footH = Math.round(14 * s) + Math.max(2, Math.round(3 * s)) + Math.round(8 * s) + fs.getAscent() + fs.getDescent() + pad;
        int maxLines = Math.max(1, ((int) (surfH * 0.6) - textTop - footH) / lineH);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, lines.get(maxLines - 1) + " …");
        }
        int h = textTop + lines.size() * lineH + footH;
        pg.dispose();
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setComposite(AlphaComposite.Src);
        float r = 8 * s;
        g.setColor(GLASS);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, r, r));
        g.setComposite(AlphaComposite.SrcOver);
        // Goldkante: oben und links, wie die Kante des Symbols
        g.setPaint(new GradientPaint(0, 0, GOLD, w * 0.7f, 0, new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 0)));
        g.setStroke(new BasicStroke(2 * s));
        g.drawLine(Math.round(r / 2), 1, w, 1);
        g.setPaint(new GradientPaint(0, 0, GOLD, 0, h, new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40)));
        g.drawLine(1, Math.round(r / 2), 1, h - Math.round(r / 2));
        int y = pad;
        g.setFont(eyebrow);
        g.setColor(LAVA);
        String unit = c.lapse().startsWith("Rundflug") ? "Station" : "Kapitel";
        boolean info = c.lapse().startsWith("Auskunft");   // Phase 14: Tafel zu Ort oder Boden
        String eb = (c.script() + (c.state() == Player.State.FINISHED || info ? "" : String.format(Locale.GERMAN, "  ·  %s %d von %d", unit, c.chapter(), c.chapters()))).toUpperCase(Locale.GERMAN);
        y += g.getFontMetrics().getAscent();
        com.dan.kilauea.ui.Glyphs.draw(g, clip(eb, g.getFontMetrics(), w - 2 * pad), pad, y);
        y += Math.round(10 * s);
        g.setFont(date);
        g.setColor(INK);
        y += g.getFontMetrics().getAscent();
        com.dan.kilauea.ui.Glyphs.draw(g, clip(c.date(), g.getFontMetrics(), w - 2 * pad), pad, y);
        y += Math.round(date.getSize() * 0.3f);
        g.setFont(title);
        g.setColor(GOLD);
        y += g.getFontMetrics().getAscent() + Math.round(4 * s);
        com.dan.kilauea.ui.Glyphs.draw(g, clip(c.title(), g.getFontMetrics(), w - 2 * pad), pad, y);
        y += Math.round(title.getSize() * 0.55f);
        g.setFont(body);
        g.setColor(INK);
        for (String l : lines) {
            y += lineH;
            com.dan.kilauea.ui.Glyphs.draw(g, l, pad, y);
        }
        // Fortschritt und Zeitraffer unten
        int by = h - pad - g.getFontMetrics(small).getDescent() - g.getFontMetrics(small).getAscent() - Math.round(8 * s) - Math.max(2, Math.round(3 * s));
        g.setColor(LINE);
        g.fillRect(pad, by, w - 2 * pad, Math.max(2, Math.round(3 * s)));
        g.setColor(info ? GOLD : LAVA);
        g.fillRect(pad, by, (int) Math.round((w - 2 * pad) * Math.max(0, Math.min(1, info ? 0.12 : c.progress()))), Math.max(2, Math.round(3 * s)));
        g.setFont(small);
        g.setColor(MUTED);
        String foot;
        if (c.state() == Player.State.FINISHED) foot = String.format(Locale.GERMAN, "Zurück in den Ausgangszustand in %.0f s", Math.ceil(c.outro()));
        else if (c.lapse().startsWith("Rundflug") || info) foot = c.lapse();
        else {
            foot = "Zeitraffer: " + c.lapse();
            if (c.state() == Player.State.PAUSED) foot += "  ·  angehalten";
            else if (c.waiting()) foot += "  ·  die Lava rechnet, die Geschichte wartet";
            foot += "  ·  L belegt · A abgeleitet · G gestaltet";
        }
        com.dan.kilauea.ui.Glyphs.draw(g, clip(foot, g.getFontMetrics(), w - 2 * pad), pad, h - pad - g.getFontMetrics().getDescent());
        g.dispose();
        return im;
    }

    private static Font serif(int size) {
        for (String n : new String[]{"Georgia", "Cambria", "Times New Roman", Font.SERIF}) {
            Font f = new Font(n, Font.PLAIN, size);
            if (f.getFamily().equals(n) || n.equals(Font.SERIF)) return f;
        }
        return new Font(Font.SERIF, Font.PLAIN, size);
    }

    static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String t = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(t) > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(t);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private static String clip(String s, FontMetrics fm, int width) {
        s = com.dan.kilauea.ui.Glyphs.fit(fm.getFont(), s);   // Statusbild Phase 14: ʻokina fehlte in Georgia
        if (fm.stringWidth(s) <= width) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void upload(GL4 gl, BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        int[] px = im.getRGB(0, 0, w, h, null, 0, w);   // ARGB, nicht vormultipliziert
        ByteBuffer b = Buffers.newDirectByteBuffer(w * h * 4);
        for (int p : px) {
            int a = p >>> 24;
            b.put((byte) (((p >> 16) & 255) * a / 255)).put((byte) (((p >> 8) & 255) * a / 255)).put((byte) ((p & 255) * a / 255)).put((byte) a);
        }
        b.flip();
        if (tex == 0 || w != texW || h != texH) {
            if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA8, w, h);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            texW = w;
            texH = h;
        } else gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        width = w;
        height = h;
    }

    int texture() { return tex; }

    void dispose(GL4 gl) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        tex = 0;
        shown = null;
    }
}
