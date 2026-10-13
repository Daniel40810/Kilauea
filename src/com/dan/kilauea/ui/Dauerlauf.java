package com.dan.kilauea.ui;

import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.render.SceneView;
import com.dan.kilauea.terrain.Place;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Phase 17: Dauerlauf von 10 Minuten. Die App führt sich selbst vor: Gipfel aus der Höhe, Rundflug, ein
 * Drehbuch, zu Fuß am Kīlauea Iki, Wärmebild, der zweite Rundflug, ein Tag im Zeitraffer und ein freier
 * Ausbruch. Jede Sekunde notiert sie Bilder/s, die Zeiten von Grafikkarte und Prozessor, das längste Bild,
 * den Speicher und die Zahl der Fäden. Am Ende stellt sie den Zustand von vorher wieder her und prüft die
 * Liste unten. Der Bericht steht in {@code logs/dauerlauf_JJJJ-MM-TT_HHMM.txt} (dazu eine CSV-Datei) und als
 * Eintrag im Protokoll.
 * <p>
 * Prüfliste [G, Schwellen gestaltet]:
 * <ol>
 * <li>keine Fehlermeldung im Protokoll und keine der Grafik</li>
 * <li>kein Hänger: das längste Bild kürzer als 500 ms, höchstens 3 % der Sekunden mit einem Bild über 100 ms</li>
 * <li>Bilder/s im Mittel mindestens 25, keine Sekunde unter 12</li>
 * <li>Speicher nach dem Aufräumen am Ende höchstens 200 MB über dem Anfang</li>
 * <li>Fäden am Ende höchstens 3 mehr als am Anfang</li>
 * <li>Drehbuch und Ausbruch zu Ende geführt, der Spieler wieder in Ruhe</li>
 * <li>mit Datenbank: das Protokoll erreicht sie ohne Fehler (ohne Datenbank übersprungen)</li>
 * </ol>
 */
public final class Dauerlauf {

    /** Ein Abschnitt des Laufs: Name, Dauer in Sekunden, was zu Beginn geschieht. */
    public record Schritt(String name, int sekunden, Consumer<Dauerlauf> beginn) { }

    /** Eine Sekunde des Laufs. */
    public record Messung(int t, int schritt, double fps, double gpuMs, double cpuMs, double laengstesMs, double speicherMb, int faeden, int fehler) { }

    /** Ein Punkt der Prüfliste. */
    public record Punkt(String name, boolean ok, String wert) {
        public String zeile() { return String.format(Locale.GERMAN, "  %-7s %s: %s", ok ? "ok" : "FEHLER", name, wert); }
    }

