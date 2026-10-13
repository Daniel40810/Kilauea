package com.dan.kilauea.monitor;

import java.util.Locale;

/**
 * Das Wärmebild (Phase 14): Skala, Farben und die Regeln, nach denen der Shader {@code thermal.frag} jeder Stelle
 * eine Temperatur gibt. Hier in Java dieselben Zahlen für Legende, Prüfung und Auskunft.
 * <ul>
 * <li><b>Lava</b>: Oberflächen- und Kerntemperatur aus dem Lava-Modell (Phase 7/8); wo Risse offen sind, mischt
 * die Kamera Kruste und Glut. Ein Bildpunkt zeigt die Helligkeitstemperatur T = (a·Tk⁴ + (1 − a)·Tr⁴)^¼ in Kelvin
 * (Stefan–Boltzmann) [A].</li>
 * <li><b>Lavasee</b>: Platten nach ihrem Alter wie im Bild des Sees, Nähte bei 1 150 °C [A].</li>
 * <li><b>Fontäne</b>: die Temperatur jedes Teilchens (Phase 8) [A].</li>
 * <li><b>Boden</b>: Lufttemperatur nach Höhe (24 °C an der Küste, 6,5 K je km [L Standardatmosphäre; Küste A]),
 * dazu die Sonne: dunkles junges Gestein bis rund 30 K wärmer, Wald nur wenige Kelvin [G]; nachts ein paar
 * Kelvin kälter [G].</li>
 * <li><b>Dampfstellen</b> und Schwefelbänke rund 70 °C [G].</li>
 * <li><b>Meer</b> rund 25 °C [L Größenordnung], am Meereseintritt wärmer [G].</li>
 * <li><b>Himmel</b>: klar am Zenit rund −40 °C, zum Horizont hin wie die Luft; Wolken wie die Luft in ihrer Höhe
 * [G nach dem Verhalten von Wärmekameras].</li>
 * </ul>
 * Das Wärmebild sieht durch Dampf und Vog (wie Kameras im langwelligen Infrarot bei dünner Fahne) [A]; dichte
 * Wolken verdecken es.
 */
public final class Thermal {
    private Thermal() { }

    /** Was an einer Stelle liegt (Kanal 2 der Messung im Kreuz). */
    public enum Kind {
        SKY("Himmel", Prov.G), GROUND("Boden in Luft und Sonne", Prov.G), FOREST("Wald und Bewuchs", Prov.G), LAVA("Lava des Modells", Prov.A),
        LAKE("Lavasee", Prov.A), SEA("Meer", Prov.L), VENT("Dampfstelle", Prov.G), FOUNTAIN("Fontäne", Prov.A), CLOUD("Wolke", Prov.G), LIFE("Tier, Mensch oder Fahrzeug", Prov.G);

        public final String label;
        public final Prov prov;

        Kind(String label, Prov prov) { this.label = label; this.prov = prov; }

        public static Kind of(double code) {
            int i = (int) Math.round(code);
            return i >= 0 && i < values().length ? values()[i] : SKY;
        }
    }

    /** Lage auf der Skala 0..1: −20 … 60 °C nehmen 45 % ein, darüber logarithmisch bis 1 200 °C [G]. */
    public static double scale(double t) {
        if (t <= 60) return Math.max(0, 0.45 * (t + 20) / 80);
        return Math.min(1, 0.45 + 0.55 * Math.log1p((t - 60) / 40) / Math.log1p(1140 / 40.0));
    }

    /** Farben wie „Ironbow“: schwarz, violett, magenta, rot, orange, gelb, weiß [G]. */
    static final float[][] STOPS = {
            {0.00f, 0.00f, 0.00f, 0.03f}, {0.18f, 0.12f, 0.03f, 0.42f}, {0.36f, 0.50f, 0.05f, 0.58f}, {0.52f, 0.86f, 0.20f, 0.30f},
            {0.68f, 0.98f, 0.48f, 0.05f}, {0.86f, 1.00f, 0.82f, 0.22f}, {1.00f, 1.00f, 1.00f, 0.92f}};

    public static float[] palette(float u) {
        u = Math.max(0, Math.min(1, u));
        for (int i = 1; i < STOPS.length; i++) {
            if (u <= STOPS[i][0]) {
                float[] a = STOPS[i - 1], b = STOPS[i];
                float f = (u - a[0]) / (b[0] - a[0]);
                return new float[]{a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f};
            }
        }
        float[] l = STOPS[STOPS.length - 1];
        return new float[]{l[1], l[2], l[3]};
    }

    /** Helligkeitstemperatur (°C) einer Fläche aus Kruste (tc) und Glut (tg), Anteil a offen (Stefan–Boltzmann). */
    public static double brightness(double tc, double tg, double a) {
        double kc = tc + 273.15, kg = tg + 273.15;
        return Math.pow(a * kg * kg * kg * kg + (1 - a) * kc * kc * kc * kc, 0.25) - 273.15;
    }

    /** Lufttemperatur (°C) in Höhe h (m). */
    public static double air(double h) { return 24 - 0.0065 * Math.max(0, h); }

    /** Messung im Kreuz als Wert mit Herkunft. */
    public static Reading probe(double t, Kind k) {
        return Reading.of("Messkreuz", t, "°C", k.prov, "Wärmebild: " + k.label);
    }

    /** Was im Kreuz liegt, als Wort mit Herkunft der Regel. */
    public static Reading what(Kind k, double t) {
        String s = switch (k) {
            case LAVA -> t > 600 ? "Lava, Kruste mit offenen Rissen" : t > 100 ? "Lava, Kruste noch heiß" : "Lava, erkaltet";
            case LAKE -> "Lavasee: Platten und Nähte";
            case FOUNTAIN -> "Fontäne: Teilchen im Flug";
            case GROUND -> "Boden, von Luft und Sonne gewärmt";
            case FOREST -> "Wald: kaum wärmer als die Luft";
            case SEA -> "Meer";
            case VENT -> "Dampfstelle";
            case CLOUD -> "Wolke, so kalt wie die Luft dort";
            case SKY -> "klarer Himmel: kalt";
            case LIFE -> t > 150 ? "Abgas eines Motors" : t > 35 ? "Motor oder Reifen, warm" : "Körper, wärmer als die Luft";
        };
        return Reading.word("im Kreuz", s, k.prov, "Regel des Wärmebilds für " + k.label);
    }

    public static String label(double t) { return String.format(Locale.GERMAN, "%.0f °C", t); }
}
