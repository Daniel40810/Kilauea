package com.dan.kilauea.sound;

import com.dan.kilauea.monitor.Seismo;

/**
 * Beben als Grollen: Jedes Beben des Magmamodells kommt mit der P-Welle (6 km/s) als kurzer, dumpfer
 * Schlag an — nahe, flache Beben hört man als „Bumm“ —, mit der S-Welle (3,5 km/s) folgt das Grollen,
 * so lange, wie die Aufzeichnung dauert (nach Lee u. a., hier auf ein Viertel gekürzt) [L Laufzeiten,
 * Dauer; Klang G]. Die Stärke folgt dem Ausschlag am Hörer (Hutton & Boore, wie der Seismograf); ab
 * rund 0,5 mm rieselt Gestein [G]. Bei starkem Zeitraffer werden viele Beben zu einem Dauergrollen.
 */
final class QuakeSound implements SoundEngine.Synth {
    static final int VOICES = 6;
    private final Dsp.Rand rnd = new Dsp.Rand(41);
    private final Spot[] spot = new Spot[VOICES];
    private final Dsp.Brown[] brown = new Dsp.Brown[VOICES];
    private final Dsp.Biquad[] deep = new Dsp.Biquad[VOICES], mid = new Dsp.Biquad[VOICES], rock = new Dsp.Biquad[VOICES];
    private final double[] tP = new double[VOICES], tS = new double[VOICES], dur = new double[VOICES], amp = new double[VOICES], pan = new double[VOICES],
            boom = new double[VOICES], rumble = new double[VOICES], rattle = new double[VOICES];
    private final boolean[] near = new boolean[VOICES], live = new boolean[VOICES];
    private double now;
    /** Wie viele Beben in den letzten Sekunden ankamen (für das Dauergrollen). */
    private double rateEnv;
    private final Spot hum = new Spot();
    private final Dsp.Brown humB = new Dsp.Brown(9);
    private final Dsp.Biquad humF = new Dsp.Biquad().set(Dsp.Biquad.LP, 50, 0.7);

    QuakeSound() {
        for (int i = 0; i < VOICES; i++) {
            spot[i] = new Spot(); brown[i] = new Dsp.Brown(500 + i);
            deep[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 55, 0.8);
            mid[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 190, 0.7);
            rock[i] = new Dsp.Biquad().set(Dsp.Biquad.HP, 1400, 0.7);
            tP[i] = -1;
        }
    }

    /**
     * Schalldruck (dB) aus dem Ausschlag am Hörer (mm Wood-Anderson, ungekappt) [G]: M 1 in 3 km gibt rund
     * 0,7 mm und ist an der Grenze des Hörbaren, M 2,4 in 1 km rund 50 mm (deutliches Grollen), die
     * Einsturzbeben von 2018 (M 5,3) in 1 km rund 50 000 mm (lauter Knall) [G nach Berichten].
     */
    static double spl(double aMm) { return Math.min(112, 40 + 15 * Math.log10(Math.max(aMm, 1e-6))); }

    @Override public void event(Scene s, double[] e) {
        if ((int) e[0] != SoundEngine.EV_QUAKE) return;
        double x = e[1], y = e[2], z = e[3], m = e[4];
        double rKm = Math.sqrt((x - s.x) * (x - s.x) + (y - s.y) * (y - s.y) + (z - s.z) * (z - s.z)) / 1000;
        double a = Seismo.amplitudeMm(m, Math.max(rKm, 0.5));
        rateEnv += 1;
        if (spl(a) < 33) return;
        int best = 0;
        for (int i = 0; i < VOICES; i++) {
            if (!live[i]) { best = i; break; }
            if (amp[i] < amp[best]) best = i;
        }
        if (live[best] && amp[best] > a) return;
        Ear.Where w = new Ear.Where().at(s, x, s.y, z);
        tP[best] = now + rKm * 1000 / Seismo.VP;
        tS[best] = now + rKm * 1000 / Seismo.VS;
        dur[best] = Dsp.clamp(Seismo.durationS(m, rKm) * 0.25, 1.5, 12);
        amp[best] = a;
        pan[best] = w.pan * 0.6;
        near[best] = rKm < 8 && -y < 4000;
        boom[best] = rumble[best] = rattle[best] = 0;
        live[best] = true;
    }

    @Override public void idle(Scene s, int n) { now += n / (double) Dsp.RATE; rateEnv = 0; }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        now = t;
        double dt = n / (double) Dsp.RATE;
        for (int k = 0; k < VOICES; k++) {
            if (!live[k]) continue;
            double l = Ear.level(spl(amp[k]));
            spot[k].aimDirect(l, pan[k], 2500, n);
            float bd = (float) Math.exp(-1 / (0.45 * Dsp.RATE)), rd = (float) Math.exp(-1 / (dur[k] * 0.45 * Dsp.RATE)), kd = (float) Math.exp(-1 / (0.9 * Dsp.RATE));
            for (int i = 0; i < n; i++) {
                double tt = t + i / (double) Dsp.RATE;
                if (tP[k] >= 0 && tt >= tP[k]) { if (near[k]) boom[k] = 1; tP[k] = -1; }
                if (tS[k] >= 0 && tt >= tS[k]) { rumble[k] = 1; if (amp[k] > 0.5) rattle[k] = 1; tS[k] = -1; }
                boom[k] *= bd; rumble[k] *= rd; rattle[k] *= kd;
                float b = brown[k].next();
                float o = deep[k].process(b) * (float) (boom[k] * 2.2 + rumble[k] * 2.6) + mid[k].process(b) * (float) (boom[k] * 0.9 + rumble[k] * 0.5)
                        + (rnd.unit() < 0.02 ? rock[k].process(rnd.next() * 3) : rock[k].process(0)) * (float) rattle[k];
                spot[k].mix(o * 0.46f, L, R, i);   // geeicht
            }
            if (tP[k] < 0 && tS[k] < 0 && boom[k] < 1e-3 && rumble[k] < 1e-3 && rattle[k] < 1e-3) live[k] = false;
        }
        // viele Beben je Sekunde (Zeitraffer, Gang): tiefes Dauergrollen nach der Rate [G]
        rateEnv *= Math.exp(-dt / 2.0);
        double rate = rateEnv / 2.0;
        if (rate > 0.8 || hum.audible()) {
            hum.aimDirect(rate > 0.8 ? Ear.level(48 + 8 * Math.log10(rate)) : 0, 0, 400, n);
            for (int i = 0; i < n; i++) hum.mix(humF.process(humB.next()) * 2.5f, L, R, i);
        }
    }
}
