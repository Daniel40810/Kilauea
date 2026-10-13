package com.dan.kilauea.sound;

/**
 * Wind und Regen am Ohr:
 * <ul>
 * <li>Wind nach dem Passat der Szene, umgerechnet auf die Höhe über Grund mit dem logarithmischen
 * Windprofil (Rauigkeit 5 cm im Offenen, 1 m im Wald) [L]; Böen als langsamer Zufallsweg (rund 3 s) [G].
 * Ein tiefes Brausen, dessen Mitte mit dem Wind steigt, bei Sturm auf Graten ein Pfeifen; im Wald rauscht
 * das Laub [G]. Pegel rund 25 dB + 2,4 dB je m/s [G].</li>
 * <li>Regen: Rauschen und einzelne Tropfen, im Wald dazu Tropfen von den Blättern [G].</li>
 * <li>In der Röhre und unter Wasser kaum Wind.</li>
 * </ul>
 */
final class WindSound implements SoundEngine.Synth {
    private final Dsp.Pink pl = new Dsp.Pink(11), pr = new Dsp.Pink(12);
    private final Dsp.Rand rl = new Dsp.Rand(13), rr = new Dsp.Rand(14), rnd = new Dsp.Rand(15);
    private final Dsp.Biquad coreL = new Dsp.Biquad(), coreR = new Dsp.Biquad(), whL = new Dsp.Biquad(), whR = new Dsp.Biquad(),
            leafL = new Dsp.Biquad().set(Dsp.Biquad.HP, 2600, 0.6), leafR = new Dsp.Biquad().set(Dsp.Biquad.HP, 2600, 0.6),
            rainL = new Dsp.Biquad().set(Dsp.Biquad.HP, 3500, 0.6), rainR = new Dsp.Biquad().set(Dsp.Biquad.HP, 3500, 0.6),
            dropL = new Dsp.Biquad(), dropR = new Dsp.Biquad(), plop = new Dsp.Biquad();
    private double gust, gustNow = 1, coreF = -1;
    /** Eichung der Kette (Rauschen und Filter) auf den Pegel nach Ear.level [A]. */
    static final double TRIM = 3.6;
    private float gCore, gWhistle, gLeaf, gRain, gDrop;
    private double dropEnvL, dropEnvR, plopEnv;

    /** Wind am Ohr (m/s) aus dem Wind in 10 m Höhe, nach dem logarithmischen Profil. */
    static double atEar(double v10, double aboveGround, double trees) {
        double z0 = trees > 0.4 ? 1.0 : 0.05;
        double h = Math.max(1.7, Math.min(aboveGround, 3000));
        double f = Math.log((h + z0) / z0) / Math.log((10 + z0) / z0);
        return v10 * Math.max(0.25, Math.min(1.9, f));
    }

    /** Schalldruck des Windes (dB) [G]. */
    static double spl(double v) { return v < 0.3 ? 0 : 25 + 2.4 * v; }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        double dt = n / (double) Dsp.RATE;
        // Böen: Ornstein-Uhlenbeck mit rund 3 s Gedächtnis
        gust += -gust / 3.0 * dt + 0.55 * Math.sqrt(dt) * rnd.next() * 1.7;
        double target = Math.max(0.35, 1 + 0.35 * gust);
        gustNow += (target - gustNow) * Math.min(1, dt * 4);
        double damp = s.underwater ? 0 : s.inTube ? 0.08 : 1;
        double v = atEar(s.wind, s.aboveGround, s.trees) * gustNow;
        double f = 110 + 22 * v;
        if (coreF < 0 || Math.abs(f - coreF) > 4) { coreL.set(Dsp.Biquad.BP, f, 0.55); coreR.set(Dsp.Biquad.BP, f * 1.07, 0.55); coreF = f; }
        whL.set(Dsp.Biquad.BP, 380 + 28 * v, 9);
        whR.set(Dsp.Biquad.BP, 420 + 30 * v, 9);
        float tCore = (float) (Ear.level(spl(v)) * damp * 1.4 * TRIM);
        float tWh = (float) (v > 9 && s.aboveGround > 30 ? Ear.level(spl(v) - 8) * damp * Math.min(1, (v - 9) / 6) * TRIM : 0);
        float tLeaf = (float) (Ear.level(28 + 22 * Math.log10(1 + v)) * damp * Math.min(1, s.trees * 1.4 + s.veg * 0.25) * (s.aboveGround < 60 ? 1 : 0.2) * TRIM);
        double rain = s.underwater || s.inTube ? 0 : s.rain;
        float tRain = (float) (rain > 0.01 ? Ear.level(40 + 18 * rain) * 1.2 * TRIM : 0);
        float tDrop = (float) (rain > 0.01 ? Ear.level(46 + 14 * rain) * TRIM : 0);
        float dc = (tCore - gCore) / n, dw = (tWh - gWhistle) / n, dlf = (tLeaf - gLeaf) / n, dr = (tRain - gRain) / n, dd = (tDrop - gDrop) / n;
        double dropRate = rain * 260 / Dsp.RATE, plopRate = rain * s.trees * 30 / Dsp.RATE;
        float de = (float) Math.exp(-1 / (0.004 * Dsp.RATE)), pe = (float) Math.exp(-1 / (0.03 * Dsp.RATE));
        float leafMod = (float) Math.pow(gustNow, 1.6);
        for (int i = 0; i < n; i++) {
            gCore += dc; gWhistle += dw; gLeaf += dlf; gRain += dr; gDrop += dd;
            float a = pl.next(), b = pr.next();
            float l = coreL.process(a) * 2.4f * gCore + whL.process(a) * 3f * gWhistle;
            float r = coreR.process(b) * 2.4f * gCore + whR.process(b) * 3f * gWhistle;
            if (gLeaf > 1e-6) { l += leafL.process(rl.next()) * gLeaf * leafMod * 0.8f; r += leafR.process(rr.next()) * gLeaf * leafMod * 0.8f; }
            if (gRain > 1e-6 || gDrop > 1e-6) {
                l += rainL.process(rl.next()) * gRain * 0.5f;
                r += rainR.process(rr.next()) * gRain * 0.5f;
                if (rnd.unit() < dropRate) { dropEnvL = rnd.unit(); dropL.set(Dsp.Biquad.BP, 1500 + 4500 * rnd.unit(), 4); }
                if (rnd.unit() < dropRate) { dropEnvR = rnd.unit(); dropR.set(Dsp.Biquad.BP, 1500 + 4500 * rnd.unit(), 4); }
                if (rnd.unit() < plopRate) { plopEnv = 0.6 + 0.6 * rnd.unit(); plop.set(Dsp.Biquad.BP, 450 + 500 * rnd.unit(), 3); }
                dropEnvL *= de; dropEnvR *= de; plopEnv *= pe;
                float pp = plop.process((float) (rnd.next() * plopEnv)) * gDrop * 2.5f;
                l += dropL.process((float) (rnd.next() * dropEnvL)) * gDrop * 3f + pp;
                r += dropR.process((float) (rnd.next() * dropEnvR)) * gDrop * 3f + pp * 0.7f;
            }
            L[i] += l;
            R[i] += r;
        }
    }
}
