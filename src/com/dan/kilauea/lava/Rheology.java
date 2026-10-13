package com.dan.kilauea.lava;

/**
 * Stoffwerte basaltischer Lava am Kīlauea und die Formeln, die das Zellenmodell daraus rechnet. Jeder
 * Wert trägt seine Herkunft: M gemessen, L Literatur, A abgeleitet (aus Literatur und Abgleich), G
 * gestaltet. Ein qualitatives Modell, ausdrücklich keine Vorhersage.
 * <p>
 * Das Fließgesetz ist das einer Bingham-Flüssigkeit, wie es zellulare Lavamodelle seit MAGFLOW
 * verwenden (Vicari u. a. 2007, Del Negro u. a. 2008): Lava fließt erst, wenn sie dicker ist als die
 * kritische Dicke {@code h_cr = S_y / (ρ g sin θ)}; darüber wächst der Fluss mit der dritten Potenz der
 * Dicke. Zähigkeit und Fließgrenze steigen exponentiell, wenn die Lava abkühlt (Form nach Ishihara u. a.
 * 1990, Werte für Hawaiʻi abgestimmt [A]).
 */
public final class Rheology {
    private Rheology() { }

    /** Temperatur am Schlot (°C): Kīlauea 1 140–1 170 °C [L, Helz &amp; Thornber 1987]. */
    public static final double T_VENT = 1150;
    /** Darunter steht die Lava und gilt als Gestein (°C) [A, knapp über dem Solidus um 980 °C]. */
    public static final double T_SOLID = 1000;
    /** Luft (°C) [G, Mittel zwischen Gipfel und Küste]. */
    public static final double T_AIR = 20;
    /** Dichte der blasigen Lava (kg/m³) [L, Pāhoehoe 1 800–2 600]. */
    public static final double RHO = 2200;
    /** Wärmekapazität (J/(kg·K)) [L]. */
    public static final double CP = 1150;
    /** Kristallisationswärme (J/kg) [L], verteilt auf das Intervall vom Schlot bis zum Erstarren. */
    public static final double LATENT = 3.5e5;
    /** Wirksame Wärmekapazität mit der Kristallisationswärme [A]. */
    public static final double C_EFF = CP + LATENT / (T_VENT - T_SOLID);
    /** Emissionsgrad [L], Stefan-Boltzmann-Konstante. */
    public static final double EPS = 0.95, SIGMA = 5.670374419e-8;
    /** Wärmeübergang an die Luft (W/(m²·K)) [L, Größenordnung bei Passat]. */
    public static final double H_AIR = 15;
    /** Wärmeleitung und Temperaturleitfähigkeit des Untergrunds [L]. */
    public static final double K_GROUND = 1.0, KAPPA = 7e-7;
    /** Wärmeleitung des Dachs einer Röhre (W/(m·K)) [L]. */
    public static final double K_ROOF = 1.5;
    /** Erdbeschleunigung. */
    public static final double G = 9.81;

    /**
     * Zähigkeit (Pa·s): rund 160 Pa·s am Schlot, 10⁴ bei 1 100 °C, 3·10⁷ beim Erstarren [A, Größenordnungen
     * nach Messungen an Hawaiʻi-Laven; Form log η = a + b·T nach Ishihara u. a. 1990].
     */
    public static double viscosity(double t) {
        double d = Math.max(-20, Math.min(220, T_VENT - t));
        return Math.pow(10, 2.2 + 0.035 * d);
    }

    /**
     * Fließgrenze (Pa): rund 100 Pa am Schlot, 2·10³ bei 1 100 °C, 6·10⁵ beim Erstarren [A, Größenordnung nach
     * den Dämmen und Dicken von Hawaiʻi-Strömen, etwa Moore 1987 für Mauna Loa 1984].
     */
    public static double yieldStrength(double t) {
        double d = Math.max(-20, Math.min(220, T_VENT - t));
        return Math.pow(10, 2.0 + 0.025 * d);
    }