    /** Die zehn Minuten. */
    public static List<Schritt> plan() {
        return List.of(
                new Schritt("Gipfel aus der Höhe, kreisend", 60, d -> {
                    d.r.state.clock.set(d.r.state.clock.day(), 10.0);
                    d.r.camera.autoOrbit = false;
                    d.r.camera.lookAt(Place.KALUAPELE.x(), 1100, Place.KALUAPELE.z(), 200, 40, 7500);
                    if (d.view != null) d.view.setAutoOrbit(true); else d.r.camera.autoOrbit = true;
                }),
                new Schritt("Rundflug: Der Gipfel", 90, d -> {
                    if (d.view != null) d.view.setAutoOrbit(false); else d.r.camera.autoOrbit = false;
                    d.r.startTour(com.dan.kilauea.camera.Tour.byId("gipfel"));
                }),
                new Schritt("Drehbuch: Fontänen im Halemaʻumaʻu 2024/25", 120, d -> {
                    d.r.tour.stop();
                    d.r.player.cameraFollows = true;
                    d.r.player.start(com.dan.kilauea.script.Scripts.byId("2024"));
                }),
                new Schritt("Zu Fuß auf dem Boden des Kīlauea Iki", 60, d -> {
                    d.r.player.stop();
                    var sp = com.dan.kilauea.terrain.SummitPaths.WALK_SPOTS.get(3);
                    if (d.view != null) d.view.walkAt(sp.x(), sp.z(), sp.heading(), 0);
                    else d.r.camera.startWalk(sp.x(), sp.z(), sp.heading());
                    d.r.camera.kFwd = true;
                }),
                new Schritt("Wärmebild am Halemaʻumaʻu", 45, d -> {
                    d.r.camera.kFwd = false;
                    d.r.camera.stopWalk();
                    Place p = Place.HALEMAUMAU;
                    d.r.camera.autoOrbit = false;
                    d.r.camera.lookAt(p.x(), Math.max(0, com.dan.kilauea.terrain.Ground.height(p.x(), p.z())), p.z(), p.viewYaw, p.viewPitch, p.viewDist);
                    d.r.thermal = true;
                }),
                new Schritt("Rundflug: Riftzone bis zum Meer", 90, d -> {
                    d.r.thermal = false;
                    d.r.startTour(com.dan.kilauea.camera.Tour.byId("rift"));
                }),
                new Schritt("Ein Tag in 90 Sekunden über der Küste", 60, d -> {
                    d.r.tour.stop();
                    Place p = Place.HOLEI_PALI;
                    d.r.camera.autoOrbit = false;
                    d.r.camera.lookAt(p.x(), Math.max(0, com.dan.kilauea.terrain.Ground.height(p.x(), p.z())), p.z(), p.viewYaw, p.viewPitch, p.viewDist);
                    d.r.timeLapse = 960;
                }),
                new Schritt("Freier Ausbruch an der Ostriftzone", 75, d -> {
                    d.r.timeLapse = 0;
                    d.r.player.cameraFollows = true;
                    d.r.player.start(com.dan.kilauea.script.Scripts.free(com.dan.kilauea.magma.Rift.EAST, 18_000, 20, 300, 4));
                }));
    }

    /** Länge des ganzen Laufs in Sekunden. */
    public static int dauer() {
        int s = 0;
        for (Schritt x : plan()) s += x.sekunden();
        return s;
    }

    // ------------------------------------------------------------ Lauf

    private static volatile Dauerlauf aktiv;
    /** Für Prüfläufe: alle Abschnitte um diesen Faktor kürzer (-Dkilauea.dauerlaufFaktor=0.1); sonst 1. */
    /** Jede Sekunde eine Zeile auf die Konsole (-Dkilauea.dauerlaufKonsole=true). */
    static final boolean KONSOLE = Boolean.getBoolean("kilauea.dauerlaufKonsole");
    static final double FAKTOR = Math.max(0.01, Math.min(1.0, Double.parseDouble(System.getProperty("kilauea.dauerlaufFaktor", "1"))));

    /** Ob gerade ein Dauerlauf läuft. */
    public static boolean laeuft() { return aktiv != null; }

    /** Den laufenden Dauerlauf abbrechen (Bericht mit dem, was bis dahin gemessen ist). */
    public static void abbrechen() {
        Dauerlauf d = aktiv;
        if (d != null) d.ende(true);
    }

    final Renderer r;
    final SceneView view;
    private final ControlPanel panel;
    private final Consumer<String> status;
    private final List<Schritt> plan = plan();
    private final List<Messung> mess = new ArrayList<>();
    private final LocalDateTime beginn = LocalDateTime.now();
    private com.dan.kilauea.db.Zustand vorher;
    private javax.swing.Timer timer;
    private int t, schritt = -1, schrittEnde;
    private long startNs;
    private double speicherAnfang;
    private int faedenAnfang, fehlerAnfang;
    private boolean spielerRuhig = true;

    private Dauerlauf(SceneView view, ControlPanel panel, Consumer<String> status) {
        this.view = view;
        this.r = view.renderer();
        this.panel = panel;
        this.status = status;
    }

