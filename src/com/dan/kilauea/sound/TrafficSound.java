package com.dan.kilauea.sound;

import com.dan.kilauea.life.Helicopters;
import com.dan.kilauea.life.Life;

/**
 * Tiere und Verkehr:
 * <ul>
 * <li>Hubschrauber: Blattschlag mit der Blattfolge (3 Blätter × 390 U/min ≈ 19,5 Hz), Heckrotor-Ton
 * (2 Blätter × rund 2 090 U/min ≈ 70 Hz), breites Rauschen und das Pfeifen der Turbine; Doppler nach der
 * Geschwindigkeit zum Hörer (c / (c − v)) [L Physik, Drehzahlen Größenordnung]. Rund 88 dB in 150 m [G].</li>
 * <li>Autos: Abrollgeräusch und Motor, lauter mit dem Tempo (+30 dB je Verzehnfachung), Doppler [G].</li>
 * <li>Nēnē: leise, nasale Rufe „nē“, oft doppelt; im Flug häufiger und lauter (der Name kommt vom Ruf [L]).</li>
 * <li>Eigene Schritte beim Gehen nach dem Untergrund: Pāhoehoe hart, ʻaʻā und Schlacke knirschend, Wege
 * dumpf, Asche weich knirschend, in der Röhre mit Hall [G].</li>
 * </ul>
 */
public final class TrafficSound implements SoundEngine.Synth {
    static final int HELI = 3, CARS = 6, CALLS = 6;
    private final Dsp.Rand rnd = new Dsp.Rand(61);
    // Hubschrauber
    private final Spot[] hSpot = new Spot[HELI];
    private final Dsp.Pink[] hPink = new Dsp.Pink[HELI];
    private final Dsp.Biquad[] hBroad = new Dsp.Biquad[HELI], hSlap = new Dsp.Biquad[HELI], hTail = new Dsp.Biquad[HELI];
    private final double[] hPh = new double[HELI], hTailPh = new double[HELI], hWhine = new double[HELI], hPulse = new double[HELI];
    private final boolean[] hLive = new boolean[HELI];
    // Autos
    private final Spot[] cSpot = new Spot[CARS];
    private final Dsp.Pink[] cPink = new Dsp.Pink[CARS];
    private final Dsp.Biquad[] cTire = new Dsp.Biquad[CARS], cEng = new Dsp.Biquad[CARS];
    private final double[] cPh = new double[CARS];
    private final boolean[] cLive = new boolean[CARS];
    // Rufe der Nēnē
    private final Tone[] call = new Tone[CALLS];
    private final Spot[] callSpot = new Spot[CALLS];
    private final Dsp.Biquad[] f1 = new Dsp.Biquad[CALLS], f2 = new Dsp.Biquad[CALLS];
    private final double[] callX = new double[CALLS], callY = new double[CALLS], callZ = new double[CALLS], callSpl = new double[CALLS];
    // Schritte
    private final Spot step = new Spot();
    private final Dsp.Biquad stepF = new Dsp.Biquad(), stepLo = new Dsp.Biquad().set(Dsp.Biquad.LP, 300, 0.7);
    private double stepT, stepEnv, stepGrain;
    private int stepSide;
    private double now;
    private final int[] order = new int[96];
    private final double[] dist = new double[96];
    private final int[] heli = new int[HELI], car = new int[CARS];

    TrafficSound() {
        for (int i = 0; i < HELI; i++) {
            hSpot[i] = new Spot(); hPink[i] = new Dsp.Pink(700 + i);
            hBroad[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 1100, 0.7);
            hSlap[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 260, 1.2);
            hTail[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 900, 0.7);
        }
        for (int i = 0; i < CARS; i++) {
            cSpot[i] = new Spot(); cPink[i] = new Dsp.Pink(800 + i);
            cTire[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 850, 0.5);
            cEng[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 260, 0.7);
        }
        for (int i = 0; i < CALLS; i++) {
            call[i] = new Tone(); callSpot[i] = new Spot();
            f1[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 850, 4);
            f2[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 2300, 5);
        }
    }

