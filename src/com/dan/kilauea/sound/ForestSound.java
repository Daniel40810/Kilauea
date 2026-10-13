package com.dan.kilauea.sound;

import com.dan.kilauea.life.Birds;
import com.dan.kilauea.life.Life;

/**
 * Regenwald: Vogelgesang, nachts die Coquí-Frösche, in der Lavaröhre Tropfen.
 * <ul>
 * <li>Gesang aus den Rufen der Vögel im Bild ({@link Life}) und, wo Wald ist, aus der Umgebung. Muster je
 * Art [G nach Beschreibungen]: ʻapapane vielgestaltig aus Pfiffen, Schnarren und Tschilpen (die Art hat
 * „eine erstaunliche Vielfalt an Liedern und Rufen“ [L NPS]); ʻamakihi ein schneller Triller; ʻōmaʻo
 * flötende, gezogene Töne; Japanbrillenvogel ein hohes, rasches Zwitschern; ʻio ein zweisilbiger Schrei;
 * Koaʻe kea ein raues Keckern. Am Morgen am regsten.</li>
 * <li>Coquí (Eleutherodactylus coqui, eingeschleppt, breitet sich hangaufwärts zum Kīlauea aus [L NPS]):
 * nachts im feuchten Wald, bis rund 1 100 m dicht, darüber bis 1 250 m auslaufend; Ruf „ko-kí“ aus einem tieferen und einem höheren,
 * steigenden Ton (rund 1,1–1,2 kHz und 2 kHz) [L Größenordnung]; ein Chor erreicht rund 70 dB [L CTAHR].</li>
 * <li>In Nāhuku tropft Wasser von der Decke [L NPS „Tropfwasser“ im Werkbuch; Klang G].</li>
 * </ul>
 */
final class ForestSound implements SoundEngine.Synth {
    static final int SONGS = 14, FROGS = 16;
    private final Dsp.Rand rnd = new Dsp.Rand(23);
    private final Tone[] song = new Tone[SONGS];
    private final Spot[] songSpot = new Spot[SONGS];
    private final double[] songX = new double[SONGS], songY = new double[SONGS], songZ = new double[SONGS], songSpl = new double[SONGS];
    private final Tone[] frog = new Tone[FROGS];
    private final Spot[] frogSpot = new Spot[FROGS];
    private final double[] frogX = new double[FROGS], frogY = new double[FROGS], frogZ = new double[FROGS], frogNext = new double[FROGS], frogPitch = new double[FROGS];
    private double anchorX = Double.NaN, anchorZ;
    private final Spot drip = new Spot();
    private final Dsp.Biquad dripF = new Dsp.Biquad();
    private double dripEnv, dripPh, dripFreq;
    private boolean dripLive;
    private double now;

    ForestSound() {
        for (int i = 0; i < SONGS; i++) { song[i] = new Tone(); songSpot[i] = new Spot(); }
        for (int i = 0; i < FROGS; i++) { frog[i] = new Tone(); frogSpot[i] = new Spot(); frogPitch[i] = 0.95 + 0.1 * rnd.unit(); }
    }

    // ------------------------------------------------------------------ Lieder

