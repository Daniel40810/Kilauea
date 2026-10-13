package com.dan.kilauea.lava;

/**
 * Ein Schlot oder eine Spalte: eine Strecke von (x0, z0) nach (x1, z1) in der Szene (bei einem Punkt
 * fallen beide zusammen), aus der Lava mit der Förderrate {@link #rate} (m³/s, blasige Lava) und der
 * Temperatur {@link #temperature} austritt. Die Rate darf sich jederzeit ändern (Drehbücher, Magma); eine
 * negative Rate ist Rückfluss: der Schlot schluckt Lava (Phase 12).
 */
public final class Vent {
    public final String name;
    public final double x0, z0, x1, z1;
    /** Förderrate (m³/s). */
    public volatile double rate;
    /**
     * Höhe der Fontäne (m), wenn ein Drehbuch sie vorgibt (Phase 12, nach Beobachtung [L]); sonst negativ, dann
     * gilt die Regel des Ortes ({@link VentSite#fountainHeight}) oder keine Fontäne.
     */
    public volatile double fountain = -1;
    /** Ort, nach dem sich die Fontäne richtet, wenn {@link #fountain} nicht gesetzt ist (oder null). */
    public volatile VentSite site;
    /** Temperatur am Austritt (°C). */
    public volatile double temperature = Rheology.T_VENT;
    /** Insgesamt ausgetreten (m³). */
    double erupted;
    /** Zellen des Schlots (vom Feld belegt). */
    int[] cells = new int[0];

    public Vent(String name, double x0, double z0, double x1, double z1, double rate) {
        this.name = name;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.rate = rate;
    }

    public Vent(String name, double x, double z, double rate) { this(name, x, z, x, z, rate); }

    /** Ein Schlot an einem der benannten Orte. */
    public static Vent at(VentSite s, double rate) {
        Vent v = new Vent(s.label, s.x0(), s.z0(), s.x1(), s.z1(), rate);
        v.site = s;
        return v;
    }

    /** Höhe der Fontäne jetzt (m): vorgegeben, nach der Regel des Ortes oder 0; ohne Förderung 0. */
    public double fountainHeight() {
        double r = rate;
        if (r <= 0) return 0;
        double f = fountain;
        if (f >= 0) return f;
        VentSite s = site;
        return s != null ? s.fountainHeight(r) : 0;
    }

    public double erupted() { return erupted; }

    public double length() { return Math.hypot(x1 - x0, z1 - z0); }
}
