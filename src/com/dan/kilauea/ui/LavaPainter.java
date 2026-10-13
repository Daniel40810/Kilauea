package com.dan.kilauea.ui;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaLake;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Zeichnet das Lava-Modell als Karte von oben, ohne Grafikkarte (Phase 7): Schummerung des Geländes,
 * darüber die Lava nach Kerntemperatur, ʻaʻā schraffiert, Lava unter Dach abgedunkelt mit Oberlichtern,
 * leergelaufene Röhren, neues Gestein und Linien gleicher Ankunftszeit. Dazu der Lavasee mit Platten
 * und Nähten. Für die kleine Karte im Bedienfeld und für die Prüfbilder von {@code tools/LavaLauf}.
 */
public final class LavaPainter {
    private LavaPainter() { }

    /** Schummerung des Geländes für den Ausschnitt [x0, x0+size] × [z0, z0+size] in px × px. */
    public static int[] hillshade(LavaField.Terrain t, double x0, double z0, double size, int px) {
        int[] rgb = new int[px * px];
        double step = size / px;
        double[] hh = new double[(px + 2) * (px + 2)];
        for (int j = -1; j <= px; j++)
            for (int i = -1; i <= px; i++)
                hh[(j + 1) * (px + 2) + (i + 1)] = t.height(x0 + (i + 0.5) * step, z0 + (j + 0.5) * step);
        for (int j = 0; j < px; j++)
            for (int i = 0; i < px; i++) {
                double h = hh[(j + 1) * (px + 2) + (i + 1)];
                if (h < 0) {
                    rgb[j * px + i] = mix(0x1E4A6E, 0x0A1828, Math.min(1, -h / 300));
                    continue;
                }
                double dx = (hh[(j + 1) * (px + 2) + (i + 2)] - hh[(j + 1) * (px + 2) + i]) / (2 * step);
                double dz = (hh[(j + 2) * (px + 2) + (i + 1)] - hh[j * (px + 2) + (i + 1)]) / (2 * step);
                double lx = -0.5, ly = 0.7071, lz = -0.5;
                double sh = Math.max(0, (-dx * lx + ly - dz * lz) / Math.sqrt(dx * dx + 1 + dz * dz));
                rgb[j * px + i] = mul(0x5E5852, 0.35 + 0.95 * sh);
            }
        return rgb;
    }

    /** Farbe der Lava nach Kerntemperatur: 1 150 °C hellgelb bis 1 000 °C dunkelrot. */
    public static int lavaColor(double t) {
        double f = Math.max(0, Math.min(1, (t - 1000) / 150));
        if (f > 0.66) return mix(0xFF9A2E, 0xFFF0A0, (f - 0.66) / 0.34);
        if (f > 0.33) return mix(0xD8401A, 0xFF9A2E, (f - 0.33) / 0.33);
        return mix(0x5A0E08, 0xD8401A, f / 0.33);
    }

    /**
     * Lava über die Schummerung legen.
     * @param hourStep Abstand der Linien gleicher Ankunftszeit (h), 0 = keine
     */
    public static void overlay(LavaField f, int[] shade, int[] out, double x0, double z0, double size, int px, double hourStep) {
        LavaField.Probe p = new LavaField.Probe();
        double step = size / px;
        float[] arr = new float[px * px];
        for (int j = 0; j < px; j++)
            for (int i = 0; i < px; i++) {
                int k = j * px + i;
                int c = shade[k];
                arr[k] = -1;
                double x = x0 + (i + 0.5) * step, z = z0 + (j + 0.5) * step;
                if (f.probe(x, z, p)) {
                    arr[k] = (float) p.arrival;
                    if (p.thickness > 0) {
                        int lc = lavaColor(p.temperature);
                        if (p.tube && !p.skylight) lc = mix(lc, 0x2A1A16, 0.62);
                        if (p.aa >= 0.5 && ((i + j) % 5 == 0)) lc = mix(lc, 0x1A0A06, 0.7);
                        c = mix(c, lc, Math.min(1, 0.55 + p.thickness));
                        if (p.skylight) c = 0xFFF4B0;
                    } else if (p.drained) c = 0x5A6A7A;
                    else if (p.rock > 0.02) c = mix(c, (p.aa >= 0.5 && (i + j) % 5 == 0) ? 0x0C0B0B : 0x232120, Math.min(1, 0.5 + p.rock));
                }
                out[k] = c;
            }
        if (hourStep <= 0) return;
        double hs = hourStep * 3600;
        for (int j = 1; j < px; j++)
            for (int i = 1; i < px; i++) {
                int k = j * px + i;
                float a = arr[k], l = arr[k - 1], u = arr[k - px];
                if (a < 0) continue;
                if ((l >= 0 && Math.floor(a / hs) != Math.floor(l / hs)) || (u >= 0 && Math.floor(a / hs) != Math.floor(u / hs)))
                    out[k] = mix(out[k], 0xFFFFFF, 0.75);
            }
    }

