package com.dan.kilauea.render;

import com.dan.kilauea.terrain.HeightGrid;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Gelände als Viererbaum mit Detailstufen (CDLOD, nach Strugar 2009): die Wurzel deckt das ganze Raster
 * (192 km), jede Teilung halbiert die Kante, die feinste Stufe hat Knoten von 94 m mit 1,5 m
 * Punktabstand. Jede Stufe gilt bis zu einer Entfernung von {@code lodRange} Knotengrößen; im äußeren
 * Fünftel ihres Bereichs gleiten die Punkte stufenlos in die Lage der gröberen Stufe (Geomorphing), so
 * dass weder Risse noch Springen entstehen.
 * <p>
 * Alle Knoten teilen sich ein Gitter aus 65 × 65 Punkten (Viertelknoten: 33 × 33); Lage und Größe
 * kommen je Instanz. Die Höhen holt der Vertex-Shader aus der Höhentextur (kubischer B-Spline, in der
 * Ferne aus gröberen Mip-Stufen) und legt das Feinrelief darauf. Knoten außerhalb des Sichtkegels
 * fallen weg (Höhenbereich je Knoten aus der Min-Max-Pyramide des Rasters).
 */
public final class TerrainLod {
    public static final int DIM = 64, LEVELS = 12;
    public static final double ROOT = HeightGrid.EXTENT;
    static final double LEAF = ROOT / (1 << (LEVELS - 1));
    /** Spielraum für das Feinrelief über und unter den Daten. */
    static final float RELIEF = 20f;

    final double[] range = new double[LEVELS];
    public double lodRange = 2.5;
    int heightTex, coverTex, geoTex, roadTex, summitTex;
    /** Lava und Grube des Lavasees (Phase 8), vom Renderer gesetzt. */
    LavaLayer lava;
    private final double[] yr = new double[2];
    private final Patch full = new Patch(DIM), quarter = new Patch(DIM / 2);
    private HeightGrid grid;

    public TerrainLod() { }

    /** Ohne Grafikkarte (Prüfung): nur Auswahl. */
    public TerrainLod(HeightGrid g) { grid = g; }
    public int nodes, vertices;
    private int minLod;