    /** Ein Lied der Art (Ruf-Art aus {@link Life}) in t, Schalldruck in 1 m. */
    double compose(Tone t, int kind) {
        t.clear();
        t.h2 = 0.18; t.h3 = 0.06;
        switch (kind) {
            case Life.C_APAPANE -> {
                int m = 3 + (int) (rnd.unit() * 5);
                for (int i = 0; i < m; i++) {
                    double u = rnd.unit();
                    if (u < 0.5) { double f = 2500 + 3000 * rnd.unit(); t.note(0.05 + 0.07 * rnd.unit(), f, f + (rnd.next() * 900), 0.9, 0, 0.03 + 0.05 * rnd.unit()); }
                    else if (u < 0.75) { double f = 3000 + 900 * rnd.unit(); t.note(0.10 + 0.10 * rnd.unit(), f, f * 0.97, 0.7, 110, 0.05); }
                    else { double f = 5000 + 1500 * rnd.unit(); t.note(0.02, f, f * 1.05, 0.8, 0, 0.04 + 0.04 * rnd.unit()); }
                }
                return 80;
            }
            case Life.C_AMAKIHI -> {
                int m = 12 + (int) (rnd.unit() * 12);
                double f = 4000 + 600 * rnd.unit();
                for (int i = 0; i < m; i++) { t.note(0.035, f * 1.02, f * 0.96, 0.85, 0, 0.025); f *= 0.99; }
                return 76;
            }
            case Life.C_OMAO -> {
                t.h2 = 0.32; t.h3 = 0.1;
                int m = 3 + (int) (rnd.unit() * 4);
                for (int i = 0; i < m; i++) { double f = 1500 + 2000 * rnd.unit(); t.note(0.15 + 0.15 * rnd.unit(), f, f * (0.8 + 0.4 * rnd.unit()), 0.9, 0, 0.05 + 0.06 * rnd.unit()); }
                return 82;
            }
            case Life.C_MEJIRO -> {
                int m = 15 + (int) (rnd.unit() * 15);
                for (int i = 0; i < m; i++) { double f = 3000 + 3500 * rnd.unit(); t.note(0.03 + 0.03 * rnd.unit(), f, f * (0.9 + 0.2 * rnd.unit()), 0.7, 0, 0.02 * rnd.unit()); }
                return 74;
            }
            case Life.C_IO -> {
                t.h2 = 0.5; t.h3 = 0.3;
                t.note(0.25, 2600, 2900, 1, 0, 0.06).note(0.35, 2400, 1900, 0.9, 0, 0);
                return 92;
            }
            case Life.C_TROPIC -> {
                int m = 4 + (int) (rnd.unit() * 5);
                for (int i = 0; i < m; i++) t.note(0.04, 2100, 1800, 0.9, 180, 0.06);
                return 84;
            }
            default -> { return 0; }
        }
    }

    private void sing(Scene s, int kind, double x, double y, double z) {
        int best = -1;
        double far = -1;
        for (int i = 0; i < SONGS; i++) {
            if (!song[i].active) { best = i; break; }
            double d = Math.hypot(songX[i] - s.x, songZ[i] - s.z);
            if (d > far) { far = d; best = i; }
        }
        double d = Math.sqrt((x - s.x) * (x - s.x) + (y - s.y) * (y - s.y) + (z - s.z) * (z - s.z));
        if (song[best].active && d > far) return;
        double spl = compose(song[best], kind);
        if (spl <= 0) return;
        songX[best] = x; songY[best] = y; songZ[best] = z; songSpl[best] = spl;
        song[best].play(now + d / Ear.C);
    }

    @Override public void event(Scene s, double[] e) {
        if ((int) e[0] != SoundEngine.EV_CALL) return;
        int kind = (int) e[1];
        if (kind < Life.C_APAPANE) return;   // Nēnē rufen bei „Tiere und Verkehr“
        double d = Math.hypot(e[2] - s.x, e[4] - s.z);
        if (d > (kind == Life.C_IO ? 1500 : 400)) return;
        sing(s, kind, e[2], e[3], e[4]);
    }