    /**
     * Startet den Lauf (Ereignis-Faden). status bekommt jede Sekunde eine Zeile; am Ende den Pfad des Berichts.
     * Läuft schon einer, passiert nichts.
     */
    public static void starten(SceneView view, ControlPanel panel, Consumer<String> status) {
        if (aktiv != null) return;
        Dauerlauf d = new Dauerlauf(view, panel, status);
        aktiv = d;
        d.los();
    }

    private void los() {
        vorher = panel != null ? panel.erfassen() : null;
        speicherAnfang = speicherNachAufraeumen();
        faedenAnfang = ManagementFactory.getThreadMXBean().getThreadCount();
        fehlerAnfang = com.dan.kilauea.db.Dienst.fehlerZahl();
        r.takeWorstFrameMs();
        com.dan.kilauea.db.Dienst.ereignis("INFO", "DAUERLAUF", "Start: " + dauer() / 60 + " Minuten, Qualität " + r.quality().label, false);
        startNs = System.nanoTime();
        timer = new javax.swing.Timer(1000, e -> tick());
        timer.start();
        tick();
    }

    private void tick() {
        if (aktiv != this) return;
        // Zeit nach der Wanduhr: kommt der Ereignis-Faden einmal nicht nach, verschiebt sich der Plan nicht
        int jetzt = (int) ((System.nanoTime() - startNs) / 1_000_000_000L);
        if (jetzt < t) return;
        t = jetzt;
        if (t >= schrittEnde) {
            schritt++;
            if (schritt >= plan.size()) { ende(false); return; }
            Schritt s = plan.get(schritt);
            schrittEnde = t + Math.max(3, (int) Math.round(s.sekunden() * FAKTOR));
            try { s.beginn().accept(this); } catch (RuntimeException ex) {
                com.dan.kilauea.db.Dienst.ereignis("FEHLER", "DAUERLAUF", s.name() + ": " + ex, false);
            }
        }
        if (t > 0) {
            Runtime rt = Runtime.getRuntime();
            mess.add(new Messung(t, schritt, r.fps(), r.gpuMs(), r.cpuMs(), r.takeWorstFrameMs(),
                    (rt.totalMemory() - rt.freeMemory()) / 1048576.0, ManagementFactory.getThreadMXBean().getThreadCount(),
                    com.dan.kilauea.db.Dienst.fehlerZahl() - fehlerAnfang));
        } else r.takeWorstFrameMs();
        Schritt s = plan.get(Math.max(0, schritt));
        int gesamt = (int) Math.round(dauer() * FAKTOR);
        String zeile = String.format(Locale.GERMAN, "Dauerlauf %d:%02d von %d:%02d · %d/%d %s · %.0f Bilder/s",
                t / 60, t % 60, gesamt / 60, gesamt % 60, schritt + 1, plan.size(), s.name(), r.fps());
        if (status != null) status.accept(zeile);
        if (KONSOLE) System.out.println(zeile);
        t++;   // dieselbe Sekunde nicht zweimal messen
    }

