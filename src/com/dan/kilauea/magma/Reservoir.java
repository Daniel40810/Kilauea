package com.dan.kilauea.magma;

import com.dan.kilauea.terrain.Geo;

/**
 * Ein Magmaspeicher als Druckbehälter: Lage (Szene, m), Tiefe der Mitte (Höhe über dem Meer, negativ),
 * Volumen V und wirksame Zusammendrückbarkeit β (Magma mit Gasblasen und nachgiebige Wand).
 * Der Überdruck folgt dem gespeicherten Mehrvolumen: P = ΔV / (V·β).
 */
public final class Reservoir {
    public final String name;
    public final double x, z, y;
    /** Volumen (m³) und wirksame Zusammendrückbarkeit (1/Pa). */
    public final double volume, beta;
    /** Halbachsen für die Schnittansicht (m): waagerecht, senkrecht. */
    public final double rh, rv;
    /** Gespeichertes Mehrvolumen gegenüber dem Ruhezustand (m³); bestimmt Druck und Hebung. */
    double dV;

    Reservoir(String name, double lat, double lon, double y, double volume, double beta, double rh, double rv) {
        this.name = name;
        this.x = Geo.x(lat, lon);
        this.z = Geo.z(lat);
        this.y = y;
        this.volume = volume;
        this.beta = beta;
        this.rh = rh;
        this.rv = rv;
    }

    /** Überdruck (Pa). */
    public double pressure() { return dV / (volume * beta); }

    /** Mehrvolumen (m³). */
    public double excess() { return dV; }
}