    /** Ausschnitt, der alle bedeckten Zellen und die Schlote umfasst (x0, z0, Seite), mindestens minSize. */
    public static double[] fit(LavaField f, double minSize) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        f.forEachCovered((x, z, g, r, h, t, aa, u, fl, arr) -> {
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], z); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], z);
        });
        for (var v : f.vents()) {
            b[0] = Math.min(b[0], Math.min(v.x0, v.x1)); b[1] = Math.min(b[1], Math.min(v.z0, v.z1));
            b[2] = Math.max(b[2], Math.max(v.x0, v.x1)); b[3] = Math.max(b[3], Math.max(v.z0, v.z1));
        }
        double side = Math.max(minSize, Math.max(b[2] - b[0], b[3] - b[1]) * 1.25);
        return new double[]{0.5 * (b[0] + b[2]) - side / 2, 0.5 * (b[1] + b[3]) - side / 2, side};
    }

    /** Legende und Maßstab in ein fertiges Bild schreiben. */
    public static void legend(Graphics2D g, int w, int h, double size, String title, String sub) { legend(g, w, h, size, title, sub, true); }

    /** Wie oben; key = false lässt die Farbleiste der Lava weg (für den See). */
    public static void legend(Graphics2D g, int w, int h, double size, String title, String sub, boolean key) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRect(0, 0, w, 44);
        g.setColor(new Color(0xF0EDE8));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(title, 10, 19);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(0xC8C4BE));
        g.drawString(sub, 10, 36);
        // Maßstab
        double mpp = size / w;
        double[] nice = {50, 100, 200, 250, 500, 1000, 2000, 5000};
        double len = nice[0];
        for (double n : nice) if (n / mpp < w * 0.28) len = n;
        int lp = (int) Math.round(len / mpp);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(6, h - 30, lp + 70, 24);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(12, h - 14, 12 + lp, h - 14);
        g.drawLine(12, h - 19, 12, h - 9);
        g.drawLine(12 + lp, h - 19, 12 + lp, h - 9);
        g.drawString(len >= 1000 ? String.format(Locale.GERMAN, "%.0f km", len / 1000) : String.format(Locale.GERMAN, "%.0f m", len), 18 + lp, h - 10);
        if (!key) return;
        // Farbleiste
        int bx = w - 236, by = h - 64;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(bx - 8, by - 18, 236, 76);
        for (int i = 0; i < 160; i++) {
            g.setColor(new Color(lavaColor(1000 + 150.0 * i / 159)));
            g.fillRect(bx + i, by, 1, 10);
        }
        g.setColor(new Color(0xE8E4DE));
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("Kern 1 000 °C", bx, by - 5);
        g.drawString("1 150", bx + 132, by - 5);
        int ly = by + 24;
        swatch(g, bx, ly, 0x232120, "erstarrt");
        swatch(g, bx + 74, ly, mix(lavaColor(1120), 0x2A1A16, 0.62), "unter Dach");
        swatch(g, bx + 160, ly, 0x5A6A7A, "leer");
        swatch(g, bx, ly + 16, 0xFFF4B0, "Oberlicht");
        g.setColor(new Color(0xE8E4DE));
        g.drawString("▨ ʻaʻā   — Ankunft je Stunde", bx + 74, ly + 25);
    }

    private static void swatch(Graphics2D g, int x, int y, int rgb, String label) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, 10, 10);
        g.setColor(new Color(0xE8E4DE));
        g.drawString(label, x + 14, y + 9);
    }

    // ------------------------------------------------------------------ Lavasee

    /**
     * Der Lavasee von oben: jede Stelle gehört zur nächsten Platte (Kruste nach Alter dunkler), an den Grenzen
     * glühen die Nähte, breiter wo sie sich öffnen. Spritzer als helle Punkte, Quelle und Senke markiert.
     */
    public static void lake(LavaLake L, BufferedImage img, double x0, double z0, double size, boolean marks) {
        int px = img.getWidth();
        double step = size / px;
        int n = L.plates();
        double[] u = new double[n], v = new double[n], ag = new double[n];
        for (int i = 0; i < n; i++) { u[i] = L.plateU(i); v[i] = L.plateV(i); ag[i] = L.plateAge(i); }
        for (int j = 0; j < px; j++)
            for (int i = 0; i < px; i++) {
                double x = x0 + (i + 0.5) * step, z = z0 + (j + 0.5) * step;
                double lu = L.toU(x, z), lv = L.toV(x, z);
                double s = L.shape(lu, lv);
                if (s >= 1) continue;
                int b1 = -1, b2 = -1;
                double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
                for (int k = 0; k < n; k++) {
                    double d = (u[k] - lu) * (u[k] - lu) + (v[k] - lv) * (v[k] - lv);
                    if (d < d1) { d2 = d1; b2 = b1; d1 = d; b1 = k; } else if (d < d2) { d2 = d; b2 = k; }
                }
                if (b1 < 0) continue;
                double gap = Math.sqrt(d2) - Math.sqrt(d1);           // Abstand zur Naht (m, doppelt)
                double open = b2 >= 0 ? L.seamOpening(b1, b2) : 0;
                if (Double.isNaN(open)) open = 0;
                double width = 1.2 + Math.max(0, open) * 120;           // breiter, wo sich die Naht öffnet
                double crust = Math.min(1, Math.sqrt(ag[b1] / 3600));
                int c = mix(0x5A2A1C, 0x161314, crust);
                c = mix(c, 0x0E0C0C, 0.15 * Math.sin(lu * 0.7 + lv * 0.4) * Math.sin(lv * 0.9 - lu * 0.3) + 0.15);
                double glow = Math.max(0, 1 - gap / width);
                c = mix(c, glow > 0.6 ? 0xFFE9A0 : 0xFF7A2A, glow);
                // Ufer: heller Saum, wo die Lava an die Wand schlägt
                if (s > 0.95) c = mix(c, 0xFF9A40, (s - 0.95) / 0.05 * 0.8);
                img.setRGB(i, j, c);
            }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double now = L.time();
        for (int k = 0; k < L.spatterCount(); k++) {
            double age = now - L.spatterTime(k);
            if (age > 300) break;
            double a = Math.max(0, 1 - age / 300) * L.spatterStrength(k);
            double r = 2 + 4 * L.spatterStrength(k);
            g.setColor(new Color(1f, 0.92f, 0.6f, (float) Math.min(1, a)));
            g.fill(new Ellipse2D.Double((L.spatterX(k) - x0) / step - r, (L.spatterZ(k) - z0) / step - r, 2 * r, 2 * r));
        }
        if (marks) {
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(0x7FD0E0));
            double sx = (L.toX(L.srcU, L.srcV) - x0) / step, sz = (L.toZ(L.srcU, L.srcV) - z0) / step;
            double kx = (L.toX(L.snkU, L.snkV) - x0) / step, kz = (L.toZ(L.snkU, L.snkV) - z0) / step;
            g.draw(new Ellipse2D.Double(sx - 9, sz - 9, 18, 18));
            g.draw(new Ellipse2D.Double(kx - 9, kz - 9, 18, 18));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Quelle", (float) sx - 20, (float) sz - 13);
            g.drawString("Senke", (float) kx - 18, (float) kz + 24);
        }
        g.dispose();
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int mul(int a, double f) {
        int r = (int) Math.min(255, ((a >> 16) & 255) * f), g = (int) Math.min(255, ((a >> 8) & 255) * f), b = (int) Math.min(255, (a & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