    private void ende(boolean abgebrochen) {
        if (aktiv != this) return;
        aktiv = null;
        if (timer != null) timer.stop();
        // aufräumen: Rundflug, Drehbuch, Gehen, Wärmebild, Zeitraffer; dann der Zustand von vorher
        r.tour.stop();
        if (r.player.state() != com.dan.kilauea.script.Player.State.IDLE) r.player.stop();
        r.camera.kFwd = false;
        r.thermal = false;
        r.timeLapse = 0;
        // der Spieler bekommt etwas Zeit, das Drehbuch zurückzunehmen, dann zählt, ob er in Ruhe ist
        javax.swing.Timer nach = new javax.swing.Timer(2500, e -> {
            spielerRuhig = r.player.state() == com.dan.kilauea.script.Player.State.IDLE;
            if (panel != null && vorher != null) panel.anwenden(vorher, false);
            double speicherEnde = speicherNachAufraeumen();
            int faedenEnde = ManagementFactory.getThreadMXBean().getThreadCount();
            List<Punkt> liste = pruefliste(mess, speicherAnfang, speicherEnde, faedenAnfang, faedenEnde, spielerRuhig,
                    r.error(), com.dan.kilauea.db.Dienst.bereit(), abgebrochen);
            File f = schreiben(liste, speicherEnde, faedenEnde, abgebrochen);
            long fehler = liste.stream().filter(p -> !p.ok()).count();
            String kurz = (abgebrochen ? "abgebrochen nach " + t + " s; " : "") + (fehler == 0 ? "alles in Ordnung" : fehler + " Punkte offen")
                    + String.format(Locale.GERMAN, " · im Mittel %.0f Bilder/s", mittel(mess, Messung::fps));
            com.dan.kilauea.db.Dienst.ereignis(fehler == 0 ? "INFO" : "WARNUNG", "DAUERLAUF", kurz + (f != null ? " · " + f.getName() : ""), false);
            if (status != null) status.accept("Dauerlauf: " + kurz + (f != null ? " · Bericht " + f.getPath() : ""));
            if (KONSOLE) System.out.println("Dauerlauf: " + kurz + (f != null ? " · Bericht " + f.getPath() : ""));
        });
        nach.setRepeats(false);
        nach.start();
    }

    // ------------------------------------------------------------ Prüfliste

    /** Die Prüfliste aus den Messungen (ohne Fenster prüfbar). */
    public static List<Punkt> pruefliste(List<Messung> m, double speicherAnfang, double speicherEnde, int faedenAnfang, int faedenEnde,
                                         boolean spielerRuhig, String grafikFehler, boolean mitDatenbank, boolean abgebrochen) {
        List<Punkt> l = new ArrayList<>();
        int fehler = 0;
        for (Messung x : m) fehler = Math.max(fehler, x.fehler());   // die Zahl wächst; das Größte zählt
        l.add(new Punkt("Keine Fehler", fehler == 0 && grafikFehler == null,
                fehler + " Fehler im Protokoll" + (grafikFehler != null ? ", Grafik: " + grafikFehler : "")));
        double laengstes = 0;
        int lang = 0, gezaehlt = 0;
        for (Messung x : m) {
            gezaehlt++;
            laengstes = Math.max(laengstes, x.laengstesMs());
            if (x.laengstesMs() > 100) lang++;
        }
        double anteil = gezaehlt == 0 ? 0 : lang / (double) gezaehlt;
        l.add(new Punkt("Kein Hänger", laengstes < 500 && anteil <= 0.03,
                String.format(Locale.GERMAN, "längstes Bild %.0f ms, %d von %d Sekunden mit einem Bild über 100 ms", laengstes, lang, gezaehlt)));
        double fpsMittel = mittel(m, Messung::fps), fpsMin = Double.MAX_VALUE;
        for (Messung x : m) if (x.t() > 2) fpsMin = Math.min(fpsMin, x.fps());
        if (fpsMin == Double.MAX_VALUE) fpsMin = 0;
        l.add(new Punkt("Bilder/s", fpsMittel >= 25 && fpsMin >= 12,
                String.format(Locale.GERMAN, "Mittel %.1f, schlechteste Sekunde %.1f", fpsMittel, fpsMin)));
        l.add(new Punkt("Speicher", speicherEnde - speicherAnfang <= 200,
                String.format(Locale.GERMAN, "%.0f MB am Anfang, %.0f MB am Ende (nach dem Aufräumen)", speicherAnfang, speicherEnde)));
        l.add(new Punkt("Fäden", faedenEnde - faedenAnfang <= 3, faedenAnfang + " am Anfang, " + faedenEnde + " am Ende"));
        l.add(new Punkt("Drehbuch zurück", spielerRuhig, spielerRuhig ? "Spieler in Ruhe" : "Spieler läuft noch"));
        l.add(new Punkt("Datenbank", true, mitDatenbank ? (fehler == 0 ? "Protokoll ohne Fehler geschrieben" : "siehe Fehler")
                : "ohne Datenbank (übersprungen)"));
        if (abgebrochen) l.add(new Punkt("Vollständig", false, "abgebrochen"));
        return l;
    }