    /** Doppler-Faktor für eine Quelle mit Geschwindigkeit v, die in Richtung (dx, dy, dz) vom Hörer aus steht. */
    public static double doppler(double vx, double vy, double vz, double dx, double dy, double dz) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
        double toward = -(vx * dx + vy * dy + vz * dz) / d;
        return Dsp.clamp(Ear.C / (Ear.C - Dsp.clamp(toward, -150, 150)), 0.6, 1.6);
    }

    @Override public void event(Scene s, double[] e) {
        if ((int) e[0] != SoundEngine.EV_CALL) return;
        int kind = (int) e[1];
        if (kind != Life.C_NENE && kind != Life.C_NENE_FLIGHT) return;
        double d = Math.sqrt((e[2] - s.x) * (e[2] - s.x) + (e[3] - s.y) * (e[3] - s.y) + (e[4] - s.z) * (e[4] - s.z));
        if (d > 350) return;
        int best = -1;
        for (int i = 0; i < CALLS; i++) if (!call[i].active) { best = i; break; }
        if (best < 0) return;
        Tone t = call[best].clear();
        t.h2 = 0.75; t.h3 = 0.55;   // nasal, obertonreich
        double f = (kind == Life.C_NENE_FLIGHT ? 470 : 420) * (0.9 + 0.2 * rnd.unit());
        t.note(0.18 + 0.08 * rnd.unit(), f, f * 0.93, 1, 0, 0.11);
        if (rnd.unit() < 0.6) t.note(0.16, f * 0.92, f * 0.86, 0.85, 0, 0);
        callX[best] = e[2]; callY[best] = e[3]; callZ[best] = e[4];
        callSpl[best] = kind == Life.C_NENE_FLIGHT ? 80 : 72;
        t.play(now + d / Ear.C);
    }

    @Override public void idle(Scene s, int n) { now += n / (double) Dsp.RATE; }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        now = t;
        // Quellen nach Art und Entfernung ordnen
        int nh = pick(s, Life.E_HELI, 60000), nc;
        for (int i = 0; i < Math.min(HELI, nh); i++) heli[i] = order[i];
        for (int k = 0; k < HELI; k++) {
            boolean on = k < nh;
            if (!on && !hLive[k]) continue;
            double dop = 1;
            if (on) {
                int q = 8 * heli[k];
                float[] a = s.sources;
                double x = a[q + 1], y = a[q + 2], z = a[q + 3];
                dop = doppler(a[q + 4], a[q + 5], a[q + 6], x - s.x, y - s.y, z - s.z);
                hSpot[k].aim(s, x, y, z, 88, 150, n);
            } else hSpot[k].silence(n);
            hLive[k] = on;
            double fb = 3 * Helicopters.ROTOR_RPM / 60 * dop, ft = 2 * Helicopters.ROTOR_RPM * Helicopters.TAIL_RATIO / 60 * dop;
            float pd = (float) Math.exp(-1 / (0.006 * Dsp.RATE));
            for (int i = 0; i < n; i++) {
                hPh[k] += fb / Dsp.RATE;
                if (hPh[k] >= 1) { hPh[k] -= 1; hPulse[k] = 1; }
                hPulse[k] *= pd;
                hTailPh[k] += ft / Dsp.RATE;
                if (hTailPh[k] >= 1) hTailPh[k] -= 1;
                hWhine[k] += 2 * Math.PI * 4200 * dop / Dsp.RATE;
                float noise = hPink[k].next();
                float slap = hSlap[k].process((float) (rnd.next() * hPulse[k] * 3 + hPulse[k] * 2));
                float o = slap * 1.6f + hBroad[k].process(noise) * (float) (0.45 * (0.8 + 0.4 * hPulse[k]))
                        + hTail[k].process((float) (2 * hTailPh[k] - 1)) * 0.22f + (float) (Math.sin(hWhine[k]) * 0.02);
                hSpot[k].mix(o * 3.2f, L, R, i);   // geeicht: 88 dB in 150 m
            }
        }
        nc = pick(s, Life.E_CAR, 700);
        for (int i = 0; i < Math.min(CARS, nc); i++) car[i] = order[i];
        for (int k = 0; k < CARS; k++) {
            boolean on = k < nc;
            if (!on && !cLive[k]) continue;
            double dop = 1, v = 0;
            if (on) {
                int q = 8 * car[k];
                float[] a = s.sources;
                double x = a[q + 1], y = a[q + 2], z = a[q + 3];
                v = a[q + 7];
                dop = doppler(a[q + 4], a[q + 5], a[q + 6], x - s.x, y - s.y, z - s.z);
                cSpot[k].aim(s, x, y, z, 68 + 30 * Math.log10(Math.max(v, 3) / 14), 7.5, n);
                cTire[k].set(Dsp.Biquad.BP, 850 * dop, 0.5);
            } else cSpot[k].silence(n);
            cLive[k] = on;
            double fe = (18 + v * 2.0) * dop;
            for (int i = 0; i < n; i++) {
                cPh[k] += fe / Dsp.RATE;
                if (cPh[k] >= 1) cPh[k] -= 1;
                float o = cTire[k].process(cPink[k].next()) * 1.6f + cEng[k].process((float) (2 * cPh[k] - 1)) * 0.35f;
                cSpot[k].mix(o * 3.7f, L, R, i);   // geeicht: 68 dB in 7,5 m
            }
        }
        // Nēnē
        for (int k = 0; k < CALLS; k++) {
            Tone tn = call[k];
            if (!tn.active && !callSpot[k].audible()) continue;
            if (tn.active) callSpot[k].aim(s, callX[k], callY[k], callZ[k], callSpl[k], 1, n); else callSpot[k].silence(n);
            for (int i = 0; i < n; i++) {
                float v = tn.next(t + i / (double) Dsp.RATE);
                callSpot[k].mix((f1[k].process(v) * 1.6f + f2[k].process(v) * 0.9f + v * 0.25f) * 1.6f, L, R, i);
            }
        }
        // Schritte
        boolean walk = s.walking && s.walkSpeed > 0.3 && !s.underwater;
        if (walk || step.audible()) {
            double spl = switch (s.surface) { case 1 -> 56; case 2 -> 48; case 3 -> 52; case 4 -> 50; default -> 53; };
            step.aimDirect(walk ? Ear.level(spl) : 0, stepSide == 0 ? -0.12 : 0.12, 12000, n);
            double interval = 0.75 / Math.max(0.3, s.walkSpeed);
            for (int i = 0; i < n; i++) {
                stepT += 1.0 / Dsp.RATE;
                if (walk && stepT > interval) {
                    stepT = 0;
                    stepSide ^= 1;
                    stepEnv = 1;
                    stepGrain = s.surface == 1 || s.surface == 4 ? 1 : 0;
                    double fc = switch (s.surface) { case 0 -> 2200; case 1 -> 3000; case 3 -> 1800; case 4 -> 2400; default -> 700; };
                    stepF.set(Dsp.Biquad.BP, fc * (0.85 + 0.3 * rnd.unit()), 1.2);
                }
                double dur = s.surface == 1 || s.surface == 4 ? 0.09 : 0.035;
                stepEnv *= Math.exp(-1 / (dur * Dsp.RATE));
                float x = rnd.next();
                float grain = stepGrain > 0 && rnd.unit() < 0.25 ? x * 2.5f : x * 0.6f;
                float o = stepF.process(grain * (float) stepEnv) * 1.8f + stepLo.process(x * (float) stepEnv) * (s.surface == 2 ? 2.4f : 0.9f);
                step.mix(o * 16f, L, R, i);   // kurze Stöße: nach der Spitze geeicht
            }
        }
    }

    /** Quellen einer Art nach Entfernung ordnen (bis maxDist); Zahl der gefundenen. */
    private int pick(Scene s, int type, double maxDist) {
        int m = 0;
        for (int i = 0; i < s.sourceCount; i++) {
            float[] a = s.sources;
            if ((int) a[8 * i] != type) continue;
            double d = Math.sqrt(Math.pow(a[8 * i + 1] - s.x, 2) + Math.pow(a[8 * i + 2] - s.y, 2) + Math.pow(a[8 * i + 3] - s.z, 2));
            if (d > maxDist) continue;
            int j = m++;
            while (j > 0 && dist[j - 1] > d) { order[j] = order[j - 1]; dist[j] = dist[j - 1]; j--; }
            order[j] = i;
            dist[j] = d;
        }
        return m;
    }
}
