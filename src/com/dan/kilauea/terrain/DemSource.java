package com.dan.kilauea.terrain;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Höhendaten aus Dateien: Kacheln im BIL-Format (GLSDEM/SRTM, 3 Bogensekunden, rund 90 m) mit
 * ESRI-Kopf ({@code .hdr}), dazu eine GTOPO30-Kachel (GeoTIFF, 30 Bogensekunden, rund 1 km) für alles,
 * was die feinen Kacheln nicht decken. Gelesen wird ohne Fremdbibliothek.
 * <p>
 * Meer ist in GLSDEM 0, in GTOPO30 −9999; beides gilt hier als „kein Land“ (Rückgabe NaN).
 * Gesucht wird im Projektordner und in {@code data/raw/}.
 */
public final class DemSource {
    /** Eine Rasterkachel in geografischen Koordinaten (Breite, Länge der oberen linken Pixelmitte). */
    static final class Tile {
        final String name;
        final int rows, cols;
        final double lat0, lon0, dLat, dLon;   // Mitte des Pixels oben links, Schritt in Grad
        final short[] v;
        final short nodata, sea;
        final int rank;                        // kleiner = feiner, wird zuerst gefragt

        Tile(String name, int rows, int cols, double lat0, double lon0, double dLat, double dLon, short[] v, short nodata, short sea, int rank) {
            this.name = name; this.rows = rows; this.cols = cols; this.lat0 = lat0; this.lon0 = lon0;
            this.dLat = dLat; this.dLon = dLon; this.v = v; this.nodata = nodata; this.sea = sea; this.rank = rank;
        }

        boolean covers(double lat, double lon) {
            double r = (lat0 - lat) / dLat, c = (lon - lon0) / dLon;
            return r >= 0 && c >= 0 && r <= rows - 1 && c <= cols - 1;
        }

        /** Bilinear; NaN, wenn einer der vier Punkte keine Daten hat. Meer liefert 0. */
        double sample(double lat, double lon) {
            double r = (lat0 - lat) / dLat, c = (lon - lon0) / dLon;
            int r0 = (int) Math.floor(r), c0 = (int) Math.floor(c);
            r0 = Math.max(0, Math.min(rows - 2, r0));
            c0 = Math.max(0, Math.min(cols - 2, c0));
            double fr = r - r0, fc = c - c0;
            double a = val(r0, c0), b = val(r0, c0 + 1), d = val(r0 + 1, c0), e = val(r0 + 1, c0 + 1);
            if (Double.isNaN(a + b + d + e)) return Double.NaN;
            return (a + (b - a) * fc) * (1 - fr) + (d + (e - d) * fc) * fr;
        }

        private double val(int r, int c) {
            short s = v[r * cols + c];
            if (s == nodata) return Double.NaN;
            if (s == sea) return 0;
            return s;
        }
    }

