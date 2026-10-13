package com.dan.kilauea.monitor;

/**
 * Schwefeldioxid (Phase 14), ohne Grafik. HVO misst den Ausstoß mit UV-Spektrometern, die quer unter der Fahne
 * durchfahren, und gibt ihn in Tonnen am Tag an [L]. Hier wird er aus dem gerechnet, was gerade ausbricht:
 * <ul>
 * <li><b>Förderung</b>: Jede Tonne Magma, die an der Oberfläche entgast, gibt rund 2,4 kg SO₂ ab
 * (rund 0,12 Gew.-% Schwefel, als SO₂ doppelt so schwer) [A, Größenordnung der Schwefelgehalte von
 * Kīlauea-Basalt]. Mit 75 % dichtem Anteil der blasigen Lava und 2 700 kg/m³ sind das rund 420 t/d je m³/s.</li>
 * <li><b>Lavasee</b> im Gipfel ohne Strom: rund 5 000 t/d, das Mittel der Jahre 2008–2018 [L, HVO].</li>
 * <li><b>Ruhe</b>: rund 30 t/d am Gipfel, wie Ende 2018 [L, HVO].</li>
 * <li>Ein Drehbuch kann die ruhige Entgasung des Gipfels vorgeben, etwa 1 750 t/d in den Pausen 2025 [L].</li>
 * </ul>
 * Zum Vergleich gemessen [L, HVO]: 2018 in der unteren Ostriftzone zuerst „über 50 000“, nach neuer Auswertung
 * fast 200 000 t/d; Episode 20 im Mai 2025 bis rund 50 000 t/d.
 */
public final class Gas {
    private Gas() { }

    /** Dichter Anteil der blasigen Lava (wie {@code Stage.DENSE}). */
    public static final double DENSE = 0.75;
    /** Dichte des Basalts (kg/m³). */
    public static final double RHO = 2700;
    /** SO₂ je kg entgastem Magma. */
    public static final double SO2_PER_KG = 0.0024;
    /** SO₂ (t/d) je m³/s blasiger Lava: rund 420. */
    public static final double T_PER_DAY_PER_RATE = DENSE * RHO * SO2_PER_KG * 86400 / 1000;
    /** Lavasee (t/d), Ruhe (t/d). */
    public static final double LAKE = 5000, QUIET = 30;
    /** Messwerte zum Vergleich (t/d). */
    public static final double MEASURED_2018_EARLY = 50_000, MEASURED_2018_REVISED = 200_000, MEASURED_EP20 = 50_000, MEASURED_PAUSE_2025 = 1750;

    /**
     * SO₂-Ausstoß (t/d): Summe der Förderraten (m³/s, nur positive), ob ein Lavasee offen ist, und die vom
     * Drehbuch vorgegebene ruhige Entgasung (NaN = nach dem See).
     */
    public static double so2(double rateSum, boolean lake, double passive) {
        double base = !Double.isNaN(passive) ? passive : lake ? LAKE : QUIET;
        return base + Math.max(0, rateSum) * T_PER_DAY_PER_RATE;
    }

    /** Herkunft des Werts in Worten (für die Tafel), je nachdem, was ihn bestimmt. */
    public static Reading reading(double rateSum, boolean lake, double passive) {
        double v = so2(rateSum, lake, passive);
        String src;
        Prov p;
        if (rateSum > 0.05) {
            src = String.format(java.util.Locale.GERMAN, "Förderung %.0f m³/s × %.0f t/d je m³/s", rateSum, T_PER_DAY_PER_RATE);
            p = Prov.A;
        } else if (!Double.isNaN(passive)) {
            src = "ruhige Entgasung vom Drehbuch, nach HVO-Messung";
            p = Prov.L;
        } else {
            src = lake ? "Lavasee, Mittel 2008–2018 nach HVO" : "Ruhe, wie Ende 2018 nach HVO";
            p = Prov.L;
        }
        return Reading.of("SO₂-Ausstoß", v, "t/d", p, src);
    }
}
