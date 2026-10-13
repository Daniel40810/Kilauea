package com.dan.kilauea.render;

import com.dan.kilauea.atmos.Atmosphere;
import com.dan.kilauea.atmos.SunClock;

/**
 * Alles, was ein Bild von der Uhr und vom Wetter braucht, einmal je Bild auf der CPU gerechnet:
 * Sonnen- und Mondrichtung, Farbe von Sonnen- und Mondlicht, Dunst, Wind, Belichtung.
 * Getrennt vom Zeichnen, damit Prüfprogramme dieselben Werte ohne Grafikkarte bekommen.
 * <p>
 * Die Szene rechnet <b>vorbelichtet</b>: Sonnen- und Mondlicht und die Himmelstabelle werden schon mit
 * der Belichtung multipliziert (übernommen aus Kneiphof).
 * <p>
 * Herkunft: Passat aus Ostnordost mit 6 bis 9 m/s [L, NOAA-Klimawerte Hilo, gerundet]; Dunst am
 * Meeresspiegel 1,2·10⁻⁵/m [G, klare Seeluft mit etwas Vog]; Südswell 1 m [G].
 */
public final class SceneState {
    public final SunClock clock = new SunClock();
    /** Dunst: Mie-Streukoeffizient am Meeresspiegel in 1/m (klar 6e-6, Vog 4e-5). */
    public double haze = 1.2e-5;
    /** Wind: Stärke (m/s) und Richtung, aus der er kommt (Grad, 70 = Ostnordost). */
    public double windSpeed = 7.5, windDirDeg = 70;
    /** Südswell: Höhe (m) und Richtung, aus der er kommt (Grad). */
    public double swellHeight = 1.0, swellDirDeg = 190;
    /** Periode des Südswells (s): Sommerswell aus dem Südpazifik 12–20 s [L, PacIOOS]; 14 s [G]. */
    public double swellPeriod = 14;
    /** Sanft nachgeführte Größen 0..1: Regen, nasse Flächen, Regenbogen, Bedeckung (ab Phase 11). */
    public double rain, wet, rainbow, overcast;
    /** Bodennebel 0..1 (ab Phase 11). */
    public double mist;
    /** Belichtungskorrektur in Blendenstufen (vom Bedienfeld). */
    public double ev;

    public final double[] sunRaw = new double[3], moonRaw = new double[3];
    public final double[] sunColor = new double[3], moonColor = new double[3];
    public double exposure = 1, targetExposure = 1, stars, night;
    /**
     * Obergrenze der Belichtung (Phase 8): glüht Lava in der Nähe, stellt sich das Auge auf die Glut ein, nicht
     * auf das dunkle Gelände, sonst brennt die Lava weiß aus. Der Renderer setzt sie je Bild.
     */
    public double maxExposure = Double.MAX_VALUE;
    private boolean adapted;
    private double lastEstimate;
    /** Im Zeitraffer passt sich das Auge schneller an (vom Renderer gesetzt, 1 = Echtzeit). */
    public double adaptBoost = 1;
    public static final double KEY = 0.055;
    private double metered;
    private boolean hasMeter;

    /** Gemessene Helligkeit des letzten Bildes (geometrisches Mittel, vorbelichtet). */
    public void measured(double geoMeanPreExposed) {
        if (!(geoMeanPreExposed > 0) || Double.isInfinite(geoMeanPreExposed)) return;
        double scene = geoMeanPreExposed / exposure;
        metered = Math.max(1e-4, Math.min(1e6, KEY * Math.pow(scene, -0.88)));
        hasMeter = true;
    }

    public void update(double dt) {
        double[] t = Atmosphere.sunTransmittance(clock.elevationDeg, haze);
        double[] tm = Atmosphere.sunTransmittance(clock.moonElevationDeg, haze);
        double moonPower = Atmosphere.SUN_POWER * 2.5e-6 * clock.moonLit;
        for (int k = 0; k < 3; k++) {
            sunRaw[k] = t[k] * Atmosphere.SUN_POWER * (1 - 0.85 * overcast);
            moonRaw[k] = tm[k] * moonPower;
        }
        double estimate = targetExposure();
        double base = hasMeter ? metered : estimate;
        targetExposure = Math.min(base, maxExposure) * Math.pow(2, ev);
        if (!adapted || dt <= 0) { exposure = targetExposure; adapted = true; }
        else {
            // Phase 11, Zeitraffer: in der Dämmerung ändert sich das Licht in Sekunden um Größenordnungen. Die
            // Belichtung folgt der (glatten) Schätzung aus dem Sonnenstand sofort mit und gleicht nur den Rest
            // zur Messung am Bild sanft aus; sonst brennt der Morgen aus und der Abend säuft ab [G]
            if (lastEstimate > 0) exposure *= Math.max(0.5, Math.min(2.0, estimate / lastEstimate));
            exposure = Math.exp(Math.log(exposure) + (Math.log(targetExposure) - Math.log(exposure)) * (1 - Math.exp(-dt * 1.2 * adaptBoost)));
        }
        lastEstimate = estimate;
        for (int k = 0; k < 3; k++) { sunColor[k] = sunRaw[k] * exposure; moonColor[k] = moonRaw[k] * exposure; }
        double dark = Atmosphere.smooth(-4, -14, clock.elevationDeg);
        stars = 2.0e-7 * exposure * dark;
        night = Atmosphere.smooth(-3, -12, clock.elevationDeg) * (1 - 0.4 * clock.moonLit * Atmosphere.smooth(0, 20, clock.moonElevationDeg));
    }

    /** Belichtung aus der geschätzten Beleuchtungsstärke (bis die erste Messung am Bild vorliegt). */
    double targetExposure() {
        double el = clock.elevationDeg;
        double lumSun = 0.2126 * sunRaw[0] + 0.7152 * sunRaw[1] + 0.0722 * sunRaw[2];
        double direct = lumSun * Math.max(0, Math.sin(Math.toRadians(el)));
        double sky = 2.2 * Math.pow(10, skyLog(el));
        double lumMoon = 0.2126 * moonRaw[0] + 0.7152 * moonRaw[1] + 0.0722 * moonRaw[2];
        double moon = lumMoon * Math.max(0.15, Math.sin(Math.toRadians(Math.max(0, clock.moonElevationDeg))));
        double L = direct + sky + moon + 4e-6;
        return 0.85 * Math.pow(L, -0.96);
    }

    static double skyLog(double el) {
        double[] e = {-90, -18, -12, -6, 0, 6, 90};
        double[] v = {-7.0, -6.3, -4.6, -2.6, -0.8, 0.0, 0.0};
        if (el <= e[0]) return v[0];
        for (int i = 1; i < e.length; i++)
            if (el <= e[i]) return v[i - 1] + (v[i] - v[i - 1]) * (el - e[i - 1]) / (e[i] - e[i - 1]);
        return 0;
    }

    /** Wind als Vektor, in den er weht (x Osten, z Süden), mal Stärke. */
    public double[] windVector() {
        double a = Math.toRadians(windDirDeg + 180);
        return new double[]{Math.sin(a) * windSpeed, -Math.cos(a) * windSpeed};
    }

    /** Swell als Vektor, in den er läuft, mal Höhe. */
    public double[] swellVector() {
        double a = Math.toRadians(swellDirDeg + 180);
        return new double[]{Math.sin(a) * swellHeight, -Math.cos(a) * swellHeight};
    }
}
