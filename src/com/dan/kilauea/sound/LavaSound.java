package com.dan.kilauea.sound;

/**
 * Lava und Dampf:
 * <ul>
 * <li>Strom: die abkühlende Glaskruste knistert und klirrt (kurze, helle Töne), um so dichter, je mehr
 * heiße Fläche in der Nähe ist; darunter leises Rumpeln [G nach Beschreibungen „wie zerbrechendes Glas“].</li>
 * <li>Lavasee: Schwappen, Spritzer an den Nähten, gelegentlich ein Gasstoß [G].</li>
 * <li>Meereseintritt: Zischen des Dampfes, Dampfexplosionen als Knall mit Nachprasseln (mit Laufzeit
 * 343 m/s) [G nach Mattox & Mangan 1997 zur Art der Explosionen].</li>
 * <li>Dampfspalten am Gipfel: leises Fauchen [G].</li>
 * <li>Unter Wasser an der Kissenlava: Knacken und Blubbern [G nach Tribble 1991].</li>
 * </ul>
 */
final class LavaSound implements SoundEngine.Synth {
    static final int HOT = 4, ENTRY = 4, BOOMS = 4;
    private final Dsp.Rand rnd = new Dsp.Rand(77);
    // Strom
    private final Spot[] hotSpot = new Spot[HOT];
    private final Dsp.Biquad[] tink = new Dsp.Biquad[HOT], rumbleF = new Dsp.Biquad[HOT];
    private final Dsp.Brown[] rumble = new Dsp.Brown[HOT];
    private final double[] hotRate = new double[HOT];
    private final boolean[] hotLive = new boolean[HOT];
    // See
    private final Spot lakeSpot = new Spot();
    private final Dsp.Brown lakeB = new Dsp.Brown(5);
    private final Dsp.Pink lakeP = new Dsp.Pink(6);
    private final Dsp.Biquad lakeLo = new Dsp.Biquad().set(Dsp.Biquad.LP, 280, 0.7), lakeSp = new Dsp.Biquad().set(Dsp.Biquad.BP, 900, 1.2);
    private double lakeEnv, lakeLfo, lakeGas;
    private boolean lakeLive;
    // Eintritt
    private final Spot[] entrySpot = new Spot[ENTRY];
    private final Dsp.Biquad[] hissF = new Dsp.Biquad[ENTRY];
    private final double[] flutter = new double[ENTRY];
    private final boolean[] entryLive = new boolean[ENTRY];
    // Explosionen
    private final Spot[] boomSpot = new Spot[BOOMS];
    private final Dsp.Brown[] boomB = new Dsp.Brown[BOOMS];
    private final Dsp.Biquad[] boomF = new Dsp.Biquad[BOOMS];
    private final double[] boomStart = new double[BOOMS], boomEnv = new double[BOOMS], boomX = new double[BOOMS], boomZ = new double[BOOMS], boomS = new double[BOOMS];
    // Dampfspalte
    private final Spot ventSpot = new Spot();
    private final Dsp.Pink ventP = new Dsp.Pink(9);
    private final Dsp.Biquad ventF = new Dsp.Biquad().set(Dsp.Biquad.HP, 700, 0.7);
    private double ventLfo;
    private boolean ventLive;
    // Kissen
    private final Spot pillowSpot = new Spot();
    private final Dsp.Biquad pillowF = new Dsp.Biquad();
    private double bubble, bubbleF, bubblePh;
    private boolean pillowLive;
    private double now;

    LavaSound() {
        for (int i = 0; i < HOT; i++) {
            hotSpot[i] = new Spot(); tink[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 4000, 9); rumble[i] = new Dsp.Brown(40 + i);
            rumbleF[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 110, 0.7);
        }
        for (int i = 0; i < ENTRY; i++) { entrySpot[i] = new Spot(); hissF[i] = new Dsp.Biquad().set(Dsp.Biquad.HP, 1800, 0.6); }
        for (int i = 0; i < BOOMS; i++) { boomSpot[i] = new Spot(); boomB[i] = new Dsp.Brown(60 + i); boomF[i] = new Dsp.Biquad().set(Dsp.Biquad.LP, 170, 0.8); boomStart[i] = -1; }
    }

