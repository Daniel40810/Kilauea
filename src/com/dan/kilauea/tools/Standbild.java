package com.dan.kilauea.tools;

import com.dan.kilauea.render.Quality;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.Place;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfhilfe: rechnet Standbilder mit demselben Renderer wie die App, ohne Fenster (Offscreen-Bildspeicher).
 * Läuft auch dort, wo kein Bildschirm ist (in meiner Prüfumgebung unter Mesa), und auf deinem Rechner.
 * <p>Aufruf: {@code Standbild <ordner> [breite höhe] [name ...]} — ohne Namen alle Ansichten.</p>
 */
public final class Standbild {
    /** Eine Ansicht: Name, Ort, Tag im Jahr, Uhrzeit (HST), Blick (Grad), Neigung, Abstand (m), Qualität. */
    record View(String name, Place place, int day, double hour, double yaw, double pitch, double dist, Quality q) { }

    static final List<View> VIEWS = new ArrayList<>();

    /** Ein Standbild zu Fuß: Lage (x, z) erst nach dem Laden bekannt (Lavaröhre), Blickrichtung (Grad von Nord), Neigung, Uhrzeit. */
    record Walk(String name, java.util.function.Supplier<double[]> at, double heading, double pitch, double hour) { }

    static final List<Walk> WALKS = new ArrayList<>();
    static {
        var sp = com.dan.kilauea.terrain.SummitPaths.WALK_SPOTS;
        WALKS.add(new Walk("p4_geh_uekahuna", () -> new double[]{sp.get(0).x(), sp.get(0).z()}, 150, -6, 9.5));
        WALKS.add(new Walk("p4_geh_steaming", () -> new double[]{sp.get(1).x(), sp.get(1).z()}, 55, -3, 7.5));
        WALKS.add(new Walk("p4_geh_iki_rand", () -> new double[]{sp.get(2).x(), sp.get(2).z()}, 130, -14, 10.0));
        WALKS.add(new Walk("p4_geh_iki_boden", () -> new double[]{sp.get(3).x(), sp.get(3).z()}, 260, 2, 11.0));
        WALKS.add(new Walk("p4_geh_nahuku_grube", () -> new double[]{sp.get(4).x(), sp.get(4).z()}, 210, -12, 11.0));
        WALKS.add(new Walk("p4_geh_nahuku_innen", () -> tubeAt(false, 40), 0, 0, 11.0));
    }

    /** Punkt auf der Achse der Röhre: vom Eingang aus ds Meter (start) bzw. ab der Mitte; dazu die Richtung. */
    static double[] tubeAt(boolean start, double ds) {
        var t = com.dan.kilauea.terrain.LavaTube.get();
        double s = start ? t.roofFrom + ds : ds;
        int i = (int) Math.max(0, Math.min(t.n - 2, s));
        double heading = Math.toDegrees(Math.atan2(t.tx[i], -t.tz[i]));
        return new double[]{t.x[i], t.z[i], heading, t.floorAt(s)};
    }
    /** Lava-Szenen (Phase 8): Schlot, Rate, Stunden vorgerechnet, Schlot zu nach (h), Blick auf Strom (0) oder Front (1). */
    record LavaShot(com.dan.kilauea.lava.VentSite site, double rate, double hours, double off, int aim) { }

