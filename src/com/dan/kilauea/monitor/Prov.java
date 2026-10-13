package com.dan.kilauea.monitor;

/**
 * Herkunft eines angezeigten Werts (Phase 14), dieselben Marken wie im Werkbuch und im Bedienfeld:
 * M gemessen (Daten wie das Höhenmodell), L belegt (Literatur, USGS/NPS), A abgeleitet (aus belegten Werten
 * gerechnet), G gestaltet (frei gewählt, damit es stimmig aussieht oder sich bedienen lässt).
 */
public enum Prov {
    M("gemessen"), L("belegt"), A("abgeleitet"), G("gestaltet");

    public final String word;

    Prov(String word) { this.word = word; }

    /** Die Marke so, wie sie im Bild steht: „[A]“. */
    public String tag() { return "[" + name() + "]"; }

    /** Legende für Tafeln: „M gemessen · L belegt · A abgeleitet · G gestaltet“. */
    public static String legend() {
        StringBuilder b = new StringBuilder();
        for (Prov p : values()) {
            if (b.length() > 0) b.append(" · ");
            b.append(p.name()).append(' ').append(p.word);
        }
        return b.toString();
    }
}
