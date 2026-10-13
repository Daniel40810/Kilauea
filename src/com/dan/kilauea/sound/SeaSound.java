package com.dan.kilauea.sound;

import com.dan.kilauea.sea.Waves;

/**
 * Brandung: Jeder der nächsten Küstenpunkte bricht im Takt der Dünung (Phase nach linearer Wellentheorie
 * wie im Bild, {@link Waves#deepPhase}) und der kürzeren See aus dem Passat. An flachen Ufern ein langes
 * Rauschen, an Klippen ein dumpfer Schlag mit Gischtzischen; laut wie rund 82 dB in 20 m bei 1 m
 * Wellenhöhe, +20 dB je Verzehnfachung der Höhe [G]. Der Schall läuft mit 343 m/s, darum brechen ferne
 * Wellen hörbar später [A]. Unter Wasser: tiefes Grollen und Blasen [G].
 */
final class SeaSound implements SoundEngine.Synth {
    static final int SLOTS = 16, NOISE = 6;
    private final Spot[] spot = new Spot[SLOTS];
    private final Dsp.Pink[] pink = new Dsp.Pink[NOISE];
    private final Dsp.Brown[] brown = new Dsp.Brown[NOISE];
    private final Dsp.Biquad[] hiss = new Dsp.Biquad[SLOTS], low = new Dsp.Biquad[SLOTS];
    private final double[] env = new double[SLOTS], boom = new double[SLOTS], prevA = new double[SLOTS], prevB = new double[SLOTS],
            pendT = new double[SLOTS * 2], pendA = new double[SLOTS * 2];
    private final boolean[] live = new boolean[SLOTS], cliff = new boolean[SLOTS];
    private final double[] sx = new double[SLOTS], sz = new double[SLOTS];
    private final Dsp.Rand rnd = new Dsp.Rand(17);
    // unter Wasser
    private final Dsp.Brown uw = new Dsp.Brown(3);
    private final Dsp.Biquad uwF = new Dsp.Biquad().set(Dsp.Biquad.LP, 180, 0.7);
    private final Spot uwSpot = new Spot();
    private double blip, blipF, blipPh;
    private boolean uwLive;

    SeaSound() {
        for (int i = 0; i < SLOTS; i++) {
            spot[i] = new Spot();
            hiss[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 700, 0.45);
            low[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 140, 0.8);
            prevA[i] = prevB[i] = Double.NaN;
            pendT[2 * i] = pendT[2 * i + 1] = -1;
        }
        for (int i = 0; i < NOISE; i++) { pink[i] = new Dsp.Pink(300 + i); brown[i] = new Dsp.Brown(400 + i); }
    }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        double dt = n / (double) Dsp.RATE;
        for (int k = 0; k < SLOTS; k++) {
            boolean on = k < s.coastCount && !s.underwater;
            if (!on && !live[k] && env[k] < 1e-3 && boom[k] < 1e-3) continue;
            if (on) {
                double x = s.coast[4 * k], z = s.coast[4 * k + 1];
                boolean cl = s.coast[4 * k + 2] > 0.5;
                // anderer Punkt in diesem Platz: Takt neu beginnen
                if (Math.hypot(x - sx[k], z - sz[k]) > 60) { prevA[k] = prevB[k] = Double.NaN; }
                sx[k] = x; sz[k] = z; cliff[k] = cl;
                double d = s.coast[4 * k + 3];
                double h = Math.max(0.05, s.swellH), hs = Math.max(0.0, s.seaH);
                spot[k].aim(s, x, 1.5, z, (cl ? 86 : 82) + 20 * Math.log10(Math.max(h, hs * 0.8) / 1.0), 20, n);
                // Kämme: Dünung und See; ein Kamm bricht, wenn die Phase durch 0 läuft
                double st = s.seaTime + Math.max(0, t - s.stamp);   // Zeit des Bildes, zwischen zwei Schnappschüssen weiter
                double pa = phase(Waves.deepPhase(x, z, s.swellDx, s.swellDz, s.swellT, st));
                double pb = phase(Waves.deepPhase(x, z, s.seaDx, s.seaDz, s.seaT, st));
                if (!Double.isNaN(prevA[k]) && Math.abs(pa - prevA[k]) > 0.5) schedule(k, 0, t + d / Ear.C, 0.75 + 0.5 * rnd.unit());
                if (!Double.isNaN(prevB[k]) && Math.abs(pb - prevB[k]) > 0.5 && hs > 0.15) schedule(k, 1, t + d / Ear.C, (hs / Math.max(h, 0.1)) * (0.3 + 0.3 * rnd.unit()));
                prevA[k] = pa; prevB[k] = pb;
            } else spot[k].silence(n);
            live[k] = on;
            for (int j = 0; j < 2; j++) {
                int q = 2 * k + j;
                if (pendT[q] >= 0 && t + dt >= pendT[q]) {
                    if (cliff[k]) boom[k] = Math.min(2, boom[k] + pendA[q]); else env[k] = Math.min(2, env[k] + pendA[q]);
                    pendT[q] = -1;
                }
            }
            // Rauschen der Brandung: an flachen Ufern lang, an Klippen ein Schlag und Zischen
            float atk = (float) Math.exp(-1 / (3.2 * Dsp.RATE)), bd = (float) Math.exp(-1 / (0.9 * Dsp.RATE));
            Dsp.Pink pn = pink[k % NOISE];
            Dsp.Brown bn = brown[k % NOISE];
            for (int i = 0; i < n; i++) {
                env[k] *= atk;
                boom[k] *= bd;
                float base = 0.16f;
                float o = hiss[k].process(pn.next()) * (float) (base + env[k] * 1.1 + boom[k] * 0.6)
                        + low[k].process(bn.next()) * (float) (base * 0.8 + env[k] * 0.5 + boom[k] * 2.2);
                spot[k].mix(o * 3.6f, L, R, i);   // geeicht: 82 dB in 20 m bei 1 m Welle
            }
        }
        // unter Wasser: Grollen der Brandung von fern, dazu Blasen [G]
        boolean u = s.underwater;
        if (u || uwLive) {
            if (u) uwSpot.aimDirect(Ear.level(62 + 20 * Math.log10(Math.max(0.2, s.swellH))), 0, 900, n); else uwSpot.silence(n);
            uwLive = u;
            for (int i = 0; i < n; i++) {
                if (rnd.unit() < 1.5 / Dsp.RATE) { blip = 1; blipF = 350 + 900 * rnd.unit(); }
                blip *= 0.9993;
                blipF *= 1.00004;
                blipPh += 2 * Math.PI * blipF / Dsp.RATE;
                uwSpot.mix(uwF.process(uw.next()) * 1.4f + (float) (Math.sin(blipPh) * blip * 0.25), L, R, i);
            }
        }
    }

    private void schedule(int k, int j, double when, double amp) {
        pendT[2 * k + j] = when;
        pendA[2 * k + j] = amp;
    }

    static double phase(double p) {
        double f = p / (2 * Math.PI);
        return f - Math.floor(f);
    }
}