    static FloatBuffer direct(int n) { return ByteBuffer.allocateDirect(n * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer(); }

    /** Ein Gitter für viele Knoten. */
    private static final class Patch {
        final int dim;
        int vao, vbo, ibo, inst, count, instances;
        FloatBuffer data = direct(4 * 4096);

        Patch(int dim) { this.dim = dim; }

        void init(GL4 gl) {
            int n = dim + 1;
            float[] v = new float[n * n * 2];
            for (int j = 0, k = 0; j < n; j++)
                for (int i = 0; i < n; i++) { v[k++] = i / (float) dim; v[k++] = j / (float) dim; }
            int[] idx = new int[dim * dim * 6];
            int k = 0;
            for (int j = 0; j < dim; j++)
                for (int i = 0; i < dim; i++) {
                    int a = j * n + i, b = a + 1, c = a + n, d = c + 1;
                    // Diagonale im Wechsel, damit Hänge nicht in eine Richtung gestreift wirken
                    if (((i ^ j) & 1) == 0) { idx[k++] = a; idx[k++] = c; idx[k++] = d; idx[k++] = a; idx[k++] = d; idx[k++] = b; }
                    else { idx[k++] = a; idx[k++] = c; idx[k++] = b; idx[k++] = b; idx[k++] = c; idx[k++] = d; }
                }
            count = idx.length;
            int[] t = new int[3];
            gl.glGenVertexArrays(1, t, 0);
            vao = t[0];
            gl.glGenBuffers(3, t, 0);
            vbo = t[0];
            ibo = t[1];
            inst = t[2];
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 8, 0);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, data.capacity() * 4L, null, GL.GL_DYNAMIC_DRAW);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, 16, 0);
            gl.glVertexAttribDivisor(1, 1);
            gl.glBindVertexArray(0);
        }

        void add(double x0, double z0, double size, int lod) {
            if (data.remaining() < 4) {
                FloatBuffer b = direct(data.capacity() * 2);
                data.flip();
                b.put(data);
                data = b;
            }
            data.put((float) x0).put((float) z0).put((float) size).put(lod);
        }

        /** Phase 17: Knoten nach dem Abstand ihrer Mitte zur Kamera ordnen, nahe zuerst (früher Tiefentest). */
        void sortNearFirst(double cx, double cz) {
            int n = data.position() / 4;
            if (n < 2) return;
            float[] a = new float[n * 4];
            data.flip();
            data.get(a);
            data.clear();
            TerrainLod.sortNearFirst(a, n, cx, cz);
            data.put(a);
        }

        void upload(GL4 gl) {
            instances = data.position() / 4;
            data.flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, data.capacity() * 4L, null, GL.GL_DYNAMIC_DRAW);   // alten Puffer verwaisen
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, data.limit() * 4L, data);
            data.clear();
        }

        void draw(GL4 gl, Shader s) {
            if (instances == 0) return;
            s.set(gl, "uGridDim", (float) dim);
            gl.glBindVertexArray(vao);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0, instances);
        }

        void dispose(GL4 gl) {
            if (vao == 0) return;
            gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
            gl.glDeleteBuffers(3, new int[]{vbo, ibo, inst}, 0);
            vao = 0;
        }
    }

    /**
     * Phase 17: n Knoten (je x0, z0, Kante, Stufe) nach dem Abstand ihrer Mitte zu (cx, cz) ordnen, nahe zuerst.
     * Die Grafikkarte zeichnet die Instanzen in dieser Reihenfolge; so verdeckt das Nahe früh, und der frühe
     * Tiefentest spart die Rechnung für das Gelände dahinter.
     */
    public static void sortNearFirst(float[] a, int n, double cx, double cz) {
        long[] key = new long[n];
        for (int i = 0; i < n; i++) {
            double h = a[4 * i + 2] * 0.5, dx = a[4 * i] + h - cx, dz = a[4 * i + 1] + h - cz;
            // Abstand (auf 16 m² gerundet) in den oberen Bits, Nummer in den unteren: eine Sortierung ohne Objekte
            key[i] = ((long) Math.min(Integer.MAX_VALUE, (dx * dx + dz * dz) / 16.0) << 20) | i;
        }
        java.util.Arrays.sort(key);
        float[] b = a.clone();
        for (int k = 0; k < n; k++) {
            int i = (int) (key[k] & 0xFFFFF);
            System.arraycopy(b, 4 * i, a, 4 * k, 4);
        }
    }

    void init(GL4 gl) {
        full.init(gl);
        quarter.init(gl);
    }

    boolean ready() { return grid != null; }

    // ------------------------------------------------------------------ Detailkacheln

    /** Höchstens so viele Kacheln kennt der Shader. */
    public static final int MAX_TILES = 8;
    private java.util.List<com.dan.kilauea.terrain.DetailTile> tiles = java.util.List.of();
    private final float[] tileRect = new float[4 * MAX_TILES], tileUV = new float[4 * MAX_TILES];
    private int atlasW, atlasH;

    /**
     * Alle Detailkacheln in einen Atlas (R16F mit Mips, Einheit 9): nebeneinander nach Höhe gestapelt,
     * mit 64 Punkten Abstand, damit die groben Mips nicht von der Nachbarkachel borgen.
     */
    void uploadDetail(GL4 gl, java.util.List<com.dan.kilauea.terrain.DetailTile> list) {
        if (summitTex != 0) gl.glDeleteTextures(1, new int[]{summitTex}, 0);
        summitTex = 0;
        tiles = list.size() > MAX_TILES ? list.subList(0, MAX_TILES) : list;
        if (tiles.isEmpty()) return;
        int[][] pos = pack(tiles);
        summitTex = Gpu.texture(gl, atlasW, atlasH, GL4.GL_R16F, true);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        // Atlas mit 0 füllen, dann die Kacheln hinein
        FloatBuffer zero = Buffers.newDirectFloatBuffer(atlasW * 64);
        for (int y = 0; y < atlasH; y += 64)
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, y, atlasW, Math.min(64, atlasH - y), GL4.GL_RED, GL.GL_FLOAT, zero);
        for (int i = 0; i < tiles.size(); i++) {
            var t = tiles.get(i);
            var sp = t.spec;
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, pos[i][0], pos[i][1], sp.nx, sp.nz, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(t.delta));
            tileRect[4 * i] = (float) sp.x0;
            tileRect[4 * i + 1] = (float) sp.z0;
            tileRect[4 * i + 2] = (float) sp.cell;
            tileRect[4 * i + 3] = 0;
            tileUV[4 * i] = pos[i][0];
            tileUV[4 * i + 1] = pos[i][1];
            tileUV[4 * i + 2] = sp.nx;
            tileUV[4 * i + 3] = sp.nz;
        }
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Lage jeder Kachel im Atlas (Himmelslinie: jede Kachel so hoch oben wie möglich); setzt atlasW, atlasH. */
    private int[][] pack(java.util.List<com.dan.kilauea.terrain.DetailTile> list) {
        final int pad = 64;
        int W = 0;
        for (var t : list) W = Math.max(W, t.spec.nx + pad);
        W = Math.max(W, 5400);
        Integer[] order = new Integer[list.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> list.get(b).spec.nz - list.get(a).spec.nz);
        int[] sky = new int[W];
        int[][] pos = new int[list.size()][];
        int H = 0;
        for (int idx : order) {
            int w = list.get(idx).spec.nx + pad, h = list.get(idx).spec.nz + pad;
            int bestX = 0, bestY = Integer.MAX_VALUE;
            for (int x = 0; x + w <= W; x += 16) {
                int y = 0;
                for (int k = x; k < x + w; k++) y = Math.max(y, sky[k]);
                if (y < bestY) { bestY = y; bestX = x; }
            }
            for (int k = bestX; k < bestX + w; k++) sky[k] = bestY + h;
            pos[idx] = new int[]{bestX, bestY};
            H = Math.max(H, bestY + h);
        }
        atlasW = W;
        atlasH = H;
        return pos;
    }

    /** Höhenbereich, den die Kacheln über einem Knoten zusätzlich öffnen. */
    private void detailRange(double x0, double z0, double size, double[] yr) {
        for (var t : tiles) {
            var sp = t.spec;
            if (x0 < sp.x1() && x0 + size > sp.x0 && z0 < sp.z1() && z0 + size > sp.z0) {
                yr[0] += t.minDelta;
                yr[1] += t.maxDelta;
            }
        }
    }

    /** Der Atlas in Zahlen (für das Bedienfeld und die Prüfung). */
    public String detailInfo() { return String.format(java.util.Locale.GERMAN, "%d Kacheln, Atlas %d × %d", tiles.size(), atlasW, atlasH); }

    void upload(GL4 gl, HeightGrid g) {
        int n = HeightGrid.N;
        if (heightTex != 0) gl.glDeleteTextures(3, new int[]{heightTex, coverTex, geoTex}, 0);
        if (roadTex != 0) gl.glDeleteTextures(1, new int[]{roadTex}, 0);
        heightTex = Gpu.texture(gl, n, n, GL4.GL_R32F, true);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(g.h));
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        coverTex = Gpu.texture(gl, n, n, GL.GL_RGBA8, true);
        ByteBuffer cb = Buffers.newDirectByteBuffer(g.cover.length);
        cb.put(g.cover).flip();
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, cb);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, wantAnisotropy);
        roadTex = Gpu.texture(gl, n, n, GL4.GL_R8, false);
        byte[] rm = g.road != null ? g.road : new byte[n * n];
        ByteBuffer rb = Buffers.newDirectByteBuffer(rm.length);
        rb.put(rm).flip();
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, rb);
        geoTex = Gpu.texture(gl, n, n, GL.GL_RGBA8, true);
        cb.clear();
        cb.put(g.geo).flip();
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, cb);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, wantAnisotropy);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        anisotropy = wantAnisotropy;
        grid = g;
    }

    /** Phase 17: anisotrope Filterung der Bodenkarten nach der Qualitätsstufe (auf dem GL-Faden nachgezogen). */
    volatile int wantAnisotropy = 8;
    private int anisotropy = 8;

    void applyAnisotropy(GL4 gl) {
        int w = wantAnisotropy;
        if (coverTex == 0 || w == anisotropy) return;
        anisotropy = w;
        if (!gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) return;
        for (int t : new int[]{coverTex, geoTex}) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, t);
            gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.max(1, w));
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Sichtweiten je Stufe (Stufe 0 die feinste). */
    void ranges() {
        for (int l = 0; l < LEVELS; l++) range[l] = LEAF * (1 << l) * lodRange;
    }

    // ------------------------------------------------------------------ Auswahl

    private final float[] mm = new float[2];
    private final double[][] planes = new double[4][4];
    private double cx, cy, cz;
    private boolean cull;

    /**
     * Wählt die Knoten für die Kamera bei (cam) und lädt die Instanzen hoch. vp: Blick·Projektion für
     * das Ausblenden außerhalb des Bildes, oder null (Schatten: alles im Umkreis).
     */
    void select(GL4 gl, double[] cam, float[] vp) { select(gl, cam, vp, 0); }

    /**
     * Wie {@link #select(GL4, double[], float[])}, aber nie feiner als Stufe minLod (für die ferne
     * Schattenkaskade: ihre Kartenpunkte sind gröber als die feinsten Knoten, die Arbeit wäre umsonst).
     */
    void select(GL4 gl, double[] cam, float[] vp, int minLod) {
        this.minLod = minLod;
        collect(cam, vp);
        this.minLod = 0;
        full.sortNearFirst(cam[0], cam[2]);
        quarter.sortNearFirst(cam[0], cam[2]);
        full.upload(gl);
        quarter.upload(gl);
    }

    /** Nur die Auswahl, ohne Grafikkarte; Ergebnis über {@link #selection(boolean)}. */
    public void collect(double[] cam, float[] vp) {
        full.data.clear();
        quarter.data.clear();
        ranges();
        cx = cam[0];
        cy = cam[1];
        cz = cam[2];
        cull = vp != null;
        if (cull) frustum(vp);
        nodes = 0;
        double x0 = HeightGrid.X0 - HeightGrid.CELL / 2, z0 = HeightGrid.Z0 - HeightGrid.CELL / 2;
        if (!node(x0, z0, ROOT, LEVELS - 1)) { full.add(x0, z0, ROOT, LEVELS - 1); nodes++; }
        vertices = full.data.position() / 4 * (DIM + 1) * (DIM + 1) + quarter.data.position() / 4 * (DIM / 2 + 1) * (DIM / 2 + 1);
    }

    /** Gewählte Knoten als (x0, z0, Kante, Stufe) je vier Werte; quarter: die Viertelknoten (halbes Gitter). */
    public float[] selection(boolean quarterNodes) {
        FloatBuffer b = (quarterNodes ? quarter : full).data.duplicate();
        b.flip();
        float[] r = new float[b.remaining()];
        b.get(r);
        return r;
    }

    /** Wie Strugar: false = Knoten liegt ganz außerhalb seiner Sichtweite (der Elternknoten zeichnet die Fläche). */
    private boolean node(double x0, double z0, double size, int lod) {
        grid.range(x0, z0, size, mm);
        double y0 = mm[0] - RELIEF, y1 = mm[1] + RELIEF;
        // Detailkacheln: ihre Wände reichen über und unter das 75-m-Gelände
        if (!tiles.isEmpty()) {
            yr[0] = y0;
            yr[1] = y1;
            detailRange(x0, z0, size, yr);
            y0 = yr[0];
            y1 = yr[1];
        }
        // Phase 8: Grube des Lavasees (bis 45 m tiefer) und Lava dieses Laufs (einige zehn Meter höher)
        if (lava != null && lava.show) {
            y0 -= 50;
            y1 += 40;
        }
        // Phase 12: Verformung (Gipfel vor dem Einsturz liegt bis rund 270 m höher)
        if (com.dan.kilauea.terrain.Deformation.active()) {
            yr[0] = y0;
            yr[1] = y1;
            com.dan.kilauea.terrain.Deformation.range(x0, z0, size, yr);
            y0 = yr[0];
            y1 = yr[1];
        }
        if (!sphereBox(range[lod], x0, y0, z0, size, y1)) return false;
        if (cull && !visible(x0, y0, z0, size, y1)) return true;
        if (lod <= minLod || !sphereBox(range[lod - 1], x0, y0, z0, size, y1)) {
            full.add(x0, z0, size, lod);
            nodes++;
            return true;
        }
        double h = size / 2;
        for (int c = 0; c < 4; c++) {
            double qx = x0 + (c & 1) * h, qz = z0 + (c >> 1) * h;
            if (!node(qx, qz, h, lod - 1)) {
                quarter.add(qx, qz, h, lod);
                nodes++;
            }
        }
        return true;
    }

    private boolean sphereBox(double r, double x0, double y0, double z0, double size, double y1) {
        double dx = Math.max(0, Math.max(x0 - cx, cx - (x0 + size)));
        double dy = Math.max(0, Math.max(y0 - cy, cy - y1));
        double dz = Math.max(0, Math.max(z0 - cz, cz - (z0 + size)));
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** Vier Seitenebenen aus der Matrix (Gribb/Hartmann); nah und fern spielen keine Rolle. */
    private void frustum(float[] m) {
        for (int p = 0; p < 4; p++) {
            int row = p >> 1;
            double s = (p & 1) == 0 ? 1 : -1;
            double[] q = planes[p];
            for (int c = 0; c < 4; c++) q[c] = m[c * 4 + 3] + s * m[c * 4 + row];
        }
    }

    private boolean visible(double x0, double y0, double z0, double size, double y1) {
        // Erdkrümmung: der Knoten liegt bis zu d²/2R tiefer, als seine Höhen sagen
        double fx = Math.max(Math.abs(x0 - cx), Math.abs(x0 + size - cx)), fz = Math.max(Math.abs(z0 - cz), Math.abs(z0 + size - cz));
        y0 -= (fx * fx + fz * fz) / (2 * 6_371_008.8);
        for (double[] q : planes) {
            double px = q[0] >= 0 ? x0 + size : x0, py = q[1] >= 0 ? y1 : y0, pz = q[2] >= 0 ? z0 + size : z0;
            if (q[0] * px + q[1] * py + q[2] * pz + q[3] < 0) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Uniforms und Texturen setzen (Einheiten 5 bis 8) und alle gewählten Knoten zeichnen. */
    void draw(GL4 gl, Shader s, double detail) {
        bindTextures(gl, s, detail);
        full.draw(gl, s);
        quarter.draw(gl, s);
    }

    /** Nur Texturen und Uniforms der Höhenfunktion (auch für Straßen). */
    void bindTextures(GL4 gl, Shader s, double detail) {
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, heightTex);
        gl.glActiveTexture(GL.GL_TEXTURE6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, coverTex);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, geoTex);
        gl.glActiveTexture(GL.GL_TEXTURE8);
        gl.glBindTexture(GL.GL_TEXTURE_2D, roadTex);
        gl.glActiveTexture(GL.GL_TEXTURE9);
        gl.glBindTexture(GL.GL_TEXTURE_2D, summitTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uHeight", 5);
        s.set(gl, "uCover", 6);
        s.set(gl, "uGeo", 7);
        s.set(gl, "uRoad", 8);
        s.set(gl, "uTiles", 9);
        int n = summitTex != 0 ? tiles.size() : 0;
        gl.glUniform1i(s.loc(gl, "uTileCount"), n);
        if (n > 0) {
            gl.glUniform4fv(s.loc(gl, "uTileRect"), n, tileRect, 0);
            gl.glUniform4fv(s.loc(gl, "uTileUV"), n, tileUV, 0);
        }
        s.set(gl, "uGrid", (float) HeightGrid.X0, (float) HeightGrid.Z0, (float) HeightGrid.CELL);
        s.set(gl, "uGridN", (float) HeightGrid.N);
        s.set(gl, "uLodRange", (float) range[0], 0.8f, (float) lodRange);
        s.set(gl, "uRelief", (float) detail);
        if (lava != null) lava.bind(gl, s);
    }

    int instances() { return full.instances + quarter.instances; }

    void dispose(GL4 gl) {
        full.dispose(gl);
        quarter.dispose(gl);
        if (heightTex != 0) gl.glDeleteTextures(3, new int[]{heightTex, coverTex, geoTex}, 0);
        if (roadTex != 0) gl.glDeleteTextures(1, new int[]{roadTex}, 0);
        heightTex = coverTex = geoTex = roadTex = 0;
    }
}
