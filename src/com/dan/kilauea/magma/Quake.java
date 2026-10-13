package com.dan.kilauea.magma;

/**
 * Ein Erdbeben: Zeit (s seit Start), Herd (Szene, m; y = Höhe, unter dem Meer negativ), Magnitude
 * und Art (Gipfel, Gang, Flanke, Einsturz).
 */
public record Quake(double t, double x, double y, double z, double mag, Kind kind) {
    public enum Kind {
        /** unter der Caldera, durch Druck in den Speichern */
        SUMMIT,
        /** an der Spitze eines Gangs (wandernder Schwarm) */
        DIKE,
        /** auf der Gleitfläche unter der Südflanke */
        FLANK,
        /** Einsturz am Gipfel, vom Drehbuch eingetragen (2018) */
        COLLAPSE
    }
}