    @Override public void event(Scene s, double[] e) {
        if ((int) e[0] != SoundEngine.EV_SURGE) return;
        int best = 0;
        for (int i = 1; i < BOOMS; i++) if (boomEnv[i] < boomEnv[best] && boomStart[i] < 0) best = i;
        boomX[best] = e[1]; boomZ[best] = e[2]; boomS[best] = Math.max(0.3, e[3]);
        double d = Math.hypot(e[1] - s.x, e[2] - s.z);
        boomStart[best] = now + d / Ear.C;
    }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        now = t;
        // --- heiße Lava: die vier nächsten Stellen
        for (int k = 0; k < HOT; k++) {
            boolean on = k < s.hotCount;
            if (!on && !hotLive[k]) continue;
            double area = 0;
            if (on) {
                double x = s.hot[3 * k], z = s.hot[3 * k + 1];
                area = s.hot[3 * k + 2];
                double y = com.dan.kilauea.terrain.Ground.grid() != null ? com.dan.kilauea.terrain.Ground.height(x, z) + 1 : 0;
                hotSpot[k].aim(s, x, y, z, 64 + 10 * Math.log10(Math.max(area, 50) / 1000), 10, n);
            } else hotSpot[k].silence(n);
            hotLive[k] = on;
            double rate = Math.min(400, Math.max(4, area / 1000 * 30)) / Dsp.RATE;
            float decay = (float) Math.exp(-1 / (0.0015 * Dsp.RATE));
            for (int i = 0; i < n; i++) {
                float exc = 0;
                if (rnd.unit() < rate) {
                    exc = rnd.exp() * 0.8f;
                    tink[k].set(Dsp.Biquad.BP, 1800 + 5500 * rnd.unit(), 7 + 6 * rnd.unit());
                }
                hotRate[k] = hotRate[k] * decay + exc;
                float o = (tink[k].process((float) (rnd.next() * hotRate[k])) * 2.2f + rumbleF[k].process(rumble[k].next()) * 0.25f) * 3.6f;   // geeicht
                hotSpot[k].mix(o, L, R, i);
            }
        }
        // --- Lavasee
        if (s.lake || lakeLive) {
            if (s.lake) lakeSpot.aim(s, s.lakeX, s.lakeY, s.lakeZ, 86, 50, n); else lakeSpot.silence(n);
            lakeLive = s.lake;
            float sd = (float) Math.exp(-1 / (0.15 * Dsp.RATE));
            for (int i = 0; i < n; i++) {
                lakeLfo += 2 * Math.PI * 0.17 / Dsp.RATE;
                if (rnd.unit() < 0.9 / Dsp.RATE) lakeEnv += 0.6 + rnd.exp() * 0.5;
                if (rnd.unit() < 0.04 / Dsp.RATE) lakeGas = 1.5;
                lakeEnv *= sd;
                lakeGas *= 0.99995;
                float o = lakeLo.process(lakeB.next()) * (float) (0.55 + 0.25 * Math.sin(lakeLfo))
                        + lakeSp.process(lakeP.next()) * (float) lakeEnv * 1.6f
                        + (float) (Math.sin(lakeLfo * 300) * lakeGas * 0.3);
                lakeSpot.mix(o * 1.6f, L, R, i);
            }
        }
        // --- Meereseintritt: Zischen
        for (int k = 0; k < ENTRY; k++) {
            boolean on = k < s.entryCount;
            if (!on && !entryLive[k]) continue;
            if (on) {
                double mw = s.entries[3 * k + 2];   // Wärmestrom ins Meer (MW)
                entrySpot[k].aim(s, s.entries[3 * k], 3, s.entries[3 * k + 1], 84 + 10 * Math.log10(Math.max(5, mw) / 100), 50, n);
            } else entrySpot[k].silence(n);
            entryLive[k] = on;
            for (int i = 0; i < n; i++) {
                flutter[k] += 2 * Math.PI * (7 + 4 * rnd.unit()) / Dsp.RATE;
                float o = hissF[k].process(rnd.next()) * (float) (0.7 + 0.3 * Math.sin(flutter[k]));
                entrySpot[k].mix(o * 3.6f, L, R, i);
            }
        }
        // --- Dampfexplosionen
        for (int k = 0; k < BOOMS; k++) {
            if (boomStart[k] >= 0 && t + n / (double) Dsp.RATE >= boomStart[k]) { boomEnv[k] = boomS[k]; boomStart[k] = -1; }
            if (boomEnv[k] < 1e-3) continue;
            boomSpot[k].aim(s, boomX[k], 5, boomZ[k], 112, 50, n);
            float d = (float) Math.exp(-1 / (1.1 * Dsp.RATE));
            for (int i = 0; i < n; i++) {
                boomEnv[k] *= d;
                float o = boomF[k].process(boomB[k].next()) * (float) boomEnv[k] * 1.4f + rnd.next() * (float) (boomEnv[k] * boomEnv[k]) * 0.3f;
                boomSpot[k].mix(o * 2.0f, L, R, i);
            }
        }
        // --- Dampfspalten
        boolean vent = s.ventDist < 250 && !s.underwater;
        if (vent || ventLive) {
            if (vent) ventSpot.aim(s, s.ventX, s.ventY, s.ventZ, 66, 5, n); else ventSpot.silence(n);
            ventLive = vent;
            for (int i = 0; i < n; i++) {
                ventLfo += 2 * Math.PI * 0.3 / Dsp.RATE;
                ventSpot.mix(ventF.process(ventP.next()) * (float) (0.8 + 0.2 * Math.sin(ventLfo)) * 3f, L, R, i);
            }
        }
        // --- unter Wasser an den Kissen: Knacken und Blasen, im Wasser hörbar (direkt)
        boolean pil = s.underwater && !Double.isNaN(s.pillowX);
        if (pil || pillowLive) {
            if (pil) {
                double d = Math.sqrt(Math.pow(s.pillowX - s.x, 2) + Math.pow(s.pillowY - s.y, 2) + Math.pow(s.pillowZ - s.z, 2));
                pillowSpot.aimDirect(Ear.level(Ear.spl(85, 5, d)), 0, 3500, n);
            } else pillowSpot.silence(n);
            pillowLive = pil;
            for (int i = 0; i < n; i++) {
                if (rnd.unit() < 25.0 / Dsp.RATE) pillowF.set(Dsp.Biquad.BP, 600 + 2400 * rnd.unit(), 6);
                if (rnd.unit() < 2.0 / Dsp.RATE) { bubble = 1; bubbleF = 300 + 500 * rnd.unit(); }
                bubble *= 0.9996;
                bubbleF *= 1.00003;
                float crack = rnd.unit() < 40.0 / Dsp.RATE ? rnd.next() * 3 : 0;
                bubblePh += 2 * Math.PI * bubbleF / Dsp.RATE;
                float o = pillowF.process(crack + rnd.next() * 0.02f) + (float) (Math.sin(bubblePh) * bubble * 0.4);
                pillowSpot.mix(o * 2f, L, R, i);
            }
        }
    }
}
