package com.dan.kilauea.sound;

/**
 * Eine Schallquelle im Raum für einen Block: Lautstärke links und rechts (gleitend über den Block, ohne
 * Knacken), Tiefpass nach Entfernung und hinter dem Kopf, unter Wasser fast stumm (Luftschall dringt
 * kaum ins Wasser) [A].
 */
final class Spot {
    private final Dsp.Biquad lp = new Dsp.Biquad();
    private float gl, gr, dl, dr;
    private double cut = -1;
    final Ear.Where w = new Ear.Where();
    double dist;

    /** Ziel für den nächsten Block: Quelle (x, y, z) mit spl0 dB in r0 m. */
    void aim(Scene s, double x, double y, double z, double spl0, double r0, int n) {
        w.at(s, x, y, z);
        dist = w.dist;
        double amp = Ear.level(Ear.spl(spl0, r0, w.dist)) * (1 - 0.25 * w.behind);
        double c = Ear.cutoff(w.dist) * (1 - 0.45 * w.behind);
        if (s.underwater) { amp *= 0.06; c = Math.min(c, 320); }
        if (s.inTube) c = Math.min(c, 2500);
        float tl = (float) (amp * w.gl), tr = (float) (amp * w.gr);
        if (Math.abs(c - cut) > cut * 0.04) { lp.set(Dsp.Biquad.LP, c, 0.707); cut = c; }
        dl = (tl - gl) / n;
        dr = (tr - gr) / n;
    }

    /** Ohne Raum (Wind am Ohr, eigene Schritte): Pegel direkt, Mitte oder leicht seitlich. */
    void aimDirect(double amp, double pan, double cutoff, int n) {
        double a = (pan + 1) * Math.PI / 4;
        float tl = (float) (amp * Math.cos(a) * Math.sqrt(2) * 0.85), tr = (float) (amp * Math.sin(a) * Math.sqrt(2) * 0.85);
        if (Math.abs(cutoff - cut) > cut * 0.04) { lp.set(Dsp.Biquad.LP, cutoff, 0.707); cut = cutoff; }
        dl = (tl - gl) / n;
        dr = (tr - gr) / n;
    }

    /** Stumm schalten (gleitend über den Block). */
    void silence(int n) { dl = -gl / n; dr = -gr / n; }

    boolean audible() { return Math.abs(gl) + Math.abs(gr) + Math.abs(dl) + Math.abs(dr) > 1e-6; }

    void mix(float mono, float[] L, float[] R, int i) {
        float y = lp.process(mono);
        gl += dl; gr += dr;
        L[i] += y * gl;
        R[i] += y * gr;
    }
}