    private final List<Tile> tiles = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    /** Sucht und liest alle Kacheln in dir und dir/data/raw (rekursiv eine Ebene tief). */
    public static DemSource scan(File dir) {
        DemSource s = new DemSource();
        List<File> cand = new ArrayList<>();
        collect(dir, cand, 2);
        collect(new File(dir, "data/raw"), cand, 3);
        for (File f : cand) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            try {
                if (n.endsWith(".bil")) s.readBil(f);
                else if (n.startsWith("gt30") && (n.endsWith(".tif") || n.endsWith(".tiff"))) s.readGtopo(f);
            } catch (IOException | RuntimeException e) {
                s.notes.add(f.getName() + ": nicht lesbar (" + e.getMessage() + ")");
            }
        }
        s.tiles.sort((a, b) -> Integer.compare(a.rank, b.rank));
        return s;
    }

    /**
     * Fingerabdruck der Höhendateien im Projekt (Namen und Größen, ohne sie zu lesen). Ändert er sich,
     * etwa weil eine Kachel dazukam, baut {@link TerrainBuilder} das Gelände neu.
     */
    public static String signature(File dir) {
        List<File> cand = new ArrayList<>();
        collect(dir, cand, 2);
        collect(new File(dir, "data/raw"), cand, 3);
        List<String> parts = new ArrayList<>();
        for (File f : cand) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".bil") || (n.startsWith("gt30") && (n.endsWith(".tif") || n.endsWith(".tiff")))) parts.add(n + ":" + f.length());
        }
        java.util.Collections.sort(parts);
        return String.join(";", parts);
    }

    private static void collect(File d, List<File> out, int depth) {
        File[] fs = d.listFiles();
        if (fs == null || depth < 0) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (!n.equals("src") && !n.equals("build") && !n.equals("dist") && !n.equals("lib") && !n.startsWith(".")) collect(f, out, depth - 1);
            } else out.add(f);
        }
    }

    public boolean isEmpty() { return tiles.isEmpty(); }
    public List<String> notes() { return notes; }

    /** Kurze Beschreibung der gefundenen Kacheln, zum Beispiel „3 × GLSDEM 3″, 1 × GTOPO30“. */
    public String describe() {
        int fine = 0, coarse = 0;
        for (Tile t : tiles) if (t.rank == 0) fine++; else coarse++;
        return fine + " × GLSDEM/SRTM 3″" + (coarse > 0 ? ", " + coarse + " × GTOPO30" : "");
    }

    /** Höhe in Metern; 0 = Meer; NaN = keine Kachel deckt den Punkt. Die feinste deckende Kachel gewinnt. */
    public double height(double lat, double lon) {
        for (Tile t : tiles) {
            if (!t.covers(lat, lon)) continue;
            double h = t.sample(lat, lon);
            if (!Double.isNaN(h)) return h;
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ BIL mit ESRI-Kopf

    private void readBil(File bil) throws IOException {
        String base = bil.getPath().substring(0, bil.getPath().length() - 4);
        File hdr = new File(base + ".hdr");
        if (!hdr.exists()) throw new IOException("Kopf " + hdr.getName() + " fehlt");
        int rows = 0, cols = 0, bits = 16;
        double ulx = Double.NaN, uly = Double.NaN, xdim = Double.NaN, ydim = Double.NaN;
        short nodata = Short.MIN_VALUE;
        boolean little = true;
        for (String line : Files.readAllLines(hdr.toPath())) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 2) continue;
            switch (p[0].toUpperCase(Locale.ROOT)) {
                case "NROWS": rows = Integer.parseInt(p[1]); break;
                case "NCOLS": cols = Integer.parseInt(p[1]); break;
                case "NBITS": bits = Integer.parseInt(p[1]); break;
                case "BYTEORDER": little = p[1].equalsIgnoreCase("I"); break;
                case "ULXMAP": ulx = Double.parseDouble(p[1]); break;
                case "ULYMAP": uly = Double.parseDouble(p[1]); break;
                case "XDIM": xdim = Double.parseDouble(p[1]); break;
                case "YDIM": ydim = Double.parseDouble(p[1]); break;
                case "NODATA": nodata = (short) Double.parseDouble(p[1]); break;
                default: break;
            }
        }
        if (bits != 16 || rows <= 0 || cols <= 0 || Double.isNaN(ulx)) throw new IOException("Kopf nicht verstanden");
        // GLSDEM schreibt die Lage in Bogensekunden (ULXMAP −561600 = −156°); andere Kacheln in Grad
        double scale = Math.abs(ulx) > 360 || Math.abs(xdim) > 0.5 ? 1.0 / 3600.0 : 1.0;
        short[] v = readShorts(bil, rows * cols, little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN, 0);
        tiles.add(new Tile(bil.getName(), rows, cols, uly * scale, ulx * scale, ydim * scale, xdim * scale, v, nodata, (short) 0, 0));
    }

    private static short[] readShorts(File f, int n, ByteOrder order, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r"); FileChannel ch = raf.getChannel()) {
            ByteBuffer bb = ByteBuffer.allocate(n * 2).order(order);
            ch.position(offset);
            while (bb.hasRemaining() && ch.read(bb) > 0) { }
            if (bb.hasRemaining()) throw new IOException("Datei zu kurz");
            bb.flip();
            short[] v = new short[n];
            bb.asShortBuffer().get(v);
            return v;
        }
    }

    // ------------------------------------------------------------------ GTOPO30 als GeoTIFF (unkomprimiert)

    /**
     * Liest aus einer GTOPO30-Kachel nur den Ausschnitt um Hawaiʻi (18–21° N, 157–154° W). Erwartet
     * unkomprimierte 16-Bit-Streifen und die GeoTIFF-Marken ModelPixelScale und ModelTiepoint.
     */
    private void readGtopo(File f) throws IOException {
        byte[] head = new byte[(int) Math.min(f.length(), 1 << 20)];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) { raf.readFully(head); }
        ByteBuffer b = ByteBuffer.wrap(head);
        b.order(head[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (b.getShort(2) != 42) throw new IOException("kein TIFF");
        int ifd = b.getInt(4);
        int n = b.getShort(ifd) & 0xffff;
        int width = 0, height = 0, bps = 16, comp = 1, rowsPerStrip = 0;
        long stripOff = -1;
        int stripCount = 0, stripType = 4;
        double[] scale = null, tie = null;
        for (int i = 0; i < n; i++) {
            int e = ifd + 2 + i * 12;
            int tag = b.getShort(e) & 0xffff, type = b.getShort(e + 2) & 0xffff, cnt = b.getInt(e + 4);
            int val = type == 3 && cnt == 1 ? b.getShort(e + 8) & 0xffff : b.getInt(e + 8);
            switch (tag) {
                case 256: width = val; break;
                case 257: height = val; break;
                case 258: bps = val; break;
                case 259: comp = val; break;
                case 273: stripOff = val; stripCount = cnt; stripType = type; break;
                case 278: rowsPerStrip = val; break;
                case 33550: scale = doubles(b, val, cnt); break;
                case 33922: tie = doubles(b, val, cnt); break;
                default: break;
            }
        }
        if (comp != 1 || bps != 16 || scale == null || tie == null) throw new IOException("GeoTIFF nicht unterstützt (Kompression " + comp + ")");
        double dLon = scale[0], dLat = scale[1];
        double lonEdge = tie[3] - tie[0] * dLon, latEdge = tie[4] + tie[1] * dLat;   // Kante oben links
        // Ausschnitt Hawaiʻi
        int r0 = Math.max(0, (int) Math.floor((latEdge - 21.0) / dLat)), r1 = Math.min(height - 1, (int) Math.ceil((latEdge - 18.0) / dLat));
        int c0 = Math.max(0, (int) Math.floor((-157.0 - lonEdge) / dLon)), c1 = Math.min(width - 1, (int) Math.ceil((-154.0 - lonEdge) / dLon));
        if (r0 >= r1 || c0 >= c1) throw new IOException("deckt Hawaiʻi nicht");
        int rows = r1 - r0 + 1, cols = c1 - c0 + 1;
        short[] v = new short[rows * cols];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] row = new byte[width * 2];
            for (int r = r0; r <= r1; r++) {
                long off;
                if (rowsPerStrip > 0 && stripCount > 1) {
                    int strip = r / rowsPerStrip;
                    long so = stripType == 3 ? (b.getShort((int) stripOff + strip * 2) & 0xffff) : (b.getInt((int) stripOff + strip * 4) & 0xffffffffL);
                    off = so + (long) (r % rowsPerStrip) * width * 2;
                } else off = stripOff + (long) r * width * 2;
                raf.seek(off);
                raf.readFully(row);
                ByteBuffer rb = ByteBuffer.wrap(row).order(b.order());
                for (int c = c0; c <= c1; c++) v[(r - r0) * cols + (c - c0)] = rb.getShort(c * 2);
            }
        }
        // Meer (−9999) zählt als 0 m: so verläuft die Küste beim Interpolieren schräg statt in 1-km-Stufen
        tiles.add(new Tile(f.getName(), rows, cols, latEdge - (r0 + 0.5) * dLat, lonEdge + (c0 + 0.5) * dLon, dLat, dLon, v, Short.MIN_VALUE, (short) -9999, 1));
    }

    private static double[] doubles(ByteBuffer b, int off, int cnt) {
        double[] d = new double[cnt];
        for (int i = 0; i < cnt; i++) d[i] = b.getDouble(off + i * 8);
        return d;
    }
}