    static final java.util.Map<String, LavaShot> LAVA = new java.util.LinkedHashMap<>();
    static {
        var V = com.dan.kilauea.lava.VentSite.class;
        LAVA.put("p8_maunaulu_tag", new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 0));
        LAVA.put("p8_maunaulu_abend", new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 0));
        LAVA.put("p8_maunaulu_nacht", new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 0));
        LAVA.put("p8_front_nacht", new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 1));
        LAVA.put("p8_front_tag", new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 1));
        LAVA.put("p8_spalte8_nacht", new LavaShot(com.dan.kilauea.lava.VentSite.FISSURE8, 100, 2, 99, 0));
        LAVA.put("p8_puuoo_nacht", new LavaShot(com.dan.kilauea.lava.VentSite.PUU_OO, 4, 12, 99, 0));
        LAVA.put("p8_iki_abend", new LavaShot(com.dan.kilauea.lava.VentSite.KILAUEA_IKI, 50, 1.5, 99, 0));
        LAVA.put("p8_fontaene_nacht", new LavaShot(com.dan.kilauea.lava.VentSite.HALEMAUMAU, 20, 0.5, 99, 2));
        LAVA.put("p9_glut", new LavaShot(com.dan.kilauea.lava.VentSite.KAMOKUNA, 6, 5, 99, 1));
        for (String k : new String[]{"p10_eintritt", "p10_nacht", "p10_luft", "p10_schlauch", "p10_tauchen_1", "p10_tauchen_2", "p10_tauchen_3",
                "p10_tauchen_4", "p10_kissen_a", "p10_kissen_b", "p10_kissen_c"})
            LAVA.put(k, new LavaShot(com.dan.kilauea.lava.VentSite.KAMOKUNA, 6, 5, 99, 3));
        LAVA.put("p11_flimmern", new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 1));
        LAVA.put("p11_nacht", new LavaShot(null, 0, 0, 0, 3));
        LAVA.put("p8_see_abend", new LavaShot(null, 0, 0, 0, 3));
        LAVA.put("p8_see_nacht", new LavaShot(null, 0, 0, 0, 3));
    }

    static {
        // Phase 8: Lava sichtbar (Ort, Uhrzeit; Blick wird in lavaScene gesetzt)
        VIEWS.add(new View("p8_maunaulu_tag", Place.MAUNA_ULU, 280, 11.0, 200, 30, 1_500, Quality.HOCH));
        VIEWS.add(new View("p8_maunaulu_abend", Place.MAUNA_ULU, 280, 18.4, 200, 30, 1_500, Quality.HOCH));
        VIEWS.add(new View("p8_maunaulu_nacht", Place.MAUNA_ULU, 280, 21.0, 200, 30, 1_500, Quality.HOCH));
        VIEWS.add(new View("p8_front_nacht", Place.MAUNA_ULU, 280, 21.0, 20, 14, 45, Quality.HOCH));
        VIEWS.add(new View("p8_front_tag", Place.MAUNA_ULU, 280, 10.0, 20, 14, 45, Quality.HOCH));
        VIEWS.add(new View("p8_spalte8_nacht", Place.FISSURE8, 280, 21.5, 210, 26, 2_600, Quality.HOCH));
        VIEWS.add(new View("p8_puuoo_nacht", Place.PUU_OO, 280, 22.0, 240, 45, 1_600, Quality.HOCH));
        VIEWS.add(new View("p8_iki_abend", Place.KILAUEA_IKI, 280, 18.4, 250, 24, 1_400, Quality.HOCH));
        VIEWS.add(new View("p8_fontaene_nacht", Place.HALEMAUMAU, 280, 21.0, 200, 30, 1_700, Quality.HOCH));
        VIEWS.add(new View("p8_see_abend", Place.HALEMAUMAU, 280, 18.4, 200, 32, 700, Quality.HOCH));
        VIEWS.add(new View("p8_see_nacht", Place.HALEMAUMAU, 280, 21.0, 200, 32, 700, Quality.HOCH));
    }

    /** Phase 11: Wind je Ansicht (aus Richtung, Grad), sonst Passat aus 70°. */
    static final java.util.Map<String, Double> WIND = new java.util.HashMap<>();
    /** Phase 11: Ort je Ansicht (Breite, Länge), wo kein benannter Ort passt. */
    static final java.util.Map<String, double[]> AT = new java.util.HashMap<>();
    static {
        // Phase 11: Luft und Licht (Blick: Gierwinkel 360° − Himmelsrichtung, wie bei allen Ansichten)
        VIEWS.add(new View("p11_insel", Place.MAUNA_LOA, 280, 13.5, 5, 58, 125_000, Quality.HOCH));
        VIEWS.add(new View("p11_ostflanke", Place.PUU_OO, 280, 14.0, 300, 10, 22_000, Quality.HOCH));
        VIEWS.add(new View("p11_regen", Place.NAHUKU, 280, 15.0, 255, 4, 300, Quality.HOCH));
        VIEWS.add(new View("p11_vog", Place.HALEMAUMAU, 280, 11.0, 225, 28, 22_000, Quality.HOCH));
        VIEWS.add(new View("p11_kona", Place.HALEMAUMAU, 280, 11.0, 20, 28, 22_000, Quality.HOCH));
        VIEWS.add(new View("p11_abend", Place.MAUNA_KEA, 280, 17.75, 100, 2, 2_500, Quality.HOCH));
        VIEWS.add(new View("p11_regenbogen", Place.KALUAPELE, 280, 17.6, 255, 4, 9_000, Quality.HOCH));
        VIEWS.add(new View("p11_nacht", Place.HALEMAUMAU, 280, 22.0, 200, 22, 5_000, Quality.HOCH));
        VIEWS.add(new View("p11_mond", Place.MAUNA_KEA, 290, 22.0, 125, 2, 400, Quality.HOCH));
        VIEWS.add(new View("p11_flimmern", Place.MAUNA_ULU, 280, 11.0, 20, 6, 60, Quality.HOCH));
        WIND.put("p11_kona", 200.0);
        AT.put("p11_regen", new double[]{19.695, -155.17});      // Hang über Hilo, rund 500 m, unter dem Wolkenband
    }

    static {
        // Phase 6: Schnitt (Ort und Blick ergeben sich aus der Lage des Schnitts)
        VIEWS.add(new View("p6_schnitt", Place.HALEMAUMAU, 280, 10.0, 0, 0, 0, Quality.HOCH));
        VIEWS.add(new View("p6_gang", Place.HALEMAUMAU, 280, 10.0, 0, 0, 0, Quality.HOCH));
        VIEWS.add(new View("p6_gang_nah", Place.HALEMAUMAU, 280, 10.0, 0, 0, 0, Quality.HOCH));
        VIEWS.add(new View("p6_suedwest", Place.HALEMAUMAU, 280, 10.0, 0, 0, 0, Quality.HOCH));
        VIEWS.add(new View("p6_nordsued", Place.HALEMAUMAU, 280, 10.0, 0, 0, 0, Quality.HOCH));
        VIEWS.add(new View("p6_aufgeklappt", Place.HALEMAUMAU, 280, 10.0, 0, 0, 0, Quality.HOCH));
        // Phase 5: Riftzonen und Küste
        VIEWS.add(new View("p5_kette", Place.PAUAHI, 280, 10.0, 230, 34, 4_200, Quality.HOCH));
        VIEWS.add(new View("p5_puhimau", Place.PUHIMAU, 280, 11.0, 200, 30, 700, Quality.HOCH));
        VIEWS.add(new View("p5_pauahi", Place.PAUAHI, 280, 9.5, 240, 22, 1_100, Quality.HOCH));
        VIEWS.add(new View("p5_maunaulu", Place.MAUNA_ULU, 280, 8.5, 210, 16, 1_200, Quality.HOCH));
        VIEWS.add(new View("p5_makaopuhi", Place.MAKAOPUHI, 280, 10.5, 200, 28, 2_600, Quality.HOCH));
        VIEWS.add(new View("p5_puuoo", Place.PUU_OO, 280, 9.0, 220, 24, 2_200, Quality.HOCH));
        VIEWS.add(new View("p5_spalte8", Place.FISSURE8, 280, 10.0, 210, 22, 1_300, Quality.HOCH));
        VIEWS.add(new View("p5_kanal", Place.FISSURE8, 280, 8.5, 215, 24, 2_400, Quality.HOCH));
        // Phase 9: Meer und Brandung
        VIEWS.add(new View("p9_bogen", Place.HOLEI_ARCH, 280, 15.5, 320, 6, 170, Quality.HOCH));
        VIEWS.add(new View("p9_klippe", Place.HOLEI_ARCH, 280, 9.5, 70, 7, 650, Quality.HOCH));
        VIEWS.add(new View("p9_brandung", Place.HOLEI_ARCH, 280, 14.0, 20, 32, 140, Quality.HOCH));
        VIEWS.add(new View("p9_sturm", Place.HOLEI_ARCH, 280, 16.5, 40, 5, 320, Quality.HOCH));
        VIEWS.add(new View("p9_glut", Place.KAMOKUNA, 280, 21.0, 15, 7, 330, Quality.HOCH));
        // Phase 10: Meereseintritt und unter Wasser (Ziel: stärkster Eintritt bzw. jüngstes Kissen)
        VIEWS.add(new View("p10_eintritt", Place.KAMOKUNA, 280, 11.0, 15, 9, 380, Quality.HOCH));
        VIEWS.add(new View("p10_nacht", Place.KAMOKUNA, 280, 21.0, 15, 9, 380, Quality.HOCH));
        VIEWS.add(new View("p10_luft", Place.KAMOKUNA, 280, 11.5, 330, 40, 1_500, Quality.HOCH));
        VIEWS.add(new View("p10_schlauch", Place.KAMOKUNA, 280, 20.5, 40, 4, 150, Quality.HOCH));
        VIEWS.add(new View("p10_tauchen_1", Place.KAMOKUNA, 280, 10.5, 20, 35, 140, Quality.HOCH));
        VIEWS.add(new View("p10_tauchen_2", Place.KAMOKUNA, 280, 10.5, 20, 22, 30, Quality.HOCH));
        VIEWS.add(new View("p10_tauchen_3", Place.KAMOKUNA, 280, 10.5, 20, 12, 18, Quality.HOCH));
        VIEWS.add(new View("p10_tauchen_4", Place.KAMOKUNA, 280, 10.5, 20, 18, 9, Quality.HOCH));
        VIEWS.add(new View("p10_kissen_a", Place.KAMOKUNA, 280, 10.5, 40, 28, 5, Quality.HOCH));
        VIEWS.add(new View("p10_kissen_b", Place.KAMOKUNA, 280, 10.5, 40, 28, 5, Quality.HOCH));
        VIEWS.add(new View("p10_kissen_c", Place.KAMOKUNA, 280, 10.5, 40, 28, 5, Quality.HOCH));
        VIEWS.add(new View("p9_kapoho", Place.KAPOHO, 280, 11.0, 250, 28, 2_200, Quality.HOCH));
        VIEWS.add(new View("p9_abend", Place.HOLEI_ARCH, 280, 17.9, 75, 3, 420, Quality.HOCH));
        VIEWS.add(new View("p9_kamokuna", Place.KAMOKUNA, 280, 12.5, 300, 18, 1_500, Quality.HOCH));
        VIEWS.add(new View("p9_luft", Place.HOLEI_ARCH, 280, 13.0, 10, 40, 5_000, Quality.HOCH));
        VIEWS.add(new View("p5_kapoho", Place.KAPOHO, 280, 15.0, 250, 26, 5_500, Quality.HOCH));
        VIEWS.add(new View("p5_holeipali", Place.HOLEI_PALI, 280, 16.0, 340, 12, 3_000, Quality.HOCH));
        VIEWS.add(new View("p5_hilina", Place.HILINA_PALI, 280, 16.5, 60, 14, 4_000, Quality.HOCH));
        VIEWS.add(new View("p5_holei", Place.HOLEI_ARCH, 280, 15.5, 320, 6, 170, Quality.HOCH));
        VIEWS.add(new View("p5_holei_bogen", Place.HOLEI_ARCH, 280, 15.5, 320, 6, 170, Quality.HOCH));
        VIEWS.add(new View("p5_kueste", Place.HOLEI_ARCH, 280, 16.0, 110, 10, 1_400, Quality.HOCH));
        VIEWS.add(new View("p4_gipfel", Place.KALUAPELE, 280, 15.0, 200, 32, 9_000, Quality.HOCH));
        VIEWS.add(new View("p4_uekahuna", Place.UEKAHUNA, 280, 9.5, 330, 14, 700, Quality.HOCH));
        VIEWS.add(new View("p4_halemaumau", Place.HALEMAUMAU, 280, 10.0, 160, 28, 1_900, Quality.HOCH));
        VIEWS.add(new View("p4_fahne", Place.KALUAPELE, 280, 8.0, 330, 12, 7_000, Quality.HOCH));
        VIEWS.add(new View("p4_dampf", Place.UEKAHUNA, 280, 7.5, 250, 10, 1_400, Quality.HOCH));
        VIEWS.add(new View("p4_kilaueaiki", Place.KILAUEA_IKI, 280, 9.0, 250, 22, 1_300, Quality.HOCH));
        VIEWS.add(new View("p3_insel", Place.MAUNA_LOA, 280, 10.0, 5, 58, 125_000, Quality.HOCH));
        VIEWS.add(new View("p3_alter", Place.MAUNA_LOA, 280, 10.0, 5, 58, 125_000, Quality.HOCH));
        VIEWS.add(new View("p3_ostrift", Place.PUU_OO, 280, 10.5, 330, 48, 26_000, Quality.HOCH));
        VIEWS.add(new View("p3_puuoo_feld", Place.PUU_OO, 280, 9.0, 200, 30, 6_000, Quality.HOCH));
        VIEWS.add(new View("p3_gipfel", Place.KALUAPELE, 280, 15.0, 200, 32, 9_000, Quality.HOCH));
        VIEWS.add(new View("p3_pahoehoe", Place.MAUNA_ULU, 280, 16.0, 150, 14, 22, Quality.HOCH));
        VIEWS.add(new View("p3_aa", Place.FISSURE8, 280, 10.0, 60, 16, 30, Quality.HOCH));
        VIEWS.add(new View("p3_regenwald", Place.NAHUKU, 280, 11.0, 210, 20, 400, Quality.HOCH));
        VIEWS.add(new View("p3_kau", Place.KAU_DESERT, 280, 16.5, 40, 12, 1_500, Quality.HOCH));
        VIEWS.add(new View("p3_schwefel", Place.UEKAHUNA, 280, 10.0, 300, 25, 1_600, Quality.HOCH));
        VIEWS.add(new View("p3_maunaloa", Place.MAUNA_LOA, 280, 9.0, 40, 35, 30_000, Quality.HOCH));
        VIEWS.add(new View("p3_strasse", Place.MAUNA_ULU, 280, 15.5, 135, 18, 650, Quality.HOCH));
        VIEWS.add(new View("p3_pali", Place.HOLEI_ARCH, 280, 15.0, 30, 22, 4_500, Quality.HOCH));
        VIEWS.add(new View("p3_kapoho", Place.KAPOHO, 280, 12.0, 230, 25, 9_000, Quality.HOCH));
    }

    /** Phase 12: ein Moment aus einem Drehbuch: Kapitel (ab 0) und Anteil darin; die Kamera folgt dem Kapitel, sonst Blick nach Kompass. */
    record ScriptShot(String name, String script, int chapter, double frac, double[] look) { }

    static final List<ScriptShot> SCRIPT_SHOTS = new ArrayList<>();
    static {
        SCRIPT_SHOTS.add(new ScriptShot("p12_1959_vorhang", "1959", 1, 0.7, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_1959_schlot", "1959", 2, 0.85, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_1959_fontaene", "1959", 4, 0.6, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_1959_see", "1959", 5, 0.4, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_maunaulu_spalten", "maunaulu", 0, 0.8, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_maunaulu_fontaene", "maunaulu", 1, 0.75, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_maunaulu_stroeme", "maunaulu", 2, 0.95, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_puuoo_fontaene", "puuoo", 1, 0.7, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_kamokuna", "puuoo", 3, 0.97, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_delta", "puuoo", 4, 0.5, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_schlauch", "puuoo", 5, 0.8, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2018_vorher", "2018", 0, 0.5, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2018_gang", "2018", 1, 0.5, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2018_leilani", "2018", 2, 0.75, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2018_spalte8", "2018", 3, 0.7, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2018_einsturz_a", "2018", 4, 0.01, new double[]{19.4066, -155.2846, 0, 150, 28, 3400}));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2018_einsturz_b", "2018", 4, 0.98, new double[]{19.4066, -155.2846, 0, 150, 28, 3400}));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2024_heben", "2024", 0, 0.9, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2024_episode1", "2024", 1, 0.8, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2024_fontaene", "2024", 3, 0.5, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_2024_boden", "2024", 4, 0.6, null));
        SCRIPT_SHOTS.add(new ScriptShot("p12_frei", "frei", 1, 0.5, null));
    }

    /** Ein Drehbuch bis zum Moment des Standbilds vorrechnen (ohne Faden, der Reihe nach); Kamera wie im Kapitel. */
    static void scriptScene(Renderer r, GLOffscreenAutoDrawable d, ScriptShot s) {
        var p = r.player;
        p.headless = true;
        p.hold = false;
        p.outroSeconds = 1e9;
        r.lava.speed = 0;
        if (!s.script().equals(lastScript) || p.state() == com.dan.kilauea.script.Player.State.IDLE || p.chapterIndex() > s.chapter()) {
            com.dan.kilauea.script.Script sc = s.script().equals("frei")
                    ? com.dan.kilauea.script.Scripts.free(com.dan.kilauea.magma.Rift.EAST, 25_000, 20, 300, 4)
                    : com.dan.kilauea.script.Scripts.byId(s.script());
            p.start(sc);
            p.update(0);
            lastScript = s.script();
        }
        long t0 = System.nanoTime();
        while (p.state() == com.dan.kilauea.script.Player.State.PLAYING) {
            var c = p.script().chapters().get(p.chapterIndex());
            if (p.chapterIndex() > s.chapter()) break;
            double goal = p.chapterIndex() < s.chapter() ? c.duration : s.frac() * c.duration;
            double left = goal - p.chapterTime();
            if (p.chapterIndex() == s.chapter() && left <= 1e-6) break;
            // Phase 14: für Bilder mit Messnetz in Schritten von einer „Sekunde Wanduhr“, das Messnetz schreibt mit
            p.advance(Math.min(Math.max(left, 1e-3), (sampleMonitor ? 1 : 60) * c.speed));
            if (sampleMonitor) {
                r.monitor.rateOverride = c.speed;
                r.monitor.passiveSO2 = r.stage.passiveSO2;
                r.monitor.sample(1);
            }
        }
        p.hold = true;
        r.lava.installGround();
        r.lava.refreshLakeImage();
        r.lava.refreshGlow();
        r.lava.refreshSeaEntry();
        r.warmFountain(40);
        r.warmEntry(60);
        if (s.look() != null) {
            double x = com.dan.kilauea.terrain.Geo.x(s.look()[0], s.look()[1]), z = com.dan.kilauea.terrain.Geo.z(s.look()[0]);
            r.camera.lookAt(x, Math.max(0, Ground.height(x, z)) + s.look()[2], z, (360 - s.look()[3]) % 360, s.look()[4], s.look()[5]);
        }
        r.camera.snap();
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(110); } catch (InterruptedException e) { }
            d.display();
            r.camera.snap();   // ein Blick vor den Schnitt wird erst im Bild gesetzt
        }
        System.out.printf(Locale.GERMAN, "  %s · vorgerechnet in %.1f s · %s%n", p.summary(), (System.nanoTime() - t0) / 1e9,
                r.lava.summary());
    }

    private static String lastScript;
    /** Phase 14: beim Vorrechnen eines Drehbuchs das Messnetz mitschreiben lassen. */
    static boolean sampleMonitor;

    // ------------------------------------------------------------------ Phase 15: Leben und Klang

    /** Nächste Figur einer Art zu (x, z): die 16 Zahlen ihrer Instanz, oder null. */
    static float[] nearest(com.dan.kilauea.life.Life life, int kind, double x, double z, java.util.function.Predicate<float[]> ok) {
        float[] best = null;
        double bd = Double.MAX_VALUE;
        synchronized (life) {
            for (int i = 0; i < life.count[kind]; i++) {
                float[] a = java.util.Arrays.copyOfRange(life.inst[kind], i * com.dan.kilauea.life.Life.F, (i + 1) * com.dan.kilauea.life.Life.F);
                if (ok != null && !ok.test(a)) continue;
                double dd = Math.hypot(a[0] - x, a[2] - z);
                if (dd < bd) { bd = dd; best = a; }
            }
        }
        return best;
    }

    /** Kompassrichtung des Blicks auf eine Figur: ihre Laufrichtung plus add Grad. */
    static double view(float[] a, double add) {
        double heading = Math.toDegrees(Math.atan2(-Math.sin(a[3]), -Math.cos(a[3])));
        return ((heading + add) % 360 + 360) % 360;
    }

    /** Kamera grob hinstellen, Figuren vorlaufen lassen (die Kamera bestimmt, wo Vögel entstehen). */
    static void lifeAt(Renderer r, GLOffscreenAutoDrawable d, int day, double hour, double x, double z, double above, double secs) {
        r.state.clock.set(day, hour);
        r.camera.lookAt(x, Ground.height(x, z), z, 0, 30, above);
        r.camera.snap();
        d.display();
        r.warmLife(secs);
    }

    /** Auf eine Figur schauen, Pflanzen abwarten, Figuren anhalten und aufnehmen. */
    static void lifeShot(Renderer r, GLOffscreenAutoDrawable d, File out, int w, int h, String name, double x, double y, double z,
                         double yaw, double pitch, double dist) throws Exception {
        boolean kv = r.camera.keepView;
        r.camera.keepView = false;
        r.camera.lookAt(x, y, z, yaw, pitch, dist);
        r.camera.snap();
        for (int i = 0; i < 300 && !r.floraComplete(); i++) d.display();
        r.lifeFrozen = true;
        shoot(r, d, out, w, h, name, 8);
        r.lifeFrozen = false;
        r.camera.keepView = kv;
        System.out.println("  " + r.life.summary(r.world) + " · " + r.lifeStats());
    }

    static void p15(Renderer r, GLOffscreenAutoDrawable d, File out, int w, int h, List<String> only) throws Exception {
        boolean all = only.contains("p15");
        java.util.function.Predicate<String> want = n -> all || only.contains(n);
        r.setQuality(Quality.HOCH);
        r.showLod = false;
        r.showAge = false;
        r.thermal = false;
        r.monitorMode = 2;
        r.state.windDirDeg = 70;
        r.state.swellHeight = 1.0;
        r.camera.diving = false;
        r.lava.reset();
        r.lava.refreshGlow();
        // Nēnē am Parkplatz Uēkahuna, Blick über die Caldera
        if (want.test("p15_nene_uekahuna")) {
            double sx = r.life.nene.siteX(0), sz = r.life.nene.siteZ(0);
            lifeAt(r, d, 281, 9.4, sx, sz, 60, 90);
            float[] b = nearest(r.life, com.dan.kilauea.life.Life.K_NENE, sx, sz, null);
            if (b != null) lifeShot(r, d, out, w, h, "p15_nene_uekahuna", b[0], b[1] + 0.22, b[2], view(b, 100), 6, 1.9);
        }
        // Gössel im Frühjahr am Besucherzentrum
        if (want.test("p15_nene_goessel")) {
            double sx = r.life.nene.siteX(1), sz = r.life.nene.siteZ(1);
            lifeAt(r, d, 60, 10.0, sx, sz, 60, 90);
            float[] b = nearest(r.life, com.dan.kilauea.life.Life.K_NENE, sx, sz, a -> a[4] < 0.8f);
            if (b != null) lifeShot(r, d, out, w, h, "p15_nene_goessel", b[0], b[1] + 0.15, b[2], view(b, 80), 34, 2.4);
        }
        // Ein Auto hält für eine Nēnē auf der Straße
        if (want.test("p15_nene_strasse")) {
            r.state.clock.set(281, 10.5);
            double[] hit = null;
            for (int round = 0; round < 120 && hit == null; round++) {
                r.warmLife(5);
                for (double[] c : r.life.cars.state()) {
                    if (c[7] == 1 || c[2] > 0.3) continue;
                    if (r.life.nene.near(c[0], c[1], 22)) { hit = c; break; }
                }
            }
            if (hit != null) {
                var pos = r.life.nene.positions();
                double[] nb = null;
                double bd = 1e9;
                for (double[] q : pos) { double dd = Math.hypot(q[0] - hit[0], q[2] - hit[1]); if (dd < bd) { bd = dd; nb = q; } }
                double mx = (hit[0] + nb[0]) / 2, mz = (hit[1] + nb[2]) / 2;
                double yaw = Math.toDegrees(Math.atan2(nb[0] - hit[0], -(nb[2] - hit[1]))) + 70;
                lifeAt(r, d, 281, 10.5, mx, mz, 40, 0);
                lifeShot(r, d, out, w, h, "p15_nene_strasse", mx, Ground.height(mx, mz) + 0.6, mz, yaw, 9, 13);
            } else System.out.println("  p15_nene_strasse: kein Halt gefunden");
        }
        // Autos auf der Chain of Craters Road unter der Hōlei Pali
        if (want.test("p15_autos_holei")) {
            double x = com.dan.kilauea.terrain.Geo.x(19.3125, -155.1435), z = com.dan.kilauea.terrain.Geo.z(19.3125);
            lifeAt(r, d, 281, 11.2, x, z, 300, 60);
            float[] c = nearest(r.life, com.dan.kilauea.life.Life.K_SEDAN, x, z, null);
            float[] c2 = nearest(r.life, com.dan.kilauea.life.Life.K_SUV, x, z, null);
            if (c2 != null && (c == null || Math.hypot(c2[0] - x, c2[2] - z) < Math.hypot(c[0] - x, c[2] - z))) c = c2;
            if (c != null) lifeShot(r, d, out, w, h, "p15_autos_holei", c[0], c[1] + 0.8, c[2], view(c, 115), 9, 24);
        }
        // Nachts mit Licht am Crater Rim Drive
        if (want.test("p15_auto_nacht")) {
            double x = com.dan.kilauea.terrain.Geo.x(19.4250, -155.2810), z = com.dan.kilauea.terrain.Geo.z(19.4250);
            lifeAt(r, d, 281, 19.4, x, z, 200, 60);
            float[] c = null;
            for (int k = com.dan.kilauea.life.Life.K_SEDAN; k <= com.dan.kilauea.life.Life.K_VAN; k++) {
                float[] q = nearest(r.life, k, x, z, a -> a[15] > 0.5f);
                if (q != null && (c == null || Math.hypot(q[0] - x, q[2] - z) < Math.hypot(c[0] - x, c[2] - z))) c = q;
            }
            if (c != null) {
                lifeShot(r, d, out, w, h, "p15_auto_nacht", c[0] - Math.sin(c[3]) * 9, c[1] + 0.8, c[2] + Math.cos(c[3]) * 9, view(c, 150), 7, 16);
                if (want.test("p15_waerme_auto")) {
                    r.thermal = true;
                    r.lifeFrozen = true;
                    shoot(r, d, out, w, h, "p15_waerme_auto", 6);
                    r.lifeFrozen = false;
                    r.thermal = false;
                }
            }
        }
        // Hubschrauber auf der Puʻu-ʻŌʻō-Schleife
        if (want.test("p15_heli_puuoo")) {
            Place p = Place.PUU_OO;
            lifeAt(r, d, 281, 11.0, p.x(), p.z(), 3000, 1);
            r.life.demoHelicopter(r.world);
            r.warmLife(140);
            var hs = r.life.helicopters();
            if (!hs.isEmpty()) {
                var hh = hs.get(0);
                double hd = Math.toDegrees(hh.heading);
                lifeShot(r, d, out, w, h, "p15_heli_puuoo", hh.x, hh.y, hh.z, hd + 125, -4, 55);
                System.out.printf(Locale.GERMAN, "  Hubschrauber %.0f m über Grund, %.0f m/s%n", hh.y - Ground.height(hh.x, hh.z), Math.hypot(hh.vx, hh.vz));
            }
        }
        // Wanderer auf dem Boden des Kīlauea Iki
        if (want.test("p15_wanderer_iki")) {
            Place p = Place.KILAUEA_IKI;
            lifeAt(r, d, 281, 10.3, p.x(), p.z(), 300, 150);
            float[] best = null;
            for (int k = 0; k < com.dan.kilauea.life.Life.HIKER_VARIANTS; k++) {
                float[] q = nearest(r.life, com.dan.kilauea.life.Life.K_HIKER + k, p.x(), p.z(), a -> a[1] < 1110);
                if (q != null && (best == null || Math.hypot(q[0] - p.x(), q[2] - p.z()) < Math.hypot(best[0] - p.x(), best[2] - p.z()))) best = q;
            }
            if (best != null) lifeShot(r, d, out, w, h, "p15_wanderer_iki", best[0], best[1] + 1, best[2], view(best, 70), 8, 9);
        }
        // Koaʻe kea im Halemaʻumaʻu
        if (want.test("p15_koae")) {
            Place p = Place.HALEMAUMAU;
            lifeAt(r, d, 281, 10.0, p.x(), p.z(), 600, 20);
            float[] b = nearest(r.life, com.dan.kilauea.life.Life.K_TROPIC, p.x(), p.z(), null);
            if (b != null) lifeShot(r, d, out, w, h, "p15_koae", b[0], b[1], b[2], view(b, 70), 18, 2.6);
        }
        // ʻApapane am Morgen im Wald bei Nāhuku
        if (want.test("p15_apapane")) {
            Place p = Place.NAHUKU;
            lifeAt(r, d, 281, 7.0, p.x(), p.z(), 30, 25);
            float[] b = null;
            for (int k = com.dan.kilauea.life.Life.K_APAPANE; k <= com.dan.kilauea.life.Life.K_MEJIRO; k++) {
                float[] q = nearest(r.life, k, p.x(), p.z(), a -> a[12] > -0.9f);   // fliegend
                if (q != null && (b == null || Math.hypot(q[0] - p.x(), q[2] - p.z()) < Math.hypot(b[0] - p.x(), b[2] - p.z()))) b = q;
            }
            if (b == null) b = nearest(r.life, com.dan.kilauea.life.Life.K_APAPANE, p.x(), p.z(), null);
            if (b != null) lifeShot(r, d, out, w, h, "p15_apapane", b[0], b[1], b[2], view(b, 90), 8, 0.55);
        }
        // Wärmebild: Nēnē am Abend
        if (want.test("p15_waerme_nene")) {
            double sx = r.life.nene.siteX(2), sz = r.life.nene.siteZ(2);
            lifeAt(r, d, 281, 18.9, sx, sz, 60, 60);
            float[] b = nearest(r.life, com.dan.kilauea.life.Life.K_NENE, sx, sz, null);
            if (b != null) {
                r.thermal = true;
                lifeShot(r, d, out, w, h, "p15_waerme_nene", b[0], b[1] + 0.3, b[2], view(b, 90), 14, 5);
                r.thermal = false;
            }
        }
        // Wärmebild aus 5,8 km über der Caldera am späten Nachmittag (dein Statusbild zu Phase 14)
        if (want.test("p15_waerme_wolke")) {
            Place p = Place.KALUAPELE;
            r.state.clock.set(280, 17.25);
            r.camera.lookAt(p.x(), Ground.height(p.x(), p.z()), p.z(), p.viewYaw, p.viewPitch, p.viewDist);
            r.camera.snap();
            for (int i = 0; i < 100 && !r.floraComplete(); i++) d.display();
            r.warmWeather(120);
            r.thermal = true;
            shoot(r, d, out, w, h, "p15_waerme_wolke", 8);
            System.out.println("  Kreuz: " + r.thermalProbe().withTag() + " · " + r.thermalWhat().withTag() + " · " + r.weatherStats());
            r.thermal = false;
            shoot(r, d, out, w, h, "p15_farbe_wolke", 6);
        }
        // Tafel mit ʻokina im Titel
        if (want.test("p15_auskunft")) {
            Place p = Place.HALEMAUMAU;
            r.state.clock.set(281, 10.5);
            r.camera.lookAt(p.x(), Ground.height(p.x(), p.z()), p.z(), 200, 30, 1500);
            r.camera.snap();
            for (int i = 0; i < 100 && !r.floraComplete(); i++) d.display();
            r.showInfo(r.groundInfo(), p.x(), p.z());
            shoot(r, d, out, w, h, "p15_auskunft", 8);
            r.clearInfo();
        }
        r.monitorMode = 0;
    }

    // ------------------------------------------------------------------ Phase 14: Messnetz, Wärmebild, Wissen

    static void p14(Renderer r, GLOffscreenAutoDrawable d, File out, int w, int h, List<String> only) throws Exception {
        boolean all = only.contains("p14");
        java.util.function.Predicate<String> want = n -> all || only.contains(n);
        r.setQuality(Quality.HOCH);
        r.showLod = false;
        r.showAge = false;
        r.archIntact = false;
        r.state.windDirDeg = 70;
        r.state.swellHeight = 1.0;
        r.camera.diving = false;
        // Drehbücher mit dem Messnetz im Bild
        Object[][] scripted = {
                {"p14_2018_gang", new ScriptShot("p14_2018_gang", "2018", 1, 0.55, new double[]{19.4050, -155.2300, 0, 95, 32, 30000}), false},
                {"p14_2018_einsturz", new ScriptShot("p14_2018_einsturz", "2018", 4, 0.62, new double[]{19.4140, -155.2900, 0, 150, 24, 5200}), false},
                {"p14_2024_episode", new ScriptShot("p14_2024_episode", "2024", 3, 0.55, null), false},
                {"p14_2024_waerme", new ScriptShot("p14_2024_waerme", "2024", 3, 0.55, null), true}};
        for (Object[] o : scripted) {
            String name = (String) o[0];
            if (!want.test(name)) continue;
            ScriptShot ss = (ScriptShot) o[1];
            r.monitorMode = 1;
            r.thermal = (Boolean) o[2];
            sampleMonitor = true;
            if (!ss.script().equals(lastScript)) lastScript = null;
            scriptScene(r, d, ss);
            sampleMonitor = false;
            r.sectionMode = com.dan.kilauea.render.SectionRenderer.Mode.OFF;
            shoot(r, d, out, w, h, name, 9);
            System.out.println("  Messnetz: " + r.monitor.summary());
        }
        // die Trommel in Echtzeit: Gang 2018, sechs Minuten Schwarm
        if (want.test("p14_2018_trommel")) {
            r.monitorMode = 1;
            r.thermal = false;
            sampleMonitor = true;
            lastScript = null;
            scriptScene(r, d, new ScriptShot("p14_2018_trommel", "2018", 1, 0.45, new double[]{19.4050, -155.2300, 0, 95, 32, 30000}));
            sampleMonitor = false;
            r.sectionMode = com.dan.kilauea.render.SectionRenderer.Mode.OFF;
            var p = r.player;
            r.monitor.rateOverride = 1;
            for (int i = 0; i < 400; i++) { p.hold = false; p.advance(1); p.hold = true; r.monitor.sample(1); }
            shoot(r, d, out, w, h, "p14_2018_trommel", 9);
        }
        if (r.player.state() != com.dan.kilauea.script.Player.State.IDLE) r.player.returnToStart();
        lastScript = null;
        r.monitor.rateOverride = Double.NaN;
        r.monitorMode = 2;
        // Wärmebilder ohne Drehbuch, je mit dem Farbbild daneben
        Object[][] heat = {
                {"p14_waerme_maunaulu", new View("p14_waerme_maunaulu", Place.MAUNA_ULU, 280, 11.0, 200, 30, 1_600, Quality.HOCH), new LavaShot(com.dan.kilauea.lava.VentSite.MAUNA_ULU, 10, 3, 99, 0)},
                {"p14_waerme_see", new View("p14_waerme_see", Place.HALEMAUMAU, 280, 10.0, 200, 38, 1_500, Quality.HOCH), new LavaShot(null, 0, 0, 0, 3)},
                {"p14_waerme_gipfel", new View("p14_waerme_gipfel", Place.UEKAHUNA, 280, 13.0, 120, 30, 5_000, Quality.HOCH), null}};
        for (Object[] o : heat) {
            String name = (String) o[0];
            if (!want.test(name)) continue;
            View v = (View) o[1];
            r.state.clock.set(v.day(), v.hour());
            r.camera.lookAt(v.place().x(), Math.max(0, Ground.height(v.place().x(), v.place().z())), v.place().z(), v.yaw(), v.pitch(), v.dist());
            r.camera.snap();
            if (o[2] != null) lavaScene(r, d, v, (LavaShot) o[2]);
            else { r.lava.reset(); r.lava.refreshGlow(); }
            for (int i = 0; i < 300 && !r.floraComplete(); i++) d.display();
            r.thermal = false;
            shoot(r, d, out, w, h, name.replace("waerme", "farbe"), 14);
            r.thermal = true;
            shoot(r, d, out, w, h, name, 6);
            System.out.println("  Kreuz: " + r.thermalProbe().withTag() + " · " + r.thermalWhat().withTag());
        }
        r.thermal = false;
        // Tafeln aus dem Wissen
        Object[][] info = {
                {"p14_auskunft_iki", 19.4160, -155.2475, 200.0, 30.0, 1_400.0, 10.5},
                {"p14_auskunft_maunaulu", 19.3450, -155.1900, 20.0, 26.0, 2_200.0, 15.0},
                {"p14_ort_puuoo", Double.NaN, 0.0, 0.0, 0.0, 0.0, 11.0}};
        for (Object[] o : info) {
            String name = (String) o[0];
            if (!want.test(name)) continue;
            r.state.clock.set(280, (Double) o[6]);
            r.lava.reset();
            r.lava.refreshGlow();
            Place pl = Place.PUU_OO;
            boolean place = Double.isNaN((Double) o[1]);
            double x = place ? pl.x() : com.dan.kilauea.terrain.Geo.x((Double) o[1], (Double) o[2]), z = place ? pl.z() : com.dan.kilauea.terrain.Geo.z((Double) o[1]);
            if (place) r.camera.lookAt(x, Math.max(0, Ground.height(x, z)), z, pl.viewYaw, pl.viewPitch, pl.viewDist);
            else r.camera.lookAt(x, Math.max(0, Ground.height(x, z)), z, (Double) o[3], (Double) o[4], (Double) o[5]);
            r.camera.snap();
            for (int i = 0; i < 300 && !r.floraComplete(); i++) d.display();
            // die Tafel erst jetzt: sie läuft nach 40 s Wanduhr ab, und die Pflanzen brauchen unter Mesa länger
            r.showInfo(place ? com.dan.kilauea.monitor.Lexicon.place(pl) : r.groundInfo(), x, z);
            shoot(r, d, out, w, h, name, 14);
        }
        r.clearInfo();
        r.monitorMode = 0;
    }

    /** Einige Bilder rechnen (Belichtung, Tafeln), dann speichern. */
    static void shoot(Renderer r, GLOffscreenAutoDrawable d, File out, int w, int h, String name, int frames) throws Exception {
        r.warmSteam(150);
        r.warmSea(25);
        long t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) { r.step(i < frames - 1 ? 0.6 : 0.0); d.display(); try { Thread.sleep(260); } catch (InterruptedException e) { } }
        d.display();
        javax.imageio.ImageIO.write(Grab.read(d, w, h), "png", new File(out, name + ".png"));
        System.out.printf(Locale.GERMAN, "%-24s %s · Auge %.0f m · %.0f ms · %s%n", name, com.dan.kilauea.atmos.SunClock.timeLabel(r.state.clock.hour()),
                r.camera.ey, (System.nanoTime() - t0) / 1e6 / frames, r.passTimes());
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
    }

    /** Phase 13: Bild aus einem Rundflug – Tour, Station, Sekunden an der Station (negativ: Anteil des Flugs dorthin), Uhrzeit. */
    record TourShot(String name, String tour, int stop, double at, double hour) { }

    static final List<TourShot> TOUR_SHOTS = List.of(
            new TourShot("p13_gipfel_uekahuna", "gipfel", 1, 1.5, 9.5),
            new TourShot("p13_gipfel_iki", "gipfel", 4, 2.0, 10.0),
            new TourShot("p13_rift_puhimau", "rift", 1, 3.0, 10.5),
            new TourShot("p13_rift_flug", "rift", 5, -0.5, 11.0),
            new TourShot("p13_rift_kamokuna", "rift", 8, 2.0, 17.6),
            new TourShot("p13_2018_leilani", "2018", 2, 2.0, 11.0),
            new TourShot("p13_2018_bilanz", "2018", 6, 2.0, 15.5));

    /** Rundflug bis zum gewünschten Augenblick abspielen (ohne zu zeichnen), dann anhalten. */
    static void tourScene(Renderer r, TourShot ts) {
        var tp = r.tour;
        tp.paused = false;
        r.camera.hold = false;
        tp.stop();
        tp.update(0, r.camera);
        r.camera.autoOrbit = false;
        Place p = Place.MAUNA_LOA;
        r.camera.lookAt(p.x() + 8000, 1500, p.z() - 6000, 5, 55, 125_000);
        r.camera.snap();
        tp.start(com.dan.kilauea.camera.Tour.byId(ts.tour()));
        double dt = 1 / 30.0;
        tp.update(dt, r.camera);
        double atStop = 0;
        for (int i = 0; i < 30 * 600 && tp.active(); i++) {
            tp.update(dt, r.camera);
            r.camera.update(dt);
            if (tp.index() == ts.stop()) {
                if (ts.at() < 0) { if (r.camera.flying() && r.camera.flightProgress() >= -ts.at()) break; }
                else if (!r.camera.flying()) { atStop += dt; if (atStop >= ts.at()) break; }
            }
        }
        tp.paused = true;
        r.camera.hold = true;
        r.settleBars();
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        int w = args.length > 2 ? Integer.parseInt(args[1]) : 1280, h = args.length > 2 ? Integer.parseInt(args[2]) : 720;
        List<String> only = new ArrayList<>();
        for (int i = 3; i < args.length; i++) only.add(args[i]);
        Renderer.vorab();
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);   // das fertige Bild bleibt im Zeichen-Bildspeicher, dort wird es gelesen
        Renderer r = new Renderer();
        if (Boolean.getBoolean("kilauea.ohneLava")) r.showLava(false);
        r.frozen = true;
        d.addGLEventListener(r);
        d.addGLEventListener(new GLEventListener() {
            @Override public void init(GLAutoDrawable a) { }
            @Override public void dispose(GLAutoDrawable a) { }
            @Override public void reshape(GLAutoDrawable a, int x, int y, int ww, int hh) { }
            @Override public void display(GLAutoDrawable a) { }
        });
        d.display();
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        while (!r.terrainReady()) { Thread.sleep(100); d.display(); }
        if (Boolean.getBoolean("kilauea.orte")) {
            VIEWS.clear();
            WALKS.clear();
            for (Place p : Place.values()) VIEWS.add(new View("ort_" + p.name().toLowerCase(), p, 280, 10.5, p.viewYaw, p.viewPitch, p.viewDist, Quality.HOCH));
        }
        for (View v : VIEWS) {
            if (!only.isEmpty() && !only.contains(v.name())) continue;
            r.setQuality(v.q());
            r.showOcean = !Boolean.getBoolean("kilauea.ohneMeer");
            r.showSteam = !Boolean.getBoolean("kilauea.ohneDampf");
            r.showLod = v.name().endsWith("_lod");
            r.showAge = v.name().endsWith("_alter");
            r.archIntact = v.name().endsWith("_bogen");
            r.state.clock.set(v.day(), v.hour());
            r.state.swellHeight = v.name().contains("sturm") ? 2.5 : 1.0;
            r.state.windDirDeg = WIND.getOrDefault(v.name(), 70.0);
            double gx = v.place().x(), gz = v.place().z();
            if (AT.containsKey(v.name())) {
                double[] ll = AT.get(v.name());
                gx = com.dan.kilauea.terrain.Geo.x(ll[0], ll[1]);
                gz = com.dan.kilauea.terrain.Geo.z(ll[0]);
            }
            r.camera.lookAt(gx, Math.max(0, Ground.height(gx, gz)), gz, v.yaw(), v.pitch(), v.dist());
            r.camera.snap();
            r.sectionMode = com.dan.kilauea.render.SectionRenderer.Mode.OFF;
            if (v.name().startsWith("p6_")) section(r, d, v.name());
            r.camera.diving = false;
            if (LAVA.containsKey(v.name())) lavaScene(r, d, v, LAVA.get(v.name()));
            if (v.name().startsWith("p10_tauchen") || v.name().startsWith("p10_kissen")) {
                if (v.name().equals("p10_kissen_b") || v.name().equals("p10_kissen_c")) r.warmEntry(30);
                double[] tip = r.pillowTip();
                if (tip != null) {
                    r.camera.diving = true;
                    double[] dd = com.dan.kilauea.sea.Pillows.downhill(Ground::height, tip[0], tip[2]);
                    double yaw = Math.toDegrees(Math.atan2(dd[0], dd[1])) + v.yaw() - 20;   // hangabwärts, etwas seitlich
                    r.camera.lookAt(tip[0], tip[1], tip[2], yaw, v.pitch(), v.dist());
                    r.camera.snap();
                    System.out.printf(Locale.GERMAN, "  Kissen bei %.0f / %.1f / %.0f m, Grund dort %.1f m, Auge %.1f m%n", tip[0], tip[1], tip[2],
                            Ground.height(tip[0], tip[2]), r.camera.ey);
                    double[] an = r.pillowAnchor();
                    if (an != null) System.out.printf(Locale.GERMAN, "  Kolonie ab %.0f / %.0f m, Grund dort %.1f m%n", an[0], an[1], Ground.height(an[0], an[1]));
                }
            }
            for (int i = 0; i < 400 && !r.floraComplete(); i++) d.display();   // Pflanzen um die Kamera setzen
            r.warmSteam(150);
            r.warmSea(25);
            if (v.name().startsWith("p11_")) r.warmWeather(240);
            long t0 = System.nanoTime();
            // einige Bilder, bis die Belichtung eingeschwungen ist
            for (int i = 0; i < 14; i++) {
                r.step(i < 13 ? 0.6 : 0.0);
                d.display();
            }
            double ms = (System.nanoTime() - t0) / 1e6 / 14;
            java.awt.image.BufferedImage img = Grab.read(d, w, h);
            File f = new File(out, v.name() + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            if (v.name().equals("p11_flimmern")) {
                // Hitzeflimmern sieht man nur in Bewegung: Unterschied zum Bild 0,2 s später, achtfach verstärkt
                r.step(0.2);
                d.display();
                java.awt.image.BufferedImage b = Grab.read(d, w, h), diff = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int p = img.getRGB(x, y), q = b.getRGB(x, y), o = 0;
                        for (int sh = 0; sh <= 16; sh += 8) o |= Math.min(255, 8 * Math.abs(((p >> sh) & 255) - ((q >> sh) & 255))) << sh;
                        diff.setRGB(x, y, o);
                    }
                javax.imageio.ImageIO.write(diff, "png", new File(out, v.name() + "_unterschied.png"));
            }
            System.out.printf(Locale.GERMAN, "%-18s %s, %s · Auge %.0f m · Belichtung %.3g · %.0f ms je Bild (Software-Grafik) · %s%n", v.name(), v.place().label,
                    com.dan.kilauea.atmos.SunClock.timeLabel(v.hour()), r.camera.ey, r.state.exposure, ms, r.floraStats() + " · " + r.passTimes() + " · CPU " + r.cpuTimes() + " · " + r.seaStats() + (v.name().startsWith("p10_") ? " · " + r.entryStats() : "") + (v.name().startsWith("p11_") ? " · " + r.weatherStats() : ""));
            if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        }
        for (ScriptShot ss : SCRIPT_SHOTS) {
            if (!only.contains(ss.name()) && !(only.contains("p12") && !only.isEmpty())) continue;
            r.setQuality(Quality.HOCH);
            r.showLod = false;
            r.showAge = false;
            r.archIntact = false;
            r.state.windDirDeg = 70;
            r.state.swellHeight = 1.0;
            r.camera.diving = false;
            scriptScene(r, d, ss);
            for (int i = 0; i < 30 && !r.floraComplete(); i++) d.display();
            r.warmSteam(150);
            r.warmSea(25);
            long t0 = System.nanoTime();
            for (int i = 0; i < 9; i++) { r.step(i < 8 ? 0.6 : 0.0); d.display(); }
            double ms = (System.nanoTime() - t0) / 1e6 / 9;
            javax.imageio.ImageIO.write(Grab.read(d, w, h), "png", new File(out, ss.name() + ".png"));
            System.out.printf(Locale.GERMAN, "%-22s %s · Auge %.0f m · Belichtung %.3g · %.0f ms · %s%n", ss.name(),
                    com.dan.kilauea.atmos.SunClock.timeLabel(r.state.clock.hour()), r.camera.ey, r.state.exposure, ms, r.passTimes());
            if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        }
        if (r.player.state() != com.dan.kilauea.script.Player.State.IDLE) { r.player.returnToStart(); }
        for (TourShot ts : TOUR_SHOTS) {
            if (!only.contains(ts.name()) && !only.contains("p13")) continue;
            r.setQuality(Quality.HOCH);
            r.showLod = false;
            r.showAge = false;
            r.archIntact = false;
            r.state.windDirDeg = 70;
            r.state.swellHeight = 1.0;
            r.camera.diving = false;
            r.state.clock.set(280, ts.hour());
            tourScene(r, ts);
            for (int i = 0; i < 150 && !r.floraComplete(); i++) d.display();
            r.warmSteam(150);
            r.warmSea(25);
            long t0 = System.nanoTime();
            for (int i = 0; i < 14; i++) { r.step(i < 13 ? 0.6 : 0.0); d.display(); }
            double ms = (System.nanoTime() - t0) / 1e6 / 14;
            javax.imageio.ImageIO.write(Grab.read(d, w, h), "png", new File(out, ts.name() + ".png"));
            System.out.printf(Locale.GERMAN, "%-22s %s · Station %d · Flug %s · Auge %.0f m · Hub %.1f° · %.0f ms · %s%n", ts.name(),
                    com.dan.kilauea.atmos.SunClock.timeLabel(ts.hour()), r.tour.index() + 1, r.camera.flying(), r.camera.ey,
                    Math.toDegrees(r.camera.lastLift), ms, r.passTimes());
            if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        }
        r.tour.paused = false;
        r.camera.hold = false;
        r.tour.stop();
        p14(r, d, out, w, h, only);
        p15(r, d, out, w, h, only);
        for (Walk wv : WALKS) {
            if (!only.isEmpty() && !only.contains(wv.name())) continue;
            r.setQuality(Quality.HOCH);
            r.showLod = false;
            r.showAge = false;
            r.state.clock.set(280, wv.hour());
            double[] at = wv.at().get();
            double heading = at.length > 2 ? at[2] : wv.heading();
            r.camera.startWalk(at[0], at[1], heading);
            r.camera.wpitch = Math.toRadians(wv.pitch());
            if (at.length > 3) r.camera.wfoot = at[3];
            r.camera.snap();
            for (int i = 0; i < 400 && !r.floraComplete(); i++) d.display();
            r.warmSteam(150);
            long t0 = System.nanoTime();
            for (int i = 0; i < 14; i++) { r.step(i < 13 ? 0.6 : 0.0); d.display(); }
            double ms = (System.nanoTime() - t0) / 1e6 / 14;
            javax.imageio.ImageIO.write(Grab.read(d, w, h), "png", new File(out, wv.name() + ".png"));
            System.out.printf(Locale.GERMAN, "%-22s zu Fuß, Füße %.1f m · %.0f ms · %s · %s%n", wv.name(), r.camera.wfoot, ms, r.floraStats(), r.passTimes() + " · CPU " + r.cpuTimes());
            if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        }
        d.destroy();
        System.exit(0);
    }

    /** Lava-Szene: Ausbruch vorrechnen, Glutkarte, Kamera auf Strom, Front, Fontäne oder See. */
    static void lavaScene(Renderer r, GLOffscreenAutoDrawable d, View v, LavaShot s) {
        var sim = r.lava;
        sim.speed = 0;                       // der Thread rechnet nicht mit, alles hier der Reihe nach
        sim.installGround();
        double tx, tz;
        if (s.site() != null) {
            String key = s.site() + "/" + s.rate() + "/" + s.hours();
            if (!key.equals(lastLava)) {
                sim.erupt(s.site(), s.rate());
                var f = sim.field();
                synchronized (f) {
                    f.step(Math.min(s.hours(), s.off()) * 3600);
                    if (s.off() < s.hours()) { sim.stopVent(); f.step((s.hours() - s.off()) * 3600); }
                }
                lastLava = key;
                r.clearEntry();
                if (s.aim() == 3) { sim.refreshSeaEntry(); r.warmEntry(300); }
            }
            sim.refreshLakeImage();
            sim.refreshGlow();
            r.warmFountain(40);
            var f = sim.field();
            double[] fit = com.dan.kilauea.ui.LavaPainter.fit(f, 300);
            tx = fit[0] + fit[2] / 2;
            tz = fit[1] + fit[2] / 2;
            if (s.aim() == 1) {
                // die Front: am weitesten vom Schlot entfernte fließende Zelle
                double[] best = {0, tx, tz};
                f.forEachCovered((x, z, g, rr, h, t, aa, u, fl, arr) -> {
                    double dd = Math.hypot(x - s.site().x(), z - s.site().z());
                    if (h > 0.05 && dd > best[0]) { best[0] = dd; best[1] = x; best[2] = z; }
                });
                tx = best[1];
                tz = best[2];
            } else if (s.aim() == 2) {
                tx = s.site().x();
                tz = s.site().z();
            } else if (s.aim() == 3) {
                sim.refreshSeaEntry();
                synchronized (sim.seaEntry) {
                    if (sim.seaEntry.count > 0) { tx = sim.seaEntry.entries[0]; tz = sim.seaEntry.entries[1]; }
                    System.out.println("  " + sim.seaEntry.summary());
                }
            }
        } else {
            sim.reset();
            sim.refreshLakeImage();
            sim.refreshGlow();
            tx = sim.lake().cx;
            tz = sim.lake().cz;
        }
        r.camera.lookAt(tx, Math.max(0, com.dan.kilauea.terrain.Ground.height(tx, tz)), tz, v.yaw(), v.pitch(), v.dist());
        r.camera.snap();
        // ein paar Bilder, damit alle Blöcke oben sind
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(110); } catch (InterruptedException e) { }
            d.display();
        }
        var st = sim.field() != null ? sim.field().stats(tx, tz) : null;
        System.out.println("  Lava: " + (st != null ? st : "kein Strom") + " · " + sim.lakeSummary());
    }

    private static String lastLava;

    /** Schnittansicht: Magma vorbereiten, Schnitt legen, Kamera davor. */
    static void section(Renderer r, GLOffscreenAutoDrawable d, String name) {
        r.magmaSpeed = 600;
        r.sectionMode = name.contains("suedwest") ? com.dan.kilauea.render.SectionRenderer.Mode.SOUTHWEST
                : name.contains("nordsued") ? com.dan.kilauea.render.SectionRenderer.Mode.NORTH_SOUTH : com.dan.kilauea.render.SectionRenderer.Mode.EAST;
        if (name.startsWith("p6_gang")) {
            var m = r.magma;
            synchronized (m) {
                if (m.dikes().isEmpty()) {
                    m.nextRift = com.dan.kilauea.magma.Rift.EAST;
                    m.boost(20);
                    for (int i = 0; i < 24 * 200 && m.activeDike() == null; i++) m.step(3600);
                    m.boost(1);
                    m.step(1.2 * 86400);
                }
            }
        }
        d.display();
        double[] v = r.sectionView();
        if (name.endsWith("_nah")) {
            // näher an Gipfel und Gang
            r.camera.lookAt(r.magma.sc.x + (v[0] - r.magma.sc.x) * 0.35, -2500, r.magma.sc.z + (v[2] - r.magma.sc.z) * 0.35, v[3], 6, 24_000);
        } else if (name.contains("aufgeklappt")) {
            // schräg von oben über den herausgenommenen Block auf Gipfel und Schnitt
            double ax = r.magma.sc.x, az = r.magma.sc.z;
            double[] tip = com.dan.kilauea.magma.Rift.EAST.at(9000);
            r.camera.lookAt((ax + tip[0]) / 2, -2000, (az + tip[1]) / 2, v[3] - 50, 30, 30_000);
        } else r.camera.lookAt(v[0], v[1], v[2], v[3], v[4], v[5]);
        r.camera.snap();
        d.display();
        System.out.println("  Magma: " + r.magma.summary());
    }

    /** Liest das fertige Bild aus dem Bildspeicher des Offscreen-Drawables. */
    static final class Grab {
        static java.awt.image.BufferedImage read(GLOffscreenAutoDrawable d, int w, int h) {
            final java.awt.image.BufferedImage[] res = new java.awt.image.BufferedImage[1];
            d.invoke(true, a -> {
                GL4 gl = a.getGL().getGL4();
                java.nio.ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
                // das Bild steht im Zeichen-Bildspeicher (der Lese-Speicher bekommt es erst beim Tausch)
                int fb = a.getContext().getDefaultDrawFramebuffer();
                gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
                gl.glReadBuffer(fb == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
                gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
                gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                int[] row = new int[w];
                for (int y = 0; y < h; y++) {
                    int o = (h - 1 - y) * w * 4;
                    for (int x = 0; x < w; x++) {
                        int i = o + x * 4;
                        row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
                    }
                    img.setRGB(0, y, w, 1, row, 0, w);
                }
                res[0] = img;
                return true;
            });
            return res[0];
        }
    }

    private Standbild() { }
}