    @Override public void idle(Scene s, int n) { now += n / (double) Dsp.RATE; }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        now = t;
        double dt = n / (double) Dsp.RATE;
        boolean outside = !s.underwater && !s.inTube;
        // Gesang aus der Umgebung, wo Wald ist
        double act = Birds.activity(s.sunElevation, s.hour);
        if (outside && s.aboveGround < 300 && s.trees > 0.25 && rnd.unit() < dt * 1.1 * act * s.trees) {
            double u = rnd.unit();
            int kind = s.elevation > 900 ? (u < 0.5 ? Life.C_APAPANE : u < 0.75 ? Life.C_AMAKIHI : u < 0.85 && s.rainYear > 2000 ? Life.C_OMAO : Life.C_MEJIRO)
                    : (u < 0.1 ? Life.C_APAPANE : u < 0.4 ? Life.C_AMAKIHI : Life.C_MEJIRO);
            double a = rnd.unit() * 2 * Math.PI, r = 25 + 140 * rnd.unit();
            double ground = s.y - s.aboveGround;
            sing(s, kind, s.x + Math.cos(a) * r, ground + 8 + 8 * rnd.unit(), s.z + Math.sin(a) * r);
        }
        for (int k = 0; k < SONGS; k++) {
            Tone tn = song[k];
            if (!tn.active && !songSpot[k].audible()) continue;
            if (tn.active) songSpot[k].aim(s, songX[k], songY[k], songZ[k], songSpl[k], 1, n); else songSpot[k].silence(n);
            for (int i = 0; i < n; i++) songSpot[k].mix(tn.next(t + i / (double) Dsp.RATE) * 4.4f, L, R, i);   // geeicht
        }
        // Coquí: nachts im feuchten Wald bis rund 1 100 m, darüber auslaufend [L Vorkommen, Grenze G]
        double hab = 0;
        if (outside && s.sunElevation < -4 && s.aboveGround < 80) {
            double el = s.elevation < 1100 ? 1 : Math.max(0, 1 - (s.elevation - 1100) / 150);
            double wet = Dsp.clamp((s.rainYear - 1500) / 1500, 0, 1);
            double cover = Dsp.clamp(Math.max(s.trees, s.veg * 0.6) * 1.5, 0, 1);
            hab = el * wet * cover;
        }
        int frogs = (int) Math.round(FROGS * hab);
        if (frogs > 0 && (Double.isNaN(anchorX) || Math.hypot(s.x - anchorX, s.z - anchorZ) > 40)) {
            anchorX = s.x; anchorZ = s.z;
            double ground = s.y - s.aboveGround;
            for (int k = 0; k < FROGS; k++) {
                double a = rnd.unit() * 2 * Math.PI, r = 4 + 46 * Math.sqrt(rnd.unit());
                frogX[k] = s.x + Math.cos(a) * r; frogZ[k] = s.z + Math.sin(a) * r;
                frogY[k] = ground + 0.5 + 3 * rnd.unit();
                frogNext[k] = t + 3 * rnd.unit();
            }
        }
        for (int k = 0; k < FROGS; k++) {
            Tone tn = frog[k];
            boolean on = k < frogs;
            if (on && !tn.active && t >= frogNext[k]) {
                double p = frogPitch[k];
                tn.clear();
                tn.h2 = 0.12; tn.h3 = 0.03;
                tn.note(0.10, 1200 * p, 1080 * p, 0.85, 0, 0.035).note(0.16, 1950 * p, 2250 * p, 1.0, 0, 0);
                tn.play(t);
                frogNext[k] = t + 1.2 + 2.8 * rnd.unit();
            }
            if (!tn.active && !frogSpot[k].audible()) continue;
            if (on) frogSpot[k].aim(s, frogX[k], frogY[k], frogZ[k], 95, 0.5, n); else frogSpot[k].silence(n);
            for (int i = 0; i < n; i++) frogSpot[k].mix(tn.next(t + i / (double) Dsp.RATE) * 2.5f, L, R, i);
        }
        // Tropfen in Nāhuku
        if (s.inTube || dripLive) {
            if (s.inTube) drip.aimDirect(Ear.level(50), rnd.next() * 0.6, 9000, n); else drip.silence(n);
            dripLive = s.inTube;
            for (int i = 0; i < n; i++) {
                if (s.inTube && rnd.unit() < 0.8 / Dsp.RATE) { dripEnv = 0.6 + 0.4 * rnd.unit(); dripFreq = 1600 + 1600 * rnd.unit(); }
                dripEnv *= 0.99955;
                dripFreq *= 1.000012;
                dripPh += 2 * Math.PI * dripFreq / Dsp.RATE;
                drip.mix((float) (Math.sin(dripPh) * dripEnv * 2), L, R, i);
            }
        }
    }
}
