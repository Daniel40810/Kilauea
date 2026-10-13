package com.dan.kilauea.tools;

import com.dan.kilauea.db.Geometrie;
import com.dan.kilauea.terrain.DetailTiles;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.TerrainBuilder;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Phase 16b: schreibt db/06_grunddaten.sql aus dem Code (Orte, Krater, Ströme, Stationen, Drehbücher, Kapitel) und auf
 * Wunsch Karten der gerechneten Geometrien als Bild.
 * <pre>java … com.dan.kilauea.tools.Grunddaten [bildordner]</pre>
 * Läuft ohne Fenster und ohne Datenbank, rund eine halbe Minute.
 */
public final class Grunddaten {
    private Grunddaten() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
        long t0 = System.nanoTime();
        HeightGrid g = TerrainBuilder.load(dir);
        com.dan.kilauea.terrain.ParkRoads.build(g);
        DetailTiles.use(DetailTiles.load(dir, g, null));
        Ground.use(g);
        com.dan.kilauea.db.Grunddaten.Ergebnis e = com.dan.kilauea.db.Grunddaten.rechne(g);
        File out = new File(dir, "db/06_grunddaten.sql");
        Files.write(out.toPath(), e.sql.getBytes(StandardCharsets.UTF_8));
        System.out.printf(Locale.GERMAN, "%s: %d Anweisungen, %d Punkte, %,d Bytes, %.1f s%n", out.getPath(), e.anweisungen, e.punkte, e.sql.length(), (System.nanoTime() - t0) / 1e9);
        System.out.println("Zeilen: " + e.zeilen);
        System.out.println("Geometrien: " + e.geometrien);
        System.out.println("Zellen, deren Alterscode nicht zum Nachrechnen passt: " + e.abweichendeZellen);
        for (String h : e.hinweise) System.out.println("Hinweis: " + h);
        System.out.println("\nKrater                       Tiefe     Länge × Breite   Fläche   Rand");
        for (com.dan.kilauea.db.Grunddaten.Krater k : e.krater)
            System.out.printf(Locale.GERMAN, "  %-26s %6.0f m  %6.0f × %5.0f m  %7.1f ha  %5.0f m%n", k.def.name(), k.tiefe, k.laenge, k.breite, k.flaecheHa, k.randHoehe);
        System.out.println("\nStrom                                   Jahr        Teile  Löcher  Punkte  km²");
        for (com.dan.kilauea.db.Grunddaten.Strom s : e.stroeme) {
            int l = 0, pkt = 0;
            for (Geometrie.Polygon p : s.umriss) { l += p.loecher.size(); pkt += p.punkte(); }
            System.out.printf(Locale.GERMAN, "  %-38s %4d–%-4s  %5d  %6d  %6d  %6.2f%n", s.name, s.jahrVon, s.jahrBis > 0 ? Integer.toString(s.jahrBis) : "", s.umriss.size(), l, pkt, s.flaecheKm2);
        }
        if (args.length > 0) {
            File bilder = new File(args[0]);
            bilder.mkdirs();
            karte(g, e, bilder, "grund_kilauea.png", -30_000, 52_000, -24_000, 26_000, 1600);
            karte(g, e, bilder, "grund_gipfel.png", -5_500, 6_500, -3_500, 4_000, 1400);
        }
    }

    /** Schummerung des Rasters, darüber Ströme nach Jahr, Achsen, Spalten und Kraterränder. */
    static void karte(HeightGrid g, com.dan.kilauea.db.Grunddaten.Ergebnis e, File dir, String name, double xa, double xb, double za, double zb, int w) throws Exception {
        int h = (int) Math.round(w * (zb - za) / (xb - xa));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double sx = (xb - xa) / w, sz = (zb - za) / h;
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                double x = xa + i * sx, z = za + j * sz;
                double y = Ground.baseHeight(x, z);
                if (y < 0) { img.setRGB(i, j, 0x0d2233); continue; }
                double dx = Ground.baseHeight(x + sx, z) - y, dz = Ground.baseHeight(x, z + sz) - y;
                double shade = 0.55 + 0.45 * Math.max(-1, Math.min(1, (-dx + dz) / (sx * 0.8)));
                int v = (int) Math.max(0, Math.min(255, 40 + 70 * shade + y * 0.012));
                img.setRGB(i, j, (v << 16) | (v << 8) | v);
            }
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (com.dan.kilauea.db.Grunddaten.Strom s : e.stroeme) {
            float t = (float) Math.max(0, Math.min(1, (s.jahrModell - 1800) / 226.0));
            Color c = Color.getHSBColor(0.62f - 0.62f * t, 0.85f, 0.95f);
            Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            for (Geometrie.Polygon p : s.umriss) {
                ring(path, p.aussen, xa, za, sx, sz);
                for (double[] l : p.loecher) ring(path, l, xa, za, sx, sz);
            }
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
            g2.fill(path);
            g2.setColor(c);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(path);
            if (s.achse != null) {
                g2.setColor(new Color(255, 255, 255, 170));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(linie(s.achse, xa, za, sx, sz));
            }
            if (s.spalte != null) {
                g2.setColor(new Color(255, 80, 40));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(linie(s.spalte, xa, za, sx, sz));
            }
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        for (com.dan.kilauea.db.Grunddaten.Krater k : e.krater) {
            Path2D path = new Path2D.Double();
            ring(path, k.rand, xa, za, sx, sz);
            g2.setColor(new Color(255, 230, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(path);
            double cx = (Geo.x(k.def.lat(), k.def.lon()) - xa) / sx, cz = (Geo.z(k.def.lat()) - za) / sz;
            if (cx > 0 && cx < w && cz > 0 && cz < h) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.drawString(k.def.name(), (float) cx + 7, (float) cz + 5);
                g2.setColor(new Color(255, 240, 190));
                g2.drawString(k.def.name(), (float) cx + 6, (float) cz + 4);
            }
        }
        g2.dispose();
        ImageIO.write(img, "png", new File(dir, name));
        System.out.println("Bild " + new File(dir, name));
    }

    static void ring(Path2D p, double[] r, double xa, double za, double sx, double sz) {
        for (int i = 0; i < r.length / 2; i++) {
            double x = (r[2 * i] - xa) / sx, y = (-r[2 * i + 1] - za) / sz;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
    }

    static Path2D linie(double[] l, double xa, double za, double sx, double sz) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < l.length / 2; i++) {
            double x = (l[2 * i] - xa) / sx, y = (-l[2 * i + 1] - za) / sz;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        return p;
    }
}
