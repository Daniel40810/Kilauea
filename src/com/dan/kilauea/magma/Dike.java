package com.dan.kilauea.magma;

/**
 * Ein Gang: Magma bricht aus dem Speicher der Südcaldera seitlich in eine Riftzone aus und schiebt sich
 * als Platte von rund 2 km Höhe und 1,5 m Dicke voran [A nach Beobachtungen 2018: rund 20 km in wenigen
 * Tagen]. Länge = Volumen / (Höhe · Dicke).
 */
public final class Dike {
    public final Rift rift;
    public final double start;
    /** Oberkante und Unterkante (Höhe über dem Meer, m), Dicke (m). */
    public final double top, bottom, width;
    double volume;
    /** Geschwindigkeit der Spitze (m/s), zuletzt. */
    double speed;
    boolean active = true;
    /**
     * Phase 12: ein vom Drehbuch oder von Hand ausgelöster Gang läuft mit vorgegebenem Tempo (m/s) bis zur
     * Länge target (m ab der Wurzel bei 1 500 m) und bleibt dort stehen; das Volumen kommt aus beiden Speichern.
     * Sonst negativ (der Gang folgt dem Druck).
     */
    double forcedSpeed = -1, target = -1;

    Dike(Rift rift, double start, double top, double bottom, double width) {
        this.rift = rift;
        this.start = start;
        this.top = top;
        this.bottom = bottom;
        this.width = width;
    }

    public double volume() { return volume; }

    /** Länge des Gangs (m) entlang der Riftzone. */
    public double length() { return volume / ((top - bottom) * width); }

    /** Lage der Spitze (x, z). */
    public double[] tip() { return rift.at(Math.min(rift.length(), 1500 + length())); }

    public boolean active() { return active; }

    public double speed() { return speed; }

    /** true, wenn ein ausgelöster Gang sein Ziel erreicht hat (Phase 12). */
    public boolean arrived() { return target >= 0 && length() >= target - 1; }

    /** Ziel (m ab der Wurzel) eines ausgelösten Gangs, sonst negativ. */
    public double target() { return target; }
}