    /**
     * Fluss (m³/s) aus einer Zelle mit Dicke h über eine Kante der Breite w in die Nachbarzelle im Abstand
     * d, wenn die Oberfläche um dz (&gt; 0) tiefer liegt. Bingham, stationär (MAGFLOW) [L]; 0 unter der
     * kritischen Dicke.
     */
    public static double flux(double h, double t, double dz, double d, double w) {
        if (h <= 0 || dz <= 0) return 0;
        double sy = yieldStrength(t), eta = viscosity(t);
        double sin = dz / Math.sqrt(d * d + dz * dz);
        double hcr = sy / (RHO * G * sin);
        double a = h / hcr;
        if (a <= 1) return 0;
        return sy * hcr * hcr * w / (3 * eta) * (a * a * a - 1.5 * a * a + 0.5);
    }

    /** Dicke der Kruste (m) nach ihrem Alter (s): 0,0779 m · √(Stunden) [L, Hon u. a. 1994, Pāhoehoe-Decken]. */
    public static double crust(double ageS) { return 0.0779 * Math.sqrt(Math.max(0, ageS) / 3600); }

    /**
     * Anteil der Kerntemperatur über der Luft, den die Oberfläche nach dem Alter der Kruste noch zeigt:
     * frisch 1, nach 10 s 0,5, nach einer Stunde rund 0,19 (gut 200 °C), nach einem Tag rund 0,09 [A, an
     * Messungen der Oberflächentemperatur nach Hon u. a. 1994 und Keszthelyi &amp; Denlinger 1996 angelehnt].
     */
    public static double surfaceFactor(double ageS) { return 1 / (1 + Math.pow(Math.max(0, ageS) / 10, 0.25)); }

    /** Wärmestrom an der Oberfläche (W/m²) bei der Oberflächentemperatur ts (°C): Strahlung und Wind. */
    public static double topLoss(double ts) {
        double k = ts + 273.15, a = T_AIR + 273.15;
        return EPS * SIGMA * (k * k * k * k - a * a * a * a) + H_AIR * (ts - T_AIR);
    }

    /** Wärmestrom in den Untergrund (W/m²) nach der Zeit seit dem Bedecken (s) [L, Halbraum, Wärmeleitung]. */
    public static double baseLoss(double t, double coveredS) {
        double delta = Math.max(0.1, Math.sqrt(Math.PI * KAPPA * Math.max(coveredS, 1)));
        return K_GROUND * Math.max(0, t - 100) / delta;
    }

    /**
     * Pāhoehoe wird zu ʻaʻā, wenn Zähigkeit mal Scherrate eine Schwelle übersteigt und die Lava schon so weit
     * abgekühlt (kristallreich) ist, dass die zerrissene Haut nicht mehr verheilt [L, Übergang nach Peterson &amp;
     * Tilling 1980; die Werte sind so abgestimmt, dass der Wechsel bei Förderraten um 5–10 m³/s liegt wie bei
     * Rowland &amp; Walker 1990, A]. Schwelle in Pa.
     */
    public static final double AA_STRESS = 250;
    /** Nur unter dieser Kerntemperatur (°C) kann ʻaʻā entstehen [A]. */
    public static final double AA_TEMP = 1128;
    /** Nur Lava, die sich mindestens so schnell bewegt (m/s), zerreißt zu ʻaʻā; ruhige Zehen bleiben Pāhoehoe [G]. */
    public static final double AA_SPEED = 0.3;
    /** So lange (s) muss die Spannung anhalten, bis die Lava ganz zu ʻaʻā geworden ist [G]. */
    public static final double AA_TIME = 300;
    /** Ab dieser Geschwindigkeit (m/s) reißt die Kruste offener Lava immer wieder auf [G]. */
    public static final double U_BREAK = 0.5;
    /** Kruste, ab der ein Fluss unter einem Dach weiterläuft (m) [A, rund vier Stunden ruhige Kruste]. */
    public static final double ROOF_MIN = 0.15;
    /** Unter dieser Geschwindigkeit (m/s) unter dem Dach entsteht keine Röhre, nur eine Decke, die sich aufbläht und erstarrt [G]. */
    public static final double U_TUBE = 0.03;
}