    static double mittel(List<Messung> m, java.util.function.ToDoubleFunction<Messung> f) {
        double s = 0;
        int n = 0;
        for (Messung x : m) if (x.t() > 2) { s += f.applyAsDouble(x); n++; }
        return n == 0 ? 0 : s / n;
    }

    private static double speicherNachAufraeumen() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 2; i++) {
            System.gc();
            try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
    }

    // ------------------------------------------------------------ Bericht

    private File schreiben(List<Punkt> liste, double speicherEnde, int faedenEnde, boolean abgebrochen) {
        try {
            File dir = com.dan.kilauea.db.Dienst.logOrdner();
            Files.createDirectories(dir.toPath());
            String stamm = "dauerlauf_" + beginn.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"));
            File txt = new File(dir, stamm + ".txt"), csv = new File(dir, stamm + ".csv");
            Files.writeString(txt.toPath(), bericht(liste, abgebrochen), StandardCharsets.UTF_8);
            StringBuilder c = new StringBuilder("t_s;schritt;bilder_s;gpu_ms;cpu_ms;laengstes_bild_ms;speicher_mb;faeden;fehler\n");
            for (Messung x : mess)
                c.append(String.format(Locale.ROOT, "%d;%d;%.1f;%.2f;%.2f;%.0f;%.0f;%d;%d%n", x.t(), x.schritt() + 1, x.fps(), x.gpuMs(), x.cpuMs(),
                        x.laengstesMs(), x.speicherMb(), x.faeden(), x.fehler()));
            Files.writeString(csv.toPath(), c.toString(), StandardCharsets.UTF_8);
            return txt;
        } catch (Exception ex) {
            com.dan.kilauea.db.Dienst.ereignis("WARNUNG", "DAUERLAUF", "Bericht nicht geschrieben: " + ex, false);
            return null;
        }
    }

    /** Der Bericht als Text: Kopf, je Abschnitt Mittelwerte, dann die Prüfliste. */
    String bericht(List<Punkt> liste, boolean abgebrochen) {
        StringBuilder b = new StringBuilder();
        b.append("Kīlauea · Dauerlauf · ").append(beginn.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append('\n');
        b.append("Grafik: ").append(r.glInfo()).append(" · Qualität ").append(r.quality().label)
                .append(" · Datenbank: ").append(com.dan.kilauea.db.Dienst.kurz()).append('\n');
        b.append(String.format(Locale.GERMAN, "Dauer %d s%s%n%n", mess.size(), abgebrochen ? " (abgebrochen)" : ""));
        b.append(String.format(Locale.GERMAN, "%-46s %8s %8s %8s %10s%n", "Abschnitt", "Bilder/s", "GPU ms", "CPU ms", "längstes"));
        for (int i = 0; i < plan.size(); i++) {
            final int k = i;
            List<Messung> m = mess.stream().filter(x -> x.schritt() == k).toList();
            if (m.isEmpty()) continue;
            double l = m.stream().mapToDouble(Messung::laengstesMs).max().orElse(0);
            b.append(String.format(Locale.GERMAN, "%-46s %8.1f %8.1f %8.1f %8.0f ms%n", (i + 1) + " " + plan.get(i).name(),
                    m.stream().mapToDouble(Messung::fps).average().orElse(0), m.stream().mapToDouble(Messung::gpuMs).average().orElse(0),
                    m.stream().mapToDouble(Messung::cpuMs).average().orElse(0), l));
        }
        b.append("\nPrüfliste\n");
        for (Punkt p : liste) b.append(p.zeile()).append('\n');
        long offen = liste.stream().filter(p -> !p.ok()).count();
        b.append('\n').append(offen == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + offen + " Punkte offen").append('\n');
        return b.toString();
    }
}
