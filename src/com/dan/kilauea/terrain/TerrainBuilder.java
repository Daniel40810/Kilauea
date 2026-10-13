package com.dan.kilauea.terrain;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Baut das Höhenraster der Szene, einmalig, und legt es in {@code data/gelaende_75m.bin} ab:
 * <ol>
 * <li>Höhen aus den Kacheln im Projekt (GLSDEM/SRTM 3″, GTOPO30) auf das 75-m-Raster der Szene.</li>
 * <li>Küste aus den Daten (Höhe über 0,5 m ist Land), Abstand zur Küste je Zelle.</li>
 * <li>Meeresboden aus dem Abstand zur Küste [G], solange keine Tiefendaten im Projekt liegen.</li>
 * <li>Änderungen seit 2000 ({@link Changes2018}).</li>
 * <li>Gestein und Grün je Zelle ({@link Geology}): Alter und Art der Lava, Regen, Bewuchs, Asche, Küste.</li>
 * </ol>
 * Ohne Höhendaten entsteht die gerechnete Insel aus Phase 1. Kommen Höhendateien dazu oder fallen
 * weg, baut der nächste Start neu (Fingerabdruck im Zwischenspeicher); sonst genügt Löschen der .bin-Datei.
 */
public final class TerrainBuilder {
    static final int MAGIC = 0x4B494C47, VERSION = 6;   // 6: Salzzone an der Küste (Phase 9)   // "KILG"
    public static volatile double progress;
    public static volatile String step = "Gelände";

    private TerrainBuilder() { }

    public static File cacheFile(File projectDir) { return new File(projectDir, "data/gelaende_75m.bin"); }

    /** Lädt das Raster aus dem Zwischenspeicher oder baut es neu. */
    public static HeightGrid load(File projectDir) {
        File cache = cacheFile(projectDir);
        String sig = DemSource.signature(projectDir);
        if (cache.isFile()) {
            try {
                step = "Gelände aus data/gelaende_75m.bin";
                HeightGrid g = read(cache, sig);
                progress = 1;
                return g;
            } catch (IOException e) {
                System.err.println("Zwischenspeicher passt nicht, baue neu: " + e.getMessage());
            }
        }
        step = "Höhendaten suchen";
        DemSource dem = DemSource.scan(projectDir);
        for (String n : dem.notes()) System.err.println("Höhendaten: " + n);
        HeightGrid g = dem.isEmpty() ? procedural() : fromDem(dem);
        if (!dem.isEmpty()) {
            try {
                step = "Gelände sichern";
                write(g, cache, sig);
            } catch (IOException e) {
                System.err.println("Gelände nicht gesichert: " + e.getMessage());
            }
        }
        progress = 1;
        return g;
    }

    /** Aus Höhendaten. */
    public static HeightGrid fromDem(DemSource dem) {
        int n = HeightGrid.N;
        float[] h = new float[n * n];
        boolean[] land = new boolean[n * n];
        step = "Höhen einlesen: " + dem.describe();
        int[] done = new int[1];
        IntStream.range(0, n).parallel().forEach(j -> {
            double z = HeightGrid.Z0 + j * HeightGrid.CELL;
            double lat = Geo.lat(z);
            for (int i = 0; i < n; i++) {
                double x = HeightGrid.X0 + i * HeightGrid.CELL;
                double v = dem.height(lat, Geo.lon(x, z));
                boolean l = v > 0.5;
                land[j * n + i] = l;
                h[j * n + i] = l ? (float) v : 0f;
            }
            tick(done, n, 0.0, 0.45);
        });
        return finish(h, land, "Höhen " + dem.describe() + " (Stand 2000) mit Änderungen 2018; Meeresboden gestaltet");
    }

    /** Ohne Daten: die gerechnete Insel aus Phase 1 auf demselben Raster. */
    public static HeightGrid procedural() {
        int n = HeightGrid.N;
        float[] h = new float[n * n];
        boolean[] land = new boolean[n * n];
        step = "Keine Höhendaten im Projekt: gerechnete Insel";
        int[] done = new int[1];
        IntStream.range(0, n).parallel().forEach(j -> {
            double z = HeightGrid.Z0 + j * HeightGrid.CELL;
            for (int i = 0; i < n; i++) {
                double x = HeightGrid.X0 + i * HeightGrid.CELL;
                double v = Island.height(x, z);
                land[j * n + i] = v > 0;
                h[j * n + i] = (float) Math.max(v, 0);
            }
            tick(done, n, 0.0, 0.45);
        });
        return finish(h, land, "gerechnete Insel (Platzhalter, keine Höhendaten gefunden)");
    }

    private static void tick(int[] done, int n, double from, double span) {
        synchronized (done) { done[0]++; progress = from + span * done[0] / n; }
    }

