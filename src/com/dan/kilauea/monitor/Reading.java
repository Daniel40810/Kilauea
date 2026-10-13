package com.dan.kilauea.monitor;

import java.util.Locale;

/**
 * Ein angezeigter Wert mit seiner Herkunft (Phase 14). Alles, was das Messnetz, das Wärmebild oder die
 * Bodenauskunft im Bild zeigt, ist ein Reading; die Prüfung stellt sicher, dass keines ohne Marke und Quelle ist.
 *
 * @param label  wofür der Wert steht („Neigung UWD“)
 * @param value  Zahl (NaN, wenn der Wert ein Wort ist)
 * @param unit   Einheit („µrad“, „t/d“; leer bei Wörtern)
 * @param text   fertiger Text, wenn der Wert ein Wort ist (sonst leer)
 * @param prov   Herkunft
 * @param source woher genau, kurz („Mogi-Quellen der zwei Speicher“)
 */
public record Reading(String label, double value, String unit, String text, Prov prov, String source) {

    public Reading {
        if (prov == null) throw new IllegalArgumentException("Reading ohne Herkunft: " + label);
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Reading ohne Quelle: " + label);
        if (unit == null) unit = "";
        if (text == null) text = "";
    }

    public static Reading of(String label, double value, String unit, Prov prov, String source) {
        return new Reading(label, value, unit, "", prov, source);
    }

    public static Reading word(String label, String text, Prov prov, String source) {
        return new Reading(label, Double.NaN, "", text, prov, source);
    }

    /** Der Wert als Text ohne Marke, mit sinnvollen Stellen: „42 000 t/d“, „−6,2 µrad“, „1,2 cm“. */
    public String formatted() {
        if (!text.isEmpty() || Double.isNaN(value)) return text;
        double a = Math.abs(value);
        String num;
        boolean whole = value == Math.rint(value) && (unit.isEmpty() || unit.equals("Jahre"));
        if (whole && a < 1000) num = String.format(Locale.GERMAN, "%.0f", value);
        else if (a >= 1000) num = String.format(Locale.GERMAN, "%,.0f", round(value, a));
        else if (a >= 100) num = String.format(Locale.GERMAN, "%.0f", value);
        else if (a >= 10) num = String.format(Locale.GERMAN, "%.1f", value);
        else if (a >= 0.1) num = String.format(Locale.GERMAN, "%.1f", value);
        else if (a > 0) num = String.format(Locale.GERMAN, "%.2f", value);
        else num = "0";
        num = num.replace('-', '−');
        if (value > 0 && signed()) num = "+" + num;
        return unit.isEmpty() ? num : num + " " + unit;
    }

    /** Wert mit Marke: „42 000 t/d [A]“. */
    public String withTag() { return formatted() + " " + prov.tag(); }

    private boolean signed() { return unit.equals("µrad") || unit.equals("cm"); }

    /** Große Zahlen auf zwei bis drei gültige Stellen (Modellwerte sind nicht genauer). */
    private static double round(double v, double a) {
        double step = a >= 100_000 ? 1000 : a >= 10_000 ? 100 : 10;
        return Math.round(v / step) * step;
    }
}
