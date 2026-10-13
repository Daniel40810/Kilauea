package com.dan.kilauea.flora;

/** Ein Netz für die Grafikkarte: Lage, Normale, Farbe (linear, mit Umgebungslicht) je Punkt und Dreiecke. */
public final class MeshData {
    public final float[] pos, nrm, col;
    public final int[] tri;
    /** Höhe (m) und Kronenhalbmesser (m) bei Maßstab 1. */
    public final float height, radius;

    public MeshData(float[] pos, float[] nrm, float[] col, int[] tri, float height, float radius) {
        this.pos = pos; this.nrm = nrm; this.col = col; this.tri = tri; this.height = height; this.radius = radius;
    }

    public int vertices() { return pos.length / 3; }
    public int triangles() { return tri.length / 3; }

    /** Aus einer Stufe von {@link com.dan.forest.TreeMesh}; Blüten färben einzelne Blattbüschel rot (ʻōhiʻa lehua). */
    public static MeshData of(com.dan.forest.TreeMesh t, float bloom, long seed) {
        int nv = t.nv;
        float[] p = java.util.Arrays.copyOf(t.pos, 3 * nv), n = java.util.Arrays.copyOf(t.nrm, 3 * nv), c = java.util.Arrays.copyOf(t.col, 3 * nv);
        java.util.Random r = new java.util.Random(seed);
        // Laubballen der groben Stufen (Ikosaeder je Ast): glatte Normalen nach außen machen sie zu
        // Lampenschirmen mit hellem Dach und schwarzem Bauch (Statusbild Phase 4). Normalen je Ecke streuen
        // und nach oben neigen, Farbe fleckig: so lesen sie sich als Laub.
        java.util.Random jr = new java.util.Random(seed * 31 + 7);
        for (int i = 0; i < nv; i++) {
            if (t.part[i] != com.dan.forest.TreeMesh.CLUMP) continue;
            double nx = n[3 * i] * 0.55 + jr.nextGaussian() * 0.55, ny = n[3 * i + 1] * 0.55 + jr.nextGaussian() * 0.55 + 0.45, nz = n[3 * i + 2] * 0.55 + jr.nextGaussian() * 0.55;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
            n[3 * i] = (float) (nx / l); n[3 * i + 1] = (float) (ny / l); n[3 * i + 2] = (float) (nz / l);
            float sp = (float) (0.8 + 0.4 * jr.nextDouble());
            c[3 * i] *= sp; c[3 * i + 1] *= sp; c[3 * i + 2] *= sp;
        }
        java.util.Map<Integer, Boolean> red = new java.util.HashMap<>();
        for (int i = 0; i < nv; i++) {
            boolean leafy = t.part[i] == com.dan.forest.TreeMesh.LEAF || t.part[i] == com.dan.forest.TreeMesh.CLUMP;
            if (!leafy || bloom <= 0) continue;
            int key = t.part[i] == com.dan.forest.TreeMesh.LEAF ? t.leaf[i] : -1 - (i / 12);
            boolean b = red.computeIfAbsent(key, k -> r.nextFloat() < bloom);
            if (b) {
                float ao = t.ao != null ? t.ao[i] : 1f;
                c[3 * i] = 0.30f * ao; c[3 * i + 1] = 0.025f * ao; c[3 * i + 2] = 0.03f * ao;
            }
        }
        return new MeshData(p, n, c, java.util.Arrays.copyOf(t.tri, 3 * t.nt), t.model.height, t.model.crownRadius);
    }
}
