package com.dan.kilauea.flora;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hāpuʻu (Cibotium glaucum), der Baumfarn im Unterwuchs des Regenwalds: ein Stamm aus Wurzelfilz von
 * 1 bis 5 m, oben ein Schopf aus 10 bis 16 Wedeln von 2 bis 3 m, die bogig nach außen fallen; junge
 * Wedel stehen eingerollt in der Mitte [L: Little &amp; Skolmen 1989; Maße genähert].
 * Jeder Wedel ist ein gebogener Streifen mit gezackten Rändern (Fiedern), beidseitig.
 */
public final class TreeFern {
    private TreeFern() { }

    /** lod 0: 14 Wedel mit 8 Abschnitten, lod 1: 8 Wedel mit 4 Abschnitten. */
    public static MeshData build(long seed, int lod) {
        Random r = new Random(seed * 31 + 7);
        List<float[]> v = new ArrayList<>();   // x y z nx ny nz r g b
        List<int[]> t = new ArrayList<>();
        float H = 2.0f + 2.8f * r.nextFloat();
        float tr = 0.16f + 0.05f * r.nextFloat();
        // Stamm: Achteck, zur Spitze kaum schlanker, rotbraun und faserig
        int sides = lod == 0 ? 8 : 5, rings = 4;
        float lean = (r.nextFloat() - 0.5f) * 0.25f, leanDir = r.nextFloat() * 6.283f;
        for (int i = 0; i <= rings; i++) {
            float y = H * i / rings, rr = tr * (1.15f - 0.25f * i / rings);
            float ox = (float) Math.cos(leanDir) * lean * y * y / H, oz = (float) Math.sin(leanDir) * lean * y * y / H;
            for (int k = 0; k < sides; k++) {
                float a = 6.2832f * k / sides;
                float nx = (float) Math.cos(a), nz = (float) Math.sin(a);
                float shade = 0.75f + 0.25f * i / rings;
                v.add(new float[]{ox + nx * rr, y, oz + nz * rr, nx, 0, nz, 0.075f * shade, 0.050f * shade, 0.032f * shade});
            }
        }
        for (int i = 0; i < rings; i++)
            for (int k = 0; k < sides; k++) {
                int a = i * sides + k, b = i * sides + (k + 1) % sides, c = a + sides, d = b + sides;
                t.add(new int[]{a, c, b});
                t.add(new int[]{b, c, d});
            }
        float topX = (float) Math.cos(leanDir) * lean * H, topZ = (float) Math.sin(leanDir) * lean * H;
        int fronds = lod == 0 ? 12 + r.nextInt(5) : 8;
        int seg = lod == 0 ? 8 : 4;
        float maxR = 0;
        for (int f = 0; f < fronds; f++) {
            float az = 6.2832f * (f + 0.3f * r.nextFloat()) / fronds;
            float len = 2.0f + 1.1f * r.nextFloat();
            float rise = 0.55f + 0.5f * r.nextFloat();          // Anstieg am Ansatz
            float droop = 1.1f + 0.6f * r.nextFloat();          // wie stark er nach außen fällt
            float w0 = 0.55f + 0.2f * r.nextFloat();
            float ca = (float) Math.cos(az), sa = (float) Math.sin(az);
            // Farbe: oberseits frisch grün, zur Spitze heller; ältere Wedel unten gelblich
            float age = r.nextFloat();
            float cr = 0.050f + 0.03f * age, cg = 0.095f - 0.01f * age, cb = 0.022f;
            int base = v.size();
            for (int s = 0; s <= seg; s++) {
                float u = s / (float) seg;
                float d = len * u;
                float y = H + rise * d * (1 - u) - droop * u * u * len * 0.45f;
                float cxp = topX + ca * d * 0.92f, czp = topZ + sa * d * 0.92f;
                float w = w0 * (float) Math.sin(Math.PI * Math.min(1, u * 1.15f + 0.05f)) * (1 - 0.3f * u);
                // Fiedern: gezackter Rand
                float jag = (s % 2 == 0 ? 1f : 0.65f);
                float px = -sa * w * jag, pz = ca * w * jag;
                // Wedel liegt leicht dachförmig: Normale nach oben, etwas nach außen
                float nx = ca * 0.25f, ny = 1f, nz = sa * 0.25f;
                float tip = 0.9f + 0.25f * u;
                v.add(new float[]{cxp + px, y - 0.08f * w, czp + pz, nx - sa * 0.3f, ny, nz + ca * 0.3f, cr * tip, cg * tip, cb * tip});
                v.add(new float[]{cxp - px, y - 0.08f * w, czp - pz, nx + sa * 0.3f, ny, nz - ca * 0.3f, cr * tip, cg * tip, cb * tip});
                v.add(new float[]{cxp, y + 0.05f, czp, nx, ny, nz, cr * tip * 1.05f, cg * tip * 1.05f, cb * tip});
                maxR = Math.max(maxR, d);
            }
            for (int s = 0; s < seg; s++) {
                int a = base + 3 * s, b = a + 3;
                t.add(new int[]{a, b, a + 2});
                t.add(new int[]{a + 2, b, b + 2});
                t.add(new int[]{a + 2, b + 2, a + 1});
                t.add(new int[]{a + 1, b + 2, b + 1});
            }
        }
        // junge, eingerollte Wedel (Fiddleheads) in der Mitte: kleine Kegel
        if (lod == 0) {
            int n = 3 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                float a = r.nextFloat() * 6.28f, rr = 0.12f * r.nextFloat();
                float bx = topX + (float) Math.cos(a) * rr, bz = topZ + (float) Math.sin(a) * rr;
                int b0 = v.size();
                float hgt = 0.35f + 0.3f * r.nextFloat();
                for (int k = 0; k < 4; k++) {
                    float aa = 1.5708f * k;
                    v.add(new float[]{bx + 0.04f * (float) Math.cos(aa), H, bz + 0.04f * (float) Math.sin(aa), (float) Math.cos(aa), 0.3f, (float) Math.sin(aa), 0.10f, 0.085f, 0.045f});
                }
                v.add(new float[]{bx, H + hgt, bz, 0, 1, 0, 0.12f, 0.11f, 0.06f});
                for (int k = 0; k < 4; k++) t.add(new int[]{b0 + k, b0 + 4, b0 + (k + 1) % 4});
            }
        }
        float[] pos = new float[v.size() * 3], nrm = new float[v.size() * 3], col = new float[v.size() * 3];
        for (int i = 0; i < v.size(); i++) {
            float[] q = v.get(i);
            pos[3 * i] = q[0]; pos[3 * i + 1] = q[1]; pos[3 * i + 2] = q[2];
            float l = (float) Math.sqrt(q[3] * q[3] + q[4] * q[4] + q[5] * q[5]);
            nrm[3 * i] = q[3] / l; nrm[3 * i + 1] = q[4] / l; nrm[3 * i + 2] = q[5] / l;
            col[3 * i] = q[6]; col[3 * i + 1] = q[7]; col[3 * i + 2] = q[8];
        }
        int[] tri = new int[t.size() * 3];
        for (int i = 0; i < t.size(); i++) System.arraycopy(t.get(i), 0, tri, 3 * i, 3);
        return new MeshData(pos, nrm, col, tri, H + 0.6f, maxR);
    }
}