    /** Küstenabstand, Meeresboden, Änderungen 2018, dann Gestein und Grün ({@link Geology}). */
    static HeightGrid finish(float[] h, boolean[] land, String source) {
        int n = HeightGrid.N;
        step = "Küste und Meeresboden";
        float[] dist = distance(land, n, HeightGrid.CELL);
        float[] sd = new float[n * n];
        progress = 0.5;
        step = "Änderungen seit 2000";
        IntStream.range(0, n).parallel().forEach(j -> {
            double z = HeightGrid.Z0 + j * HeightGrid.CELL;
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                double x = HeightGrid.X0 + i * HeightGrid.CELL;
                sd[k] = land[k] ? dist[k] : -dist[k];
                double y = land[k] ? h[k] : Island.seaFloor(x, z, dist[k]);
                if (Math.abs(x - 25_000) < 32_000 && Math.abs(z + 2_000) < 16_000) y = Changes2018.apply(x, z, y);
                h[k] = (float) y;
            }
        });
        byte[] cover = new byte[n * n * 4], geo = new byte[n * n * 4];
        HeightGrid g = new HeightGrid(h, cover, geo, source + "; Lava nach Jahr und Art, Regen und Bewuchs berechnet");
        step = "Lavaströme 1801–2024, Regen, Bewuchs";
        Geology.build(g, sd, cover, geo, v -> progress = 0.55 + 0.43 * v);
        return g;
    }

    /**
     * Abstand jeder Zelle zur nächsten Zelle der anderen Art (Land ↔ Meer), in Metern; zwei Durchläufe
     * mit 8 Nachbarn (Fehler unter 8 %, für den Meeresboden reicht das).
     */
    static float[] distance(boolean[] land, int n, double cell) {
        float inf = 1e9f;
        float[] d = new float[n * n];
        for (int k = 0; k < n * n; k++) {
            int i = k % n, j = k / n;
            boolean edge = false;
            if (i > 0 && land[k - 1] != land[k]) edge = true;
            else if (i < n - 1 && land[k + 1] != land[k]) edge = true;
            else if (j > 0 && land[k - n] != land[k]) edge = true;
            else if (j < n - 1 && land[k + n] != land[k]) edge = true;
            d[k] = edge ? 0.5f : inf;
        }
        float o = 1f, g = (float) Math.sqrt(2);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                float v = d[k];
                if (i > 0) v = Math.min(v, d[k - 1] + o);
                if (j > 0) {
                    v = Math.min(v, d[k - n] + o);
                    if (i > 0) v = Math.min(v, d[k - n - 1] + g);
                    if (i < n - 1) v = Math.min(v, d[k - n + 1] + g);
                }
                d[k] = v;
            }
        for (int j = n - 1; j >= 0; j--)
            for (int i = n - 1; i >= 0; i--) {
                int k = j * n + i;
                float v = d[k];
                if (i < n - 1) v = Math.min(v, d[k + 1] + o);
                if (j < n - 1) {
                    v = Math.min(v, d[k + n] + o);
                    if (i < n - 1) v = Math.min(v, d[k + n + 1] + g);
                    if (i > 0) v = Math.min(v, d[k + n - 1] + g);
                }
                d[k] = v;
            }
        for (int k = 0; k < n * n; k++) d[k] *= (float) cell;
        return d;
    }

    // ------------------------------------------------------------------ Zwischenspeicher

    static void write(HeightGrid g, File f, String sig) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " nicht anlegbar");
        File tmp = new File(f.getPath() + ".neu");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 1 << 20))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(HeightGrid.N);
            out.writeUTF(sig);
            out.writeUTF(g.source);
            for (float v : g.h) out.writeShort((short) Math.max(-32767, Math.min(32767, Math.round(v * 4))));
            out.write(g.cover);
            out.write(g.geo);
        }
        if (f.exists() && !f.delete()) throw new IOException("alte Datei nicht ersetzbar");
        if (!tmp.renameTo(f)) throw new IOException("Umbenennen fehlgeschlagen");
    }

    static HeightGrid read(File f, String sig) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 20))) {
            if (in.readInt() != MAGIC) throw new IOException("keine Geländedatei");
            int v = in.readInt();
            if (v != VERSION) throw new IOException("Version " + v + " statt " + VERSION);
            int n = in.readInt();
            if (n != HeightGrid.N) throw new IOException(String.format(Locale.ROOT, "Raster %d statt %d", n, HeightGrid.N));
            if (!in.readUTF().equals(sig)) throw new IOException("Höhendateien im Projekt haben sich geändert");
            String src = in.readUTF();
            float[] h = new float[n * n];
            byte[] raw = new byte[n * n * 2];
            in.readFully(raw);
            for (int k = 0; k < n * n; k++) h[k] = (short) (((raw[2 * k] & 0xff) << 8) | (raw[2 * k + 1] & 0xff)) / 4f;
            byte[] cover = new byte[n * n * 4];
            in.readFully(cover);
            byte[] geo = new byte[n * n * 4];
            in.readFully(geo);
            return new HeightGrid(h, cover, geo, src);
        }
    }
}
