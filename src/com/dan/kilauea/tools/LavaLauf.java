package com.dan.kilauea.tools;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaLake;
import com.dan.kilauea.lava.Vent;
import com.dan.kilauea.lava.VentSite;
import com.dan.kilauea.terrain.DetailTiles;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.TerrainBuilder;
import com.dan.kilauea.ui.LavaPainter;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ein Lauf des Lava-Modells ohne Fenster und ohne Grafikkarte: Tabelle alle 10 Minuten (Semikolon), dazu
 * auf Wunsch eine Karte und der Verlauf als Bild.
 * <pre>
 * LavaLauf [Ort] [m³/s] [Stunden] [Schlot aus nach Stunden] [Bild.png]
 * LavaLauf SEE [Minuten] [Bild.png]
 * </pre>
 * Orte: {@link VentSite} (HALEMAUMAU, KILAUEA_IKI, MAUNA_ULU, PUU_OO, FISSURE8, SW_RIFT). Ohne Angaben:
 * Mauna Ulu, 10 m³/s, 6 Stunden. Arbeitsordner = Projektordner (oder -Dkilauea.dir=…).
 */
public final class LavaLauf {
    private LavaLauf() { }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        if (args.length > 0 && args[0].equalsIgnoreCase("SEE")) {
            lake(args.length > 1 ? Double.parseDouble(args[1]) : 60, args.length > 2 ? args[2] : null);
            return;
        }
        VentSite site = args.length > 0 ? VentSite.valueOf(args[0].toUpperCase(Locale.ROOT)) : VentSite.MAUNA_ULU;
        double rate = args.length > 1 ? Double.parseDouble(args[1]) : site.rate;
        double hours = args.length > 2 ? Double.parseDouble(args[2]) : 6;
        double off = args.length > 3 ? Double.parseDouble(args[3]) : hours;
        String png = args.length > 4 ? args[4] : null;

        File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
        HeightGrid g = TerrainBuilder.load(dir);
        Ground.use(g);
        DetailTiles.use(DetailTiles.load(dir, g, null));
        LavaField f = new LavaField(Ground::solidHeight, 10, 1024, 1);
        Vent v = Vent.at(site, rate);
        f.addVent(v);
        System.out.println("Stunde;Laenge km;Flaeche km2;beweglich m3;erstarrt m3;Kern Mittel C;Front C;aa %;unter Dach ha;Oberlichter;Bloecke;Rechenzeit s");
        List<double[]> rows = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int k = 1; k <= Math.round(hours * 6); k++) {
            if (k / 6.0 > off) v.rate = 0;
            f.step(600);
            LavaField.Stats s = f.stats(site.x(), site.z());
            double cpu = (System.nanoTime() - t0) / 1e9;
            System.out.printf(Locale.ROOT, "%.3f;%.3f;%.4f;%.0f;%.0f;%.1f;%.1f;%.1f;%.2f;%d;%d;%.1f%n", k / 6.0, s.length / 1000, s.area / 1e6, s.mobile, s.rock,
                    s.meanT, s.frontT, s.aaShare * 100, s.roofArea / 1e4, s.skylights, f.blocksUsed(), cpu);
            rows.add(new double[]{k / 6.0, s.length / 1000, s.area / 1e6, s.meanT, s.frontT, s.aaShare * 100, s.roofArea / 1e4});
        }
        System.err.println(f.stats(site.x(), site.z()));
        if (png == null) return;
        double[] view = LavaPainter.fit(f, 800);
        int px = 1000;
        int[] shade = LavaPainter.hillshade(Ground::solidHeight, view[0], view[1], view[2], px);
        int[] rgb = new int[px * px];
        LavaPainter.overlay(f, shade, rgb, view[0], view[1], view[2], px, hours > 3 ? 1 : 0.25);
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, px, px, rgb, 0, px);
        Graphics2D gr = img.createGraphics();
        LavaField.Stats s = f.stats(site.x(), site.z());
        LavaPainter.legend(gr, px, px, view[2], String.format(Locale.GERMAN, "%s · %.0f m³/s · %s", site.label, rate,
                        off < hours ? String.format(Locale.GERMAN, "%.0f h, Schlot aus nach %.0f h", hours, off) : String.format(Locale.GERMAN, "%.1f h", hours)),
                String.format(Locale.GERMAN, "%,.0f m³ · %.2f km² · %.2f km lang · Kern im Mittel %.0f °C · ʻaʻā %.0f %% · unter Dach %.1f ha, %d Oberlichter",
                        s.erupted, s.area / 1e6, s.length / 1000, s.meanT, s.aaShare * 100, s.roofArea / 1e4, s.skylights));
        gr.dispose();
        ImageIO.write(img, "png", new File(png));
        chart(rows, png.replace(".png", "_verlauf.png"), site.label + String.format(Locale.GERMAN, ", %.0f m³/s", rate));
    }

    /** Verlauf als vier kleine Diagramme. */
    static void chart(List<double[]> rows, String file, String title) throws IOException {
        int w = 1000, h = 560;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x171A1F));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0xE8E6E3));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(title, 16, 24);
        String[] names = {"Länge der Front (km)", "Fläche (km²)", "Kern im Mittel (orange) und an der Front (gelb), °C", "ʻaʻā in % (orange) und unter Dach in ha (gelb)"};
        int[][] cols = {{1}, {2}, {3, 4}, {5, 6}};
        int[] colors = {0xFF7A33, 0xF0B44A, 0x5FB4BE};
        for (int p = 0; p < 4; p++) {
            int x0 = 16 + (p % 2) * 492, y0 = 44 + (p / 2) * 256, pw = 470, ph = 236;
            g.setColor(new Color(0x1E232A));
            g.fillRect(x0, y0, pw, ph);
            g.setColor(new Color(0x9A9FA6));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(names[p], x0 + 10, y0 + 18);
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, tmax = rows.get(rows.size() - 1)[0];
            for (double[] r : rows) for (int c : cols[p]) if (Double.isFinite(r[c])) { lo = Math.min(lo, r[c]); hi = Math.max(hi, r[c]); }
            if (p < 2 || p == 3) lo = 0;
            if (hi - lo < 1e-9) hi = lo + 1;
            double pad = (hi - lo) * 0.08;
            hi += pad;
            if (p == 2) lo -= pad;
            int ix0 = x0 + 46, iy0 = y0 + 28, iw = pw - 60, ih = ph - 52;
            g.setColor(new Color(0x2C3138));
            for (int k = 0; k <= 4; k++) g.drawLine(ix0, iy0 + ih * k / 4, ix0 + iw, iy0 + ih * k / 4);
            g.setColor(new Color(0x9A9FA6));
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int k = 0; k <= 4; k++) g.drawString(String.format(Locale.GERMAN, p == 2 ? "%.0f" : "%.2f", hi - (hi - lo) * k / 4), x0 + 4, iy0 + ih * k / 4 + 4);
            for (int k = 0; k <= 6; k++) g.drawString(String.format(Locale.GERMAN, "%.0f h", tmax * k / 6), ix0 + iw * k / 6 - 8, iy0 + ih + 14);
            for (int ci = 0; ci < cols[p].length; ci++) {
                int c = cols[p][ci];
                g.setColor(new Color(colors[p == 1 ? 1 : ci]));
                g.setStroke(new BasicStroke(2f));
                int px = -1, py = -1;
                for (double[] r : rows) {
                    if (!Double.isFinite(r[c])) continue;
                    int x = ix0 + (int) Math.round(iw * r[0] / tmax), y = iy0 + (int) Math.round(ih * (hi - r[c]) / (hi - lo));
                    if (px >= 0) g.drawLine(px, py, x, y);
                    px = x;
                    py = y;
                }
            }
        }
        g.dispose();
        ImageIO.write(img, "png", new File(file));
    }

    /** Der Lavasee: Tabelle der Platten und ein Bild von oben. */
    static void lake(double minutes, String png) throws IOException {
        LavaLake L = LavaLake.halemaumau(5);
        System.out.println("Minute;Platten;neu;abgetaucht;Spiegel m;Gaskolben;Spritzer");
        for (int m = 1; m <= minutes; m++) {
            L.step(60);
            System.out.printf(Locale.ROOT, "%d;%d;%d;%d;%.2f;%d;%d%n", m, L.plates(), L.born(), L.sunk(), L.level(), L.pistons(), L.spatters());
        }
        if (png == null) return;
        int px = 900;
        double size = 2.3 * L.a;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        Graphics2D g0 = img.createGraphics();
        g0.setColor(new Color(0x2A2624));
        g0.fillRect(0, 0, px, px);
        g0.dispose();
        LavaPainter.lake(L, img, L.cx - size / 2, L.cz - size / 2, size, true);
        Graphics2D g = img.createGraphics();
        LavaPainter.legend(g, px, px, size, "Lavasee im Halemaʻumaʻu", String.format(Locale.GERMAN,
                "nach %.0f min · %d Platten · %d neu, %d abgetaucht · Spiegel %.1f m über dem Boden · %d Gaskolben", minutes, L.plates(), L.born(), L.sunk(), L.level(), L.pistons()), false);
        g.dispose();
        ImageIO.write(img, "png", new File(png));
    }
}
