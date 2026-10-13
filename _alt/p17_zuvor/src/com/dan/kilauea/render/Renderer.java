package com.dan.kilauea.render;

import com.dan.kilauea.atmos.Atmosphere;
import com.dan.kilauea.atmos.SunClock;
import com.dan.kilauea.camera.OrbitCamera;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;
import com.dan.kilauea.terrain.TerrainBuilder;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte, Grundlage aus Kneiphof. Je Bild:
 * <ol>
 * <li>Schattenkaskaden der Sonne (3 Karten, Tiefe 32F; das Gelände wirft Schatten).</li>
 * <li>Gelände als Viererbaum mit Detailstufen ({@link TerrainLod}): Auswahl je Bild auf der CPU, Höhen
 * und Bodenart aus Texturen im Vertex-Shader.</li>
 * <li>Himmelstabelle (256 × 128): Einfachstreuung für Sonne und Mond, vorbelichtet.</li>
 * <li>Szene in einen HDR-Puffer (RGBA16F, umgekehrte 32F-Tiefe): Himmel, Gelände; Kopie von Farbe und
 * Tiefe; dann das Meer (Blick durch das Wasser auf den Grund, Brandung nach der Wassertiefe).</li>
 * <li>Belichtungsmessung, Bloom, Nachbearbeitung (Filmkurve), Kantenglättung (FXAA).</li>
 * </ol>
 * Gelände, Schatten und Meer rechnen die Erdkrümmung (d²/2R) gleich ein. Im Bildlauf werden keine
 * Dateien gelesen und keine Datenbank gefragt.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    static final int BLOOM_LEVELS = 6;

    public final OrbitCamera camera = new OrbitCamera();
    public final SceneState state = new SceneState();
    public final Cascades cascades = new Cascades();
    /** Zeitraffer: Faktor gegenüber der Uhr (0 = Uhr steht). */
    public volatile double timeLapse = 0;
    public volatile double renderScale = 1.0;
    public volatile boolean shadows = true, bloom = true, fxaa = true, showOcean = true, curvature = true;
    /** Prüfbild: Detailstufen des Geländes einfärben. */
    public volatile boolean showLod;
    /** Prüfbild: Alter der Lava einfärben (rot jünger als 10 Jahre … braun älter als 10 000). */
    public volatile boolean showAge;
    public volatile double detail = 1.0;
    public volatile boolean autoScale;
    private volatile Quality quality = Quality.HOCH;
    private long lastScaleNanos;

    private final Shader skyLut = new Shader("Himmelstabelle", null, "fullscreen.vert", "skylut.frag");
    private final Shader sky = new Shader("Himmel", null, "fullscreen.vert", "sky.frag");
    private final Shader terrain = new Shader("Gelände", null, "terrain.vert", "terrain.frag");
    private final Shader shadowDepth = new Shader("Schattentiefe", null, "shadow_depth.vert", "shadow_depth.frag");
    private final Shader terrainDepth = new Shader("Geländeschatten", new String[]{"DEPTH_ONLY"}, "terrain.vert", "shadow_depth.frag");
    private final Shader ocean = new Shader("Meer", null, "ocean.vert", "ocean.frag");
    private final Shader floraS = new Shader("Pflanzen", null, "flora.vert", "flora.frag");
    private final Shader roadS = new Shader("Straßen", null, "road.vert", "road.frag");
    private final Shader tubeS = new Shader("Lavaröhre", null, "tube.vert", "tube.frag");
    private final Shader rockS = new Shader("Fels", null, "rock.vert", "rock.frag");
    private final Shader sectionS = new Shader("Schnitt", null, "section.vert", "section.frag");
    private final Shader rockDepth = new Shader("Felsschatten", new String[]{"DEPTH_ONLY"}, "rock.vert", "shadow_depth.frag");
    private final Shader groundS = new Shader("Bodendecke", null, "ground.vert", "ground.frag");
    private final Shader steamS = new Shader("Dampf", null, "steam.vert", "steam.frag");
    private final Shader lakeS = new Shader("Lavasee", null, "lake.vert", "lake.frag");
    private final Shader fountainS = new Shader("Fontäne", null, "fountain.vert", "fountain.frag");
    private final Shader pillowS = new Shader("Kissenlava", null, "pillow.vert", "pillow.frag");
    private final Shader underS = new Shader("Unter Wasser", null, "fullscreen.vert", "underwater.frag");
    private final Shader cloudS = new Shader("Wolken", null, "fullscreen.vert", "clouds.frag");
    private final Shader compS = new Shader("Zusammenbau", null, "fullscreen.vert", "composite.frag");
    private final Shader floraDepth = new Shader("Pflanzenschatten", new String[]{"DEPTH_ONLY"}, "flora.vert", "shadow_depth.frag");
    private final Shader lum = new Shader("Messung", null, "fullscreen.vert", "lum.frag");
    private final Shader bloomDown = new Shader("Bloom ab", null, "fullscreen.vert", "bloom_down.frag");
    private final Shader bloomUp = new Shader("Bloom auf", null, "fullscreen.vert", "bloom_up.frag");
    private final Shader post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag");
    private final Shader aa = new Shader("FXAA", null, "fullscreen.vert", "fxaa.frag");
    /** Phase 12: Bild über Bild legen (Dampf aus halber Auflösung, Infotafel). */
    private final Shader overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag");
    /** Phase 14: Wärmebild und die Teilchen der Fontänen als Temperatur. */
    private final Shader thermalS = new Shader("Wärmebild", null, "fullscreen.vert", "thermal.frag");
    private final Shader fountainHeat = new Shader("Fontäne (Wärme)", new String[]{"THERMAL"}, "fountain.vert", "fountain.frag");
    /** Phase 15: Figuren (Nēnē, Vögel, Hubschrauber, Wanderer, Autos), ihre Schatten und ihre Wärme. */
    private final Shader lifeS = new Shader("Figuren", null, "life.vert", "life.frag");
    private final Shader lifeDepth = new Shader("Figurenschatten", new String[]{"DEPTH_ONLY"}, "life.vert", "shadow_depth.frag");
    private final Shader lifeHeat = new Shader("Figuren (Wärme)", new String[]{"THERMAL"}, "life.vert", "life.frag");
    private final Shader[] all = {skyLut, sky, terrain, shadowDepth, terrainDepth, floraS, floraDepth, roadS, tubeS, rockS, rockDepth, sectionS, groundS, steamS, lakeS, fountainS, pillowS, underS, cloudS, compS, ocean, lum, bloomDown, bloomUp, post, aa, overlay, thermalS, fountainHeat, lifeS, lifeDepth, lifeHeat};

    private int emptyVao;
    private final Gpu.Mesh oceanMesh = new Gpu.Mesh();
    final TerrainLod lod = new TerrainLod();
    /** Obergrenze der Belichtung bei glühender Lava (siehe step) [G]. */
    static final double LAVA_EXPOSURE = 25.0;
    /** Lava auf der Grafikkarte (Phase 8). */
    final LavaLayer lavaLayer = new LavaLayer();

    /** Lava im Bild zeigen (Phase 8); aus: Gelände, Pflanzen und Straßen wie ohne Lava, das Modell rechnet weiter. */
    public void showLava(boolean on) { lavaLayer.show = on; }
    final LakeRenderer lakeR = new LakeRenderer();
    final FountainRenderer fountainR = new FountainRenderer();
    /** Meereseintritt (Phase 10): Glutbrocken und Lavafälle, Kissenlava mit Blasen. */
    final com.dan.kilauea.lava.Littoral littoral = new com.dan.kilauea.lava.Littoral();
    final FountainRenderer littoralR = new FountainRenderer(littoral, com.dan.kilauea.lava.Littoral.MAX);
    final PillowRenderer pillowR = new PillowRenderer();
    /** Passatwetter (Phase 11): Wolken, Regen, Vog; das Wetter an der Kamera führt Regen, Nässe, Regenbogen, Nebel. */
    public final com.dan.kilauea.atmos.Weather weather = new com.dan.kilauea.atmos.Weather();
    final CloudLayer clouds = new CloudLayer();
    private long frameNo;
    private final float[] entryU = new float[32];
    private int entryN;
    private double lastSurge = -1;
    final FloraRenderer flora = new FloraRenderer();

    // ------------------------------------------------------------------ Phase 15: Leben und Klang
    /** Die Figuren der Szene, ohne Grafik; jede Gruppe einzeln schaltbar. */
    public final com.dan.kilauea.life.Life life = new com.dan.kilauea.life.Life(2026);
    /** Was die Figuren je Bild von der Szene wissen (Uhr, Kamera, Sperrzonen). */
    public final com.dan.kilauea.life.World world = new com.dan.kilauea.life.World();
    final LifeRenderer lifeR = new LifeRenderer();
    /** Der Klang (eigener Faden; startet die App). */
    public final com.dan.kilauea.sound.SoundEngine sound = new com.dan.kilauea.sound.SoundEngine();
    private final SoundSampler soundSampler = new SoundSampler();
    private double[][] lastFountains = new double[0][];

    /** Figuren anhalten (Standbilder: die Szene steht, die Figuren bleiben, wo sie sind). */
    public volatile boolean lifeFrozen;

    /** Für Standbilder und Prüfung: die Figuren einige Sekunden in kleinen Schritten vorlaufen lassen. */
    public void warmLife(double seconds) {
        for (double t = 0; t < seconds; t += 1 / 15.0) {
            fillWorld();
            life.update(1 / 15.0, world);
        }
    }

    /** Sekunden seit dem Start (Uhr der Wellen und des Dampfes). */
    public double simTime() { return simTime; }

    /** Figuren im Bild und ihre Dreiecke. */
    public String lifeStats() {
        return String.format(Locale.GERMAN, "%,d Figuren, %,d Dreiecke", lifeR.figures, lifeR.triangles);
    }

    /** Die Welt der Figuren aus der Szene füllen: Uhr, Kamera, Wind, Regen, Sperrzonen um Fontänen und heiße Lava. */
    void fillWorld() {
        var w = world;
        w.hour = state.clock.hour();
        w.day = state.clock.day();
        w.sunElevation = state.clock.elevationDeg;
        w.camX = camera.ex; w.camY = camera.ey; w.camZ = camera.ez;
        double[] wv = state.windVector();
        w.windX = wv[0]; w.windZ = wv[1];
        w.rain = state.rain;
        w.clearHazards();
        w.script = player.state() != com.dan.kilauea.script.Player.State.IDLE;
        if (!lavaLayer.show) return;
        double best = -1;
        for (double[] q : lastFountains) {
            double x = 0.5 * (q[0] + q[2]), z = 0.5 * (q[1] + q[3]), len = Math.hypot(q[2] - q[0], q[3] - q[1]);
            // Sperrzone um eine Fontäne: 600 m + 1,5 × Höhe, 0,8 bis 1,5 km, dazu die halbe Spalte [G nach den Sperrungen des Parks]
            w.hazard(x, z, Math.max(800, Math.min(1500, 600 + 1.5 * q[5])) + 0.5 * len);
            if (q[5] > best) { best = q[5]; w.ventX = x; w.ventY = q[4]; w.ventZ = z; }
        }
        double[] h = lava.hot;
        for (int i = 0; i < Math.min(8, lava.hotCount); i++) w.hazard(h[3 * i], h[3 * i + 1], 300);
    }
    final TubeRenderer tube = new TubeRenderer();
    final GroundCoverRenderer groundCover = new GroundCoverRenderer();
    final SteamRenderer steam = new SteamRenderer();
    /** Meer vor der Küste und Gischt (Phase 9). */
    final SeaLayer sea = new SeaLayer();
    final RockRenderer rocks = new RockRenderer();
    final SectionRenderer section = new SectionRenderer();
    /** Das Magmasystem (Phase 6), ohne Grafik; der Schnitt zeigt es. */
    public final com.dan.kilauea.magma.MagmaSystem magma = new com.dan.kilauea.magma.MagmaSystem(2026);
    /** Das Lava-Modell (Phase 7): Strom und Lavasee in einem eigenen Thread; der See folgt dem Druck unter dem Halemaʻumaʻu. */
    public final com.dan.kilauea.lava.LavaSim lava = new com.dan.kilauea.lava.LavaSim(() -> { synchronized (magma) { return magma.hmm.pressure(); } });
    /** Simulationssekunden des Magmas je Sekunde Wanduhr (0 = steht). */
    public volatile double magmaSpeed = 3600;
    /** Phase 12: Bühne und Spieler der Drehbücher; solange einer läuft, rechnet er das Magma. */
    public final com.dan.kilauea.script.Stage stage = new com.dan.kilauea.script.Stage(lava, magma);
    private volatile boolean magmaByScript;
    public final com.dan.kilauea.script.Player player = new com.dan.kilauea.script.Player(stage, new ScriptHost());
    /** Phase 13: Rundflüge. */
    public final com.dan.kilauea.camera.TourPlayer tour = new com.dan.kilauea.camera.TourPlayer();
    /** Kinobalken immer zeigen (sonst nur während eines Rundflugs, wenn {@link #barsOnTour}). */
    public volatile boolean bars, barsOnTour = true;
    private double barsNow;

    /** Kinobalken sofort auf ihren Sollwert setzen (für Standbilder). */
    public void settleBars() { barsNow = bars || (barsOnTour && tour.active()) ? 1 : 0; }
    /** Höhe eines Kinobalkens bei voller Breite 2,35 : 1 auf 16 : 10 (Bruchteil der Bildhöhe). */
    static final double BAR = 0.12;
    /** Infotafel der Drehbücher im Bild zeigen. */
    public volatile boolean showCaption = true;
    final CaptionOverlay caption = new CaptionOverlay();
    /** Dampf, Gischt und Asche in halber Auflösung rechnen (Phase 12; das Statusbild zu Phase 11 zeigte 8,5 ms nah an der Fahne). */
    public volatile boolean halfResSteam = !Boolean.getBoolean("kilauea.dampfVoll");
    private int steamTex, steamFbo, steamW, steamH;
    private volatile boolean wantSectionView;

    // ------------------------------------------------------------------ Phase 14: Messnetz und Wissen
    /** Das Messnetz: Neigung, GPS, Seismograf, SO₂ aus dem Modell. */
    public final com.dan.kilauea.monitor.Monitor monitor = new com.dan.kilauea.monitor.Monitor(magma, lava);
    /** Tafel des Messnetzes im Bild: 0 bei Drehbüchern, 1 immer, 2 aus. */
    public volatile int monitorMode;
    final HudOverlay monitorHud = new HudOverlay(), thermalHud = new HudOverlay();
    /** Wärmebild statt Farbbild (Taste T). */
    public volatile boolean thermal;
    private int heatTex, heatFbo, probeTex, probeFbo, probePbo;
    private long probeFence;
    private volatile double probeT = Double.NaN, probeKind;
    /** Einheit der Wolken im Wärmebild (5 gehört dort dem Gelände). */
    static final int THERMAL_CLOUD_UNIT = 18;
    /** Infotafel zu einem Ort oder zum Boden unter dem Blick (bis sie abläuft oder der Blick weit weg ist). */
    private volatile com.dan.kilauea.script.Player.Caption info;
    private volatile long infoUntil;
    private volatile double infoX, infoZ;
    /** Rundflüge am Morgen (8 Uhr): Uhr beim Start setzen und am Ende zurückholen (Statusbild Phase 13). */
    public volatile boolean tourMorning = true;
    /** Beim Hinfliegen zu einem Ort dessen Tafel zeigen. */
    public volatile boolean placeInfo = true;
    private double[] tourClock;
    private boolean tourWas;

    /** Rundflug starten; am Morgen, wenn so eingestellt. */
    public void startTour(com.dan.kilauea.camera.Tour t) {
        if (tourMorning && tourClock == null && !magmaByScript) {
            tourClock = new double[]{state.clock.day(), state.clock.hour(), timeLapse};
            state.clock.set(state.clock.day(), 8.0);
            timeLapse = 0;
        }
        tour.start(t);
    }

    /** Eine Tafel aus dem Wissen zeigen (Ort oder Boden), 40 s lang oder bis der Blick weit wegfliegt. */
    public void showInfo(com.dan.kilauea.monitor.Lexicon.Info in, double x, double z) {
        String eb = in.kind().equals("Ort") ? "Ort" : "Was liegt hier?";
        info = new com.dan.kilauea.script.Player.Caption(eb, 0, 0, in.title(), in.line(), in.text(), 1,
                "Auskunft · " + com.dan.kilauea.monitor.Prov.legend(), false, com.dan.kilauea.script.Player.State.PLAYING, 0);
        infoX = x;
        infoZ = z;
        infoUntil = System.nanoTime() + 40_000_000_000L;
    }

    /** Was liegt unter dem Blick? (Taste I) */
    public com.dan.kilauea.monitor.Lexicon.Info groundInfo() {
        return com.dan.kilauea.monitor.Lexicon.ground(camera.targetX(), camera.targetZ(), lava.field());
    }

    public void clearInfo() { info = null; }

    /** Messung im Kreuz des Wärmebilds (°C) oder NaN, und was dort liegt. */
    public com.dan.kilauea.monitor.Reading thermalProbe() {
        double t = probeT;
        return Double.isNaN(t) ? null : com.dan.kilauea.monitor.Thermal.probe(t, com.dan.kilauea.monitor.Thermal.Kind.of(probeKind));
    }

    public com.dan.kilauea.monitor.Reading thermalWhat() {
        double t = probeT;
        return Double.isNaN(t) ? null : com.dan.kilauea.monitor.Thermal.what(com.dan.kilauea.monitor.Thermal.Kind.of(probeKind), t);
    }

    /** Ob die Tafel des Messnetzes gerade im Bild steht. */
    public boolean monitorShown() {
        int m = monitorMode;
        return m == 1 || (m == 0 && player.state() != com.dan.kilauea.script.Player.State.IDLE);
    }

    /** Was die Drehbücher am Bild tun dürfen: Kamera, Uhr, Schnitt, Asche; und was am Ende zurückkommt. */
    private final class ScriptHost implements com.dan.kilauea.script.Player.Host {
        private boolean cameraMoved;

        @Override public void view(double x, double y, double z, double yawDeg, double pitchDeg, double dist) {
            camera.autoOrbit = false;
            camera.lookAt(x, y, z, yawDeg, pitchDeg, dist);
            cameraMoved = true;
        }
        @Override public void time(int doy, double h) {
            int day = doy > 0 ? doy : state.clock.day();
            if (h < 0) {
                // negativ: so viele Stunden nach Sonnenuntergang (für Drehbücher ohne festen Tag)
                SunClock c = new SunClock();
                double set = 18.5;
                for (double t = 16; t < 20.5; t += 1 / 60.0) {
                    c.set(day, t);
                    if (c.elevationDeg < -0.83) { set = t; break; }
                }
                h = set - h;
            }
            state.clock.set(day, h);
        }
        @Override public void section(String mode) { sectionMode = SectionRenderer.Mode.valueOf(mode); }
        @Override public void ash(double x, double z, double radius, int puffs) { steam.steam.ash(x, z, radius, puffs); }
        @Override public void lookAtSection() { if (player.cameraFollows) { wantSectionView = true; cameraMoved = true; } }
        @Override public void clearParticles() {
            fountainR.fountain.clear();
            clearEntry();
        }
        @Override public Object save() {
            cameraMoved = false;
            monitor.clear();
            return new double[]{state.clock.day(), state.clock.hour(), timeLapse, magmaSpeed, sectionMode.ordinal(),
                    camera.tx, camera.ty, camera.tz, Math.toDegrees(camera.yaw), Math.toDegrees(camera.pitch), camera.dist};
        }
        @Override public void restore(Object o) {
            if (!(o instanceof double[] v)) return;
            state.clock.set((int) v[0], v[1]);
            timeLapse = v[2];
            magmaSpeed = v[3];
            sectionMode = SectionRenderer.Mode.values()[(int) v[4]];
            if (cameraMoved) camera.lookAt(v[5], v[6], v[7], v[8], v[9], v[10]);
            cameraMoved = false;
            monitor.clear();
        }
        @Override public void magmaOwnedByScript(boolean owned) {
            magmaByScript = owned;
            if (owned) timeLapse = 0;   // die Drehbücher stellen die Uhrzeit selbst
        }
    }
    /** Lage des Schnitts (aus, Ostrift, Südwestrift, Nord–Süd). */
    public volatile SectionRenderer.Mode sectionMode = SectionRenderer.Mode.OFF;
    /** Felsbogen Hōlei vor dem Einsturz (September 2026) zeigen; sonst Stand heute. */
    public volatile boolean archIntact;
    /** Dampf und Gasfahnen zeigen. */
    public volatile boolean showSteam = true;
    final RoadRenderer roads = new RoadRenderer(0), dirtPaths = new RoadRenderer(1), pavedPaths = new RoadRenderer(2);
    /** Bäume und Baumfarne zeichnen. */
    public volatile boolean showFlora = true;
    private CompletableFuture<HeightGrid> pendingTerrain;

    // ------------------------------------------------------------------ Vorab bauen (Startbild)
    private static CompletableFuture<HeightGrid> preTerrain;
    private static volatile boolean vorabFertig;
    /** Projektordner (Höhendaten, Zwischenspeicher); vorgegeben ist das Arbeitsverzeichnis. */
    public static volatile java.io.File projectDir = new java.io.File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));

    /**
     * Startet Laden oder Bau des Geländes im Hintergrund, noch vor dem Fenster (das Startbild zeigt
     * den Fortschritt): Höhenraster aus dem Zwischenspeicher oder aus den Höhendaten im Projekt.
     */
    public static synchronized void vorab() {
        if (preTerrain != null) return;
        preTerrain = CompletableFuture.supplyAsync(() -> {
            HeightGrid g = TerrainBuilder.load(projectDir);
            TerrainBuilder.step = "Straßen im Park";
            com.dan.kilauea.terrain.ParkRoads.build(g);
            TerrainBuilder.step = "Detailkacheln";
            com.dan.kilauea.terrain.DetailTiles.use(com.dan.kilauea.terrain.DetailTiles.load(projectDir, g, n -> TerrainBuilder.step = "Detailkachel " + n));
            Ground.use(g);
            TerrainBuilder.step = "Lavaröhre Nāhuku";
            com.dan.kilauea.terrain.LavaTube.use(com.dan.kilauea.terrain.LavaTube.build());
            com.dan.kilauea.terrain.ParkRoads.buildTrails();
            com.dan.kilauea.camera.WalkWorld.install();
            vorabFertig = true;
            return g;
        });
    }

    /** Fortschritt des Vorab-Aufbaus, 0 bis 1. */
    public static double vorabFortschritt() {
        if (preTerrain == null) return 0;
        return vorabFertig ? 1 : Math.min(0.99, TerrainBuilder.progress);
    }

    public static String vorabText() { return vorabFertig ? "Die Insel steht." : "Im Hintergrund: " + TerrainBuilder.step; }

    private int lutTex, lutFbo, hdrTex, depthTex, hdrFbo, lumTex, lumFbo, ldrTex, ldrFbo, copyTex, copyDepth, copyFbo;
    private int shadowTex, shadowSize;
    private final int[] shadowFbo = new int[Cascades.COUNT];
    private final int[] bloomTex = new int[BLOOM_LEVELS], bloomFbo = new int[BLOOM_LEVELS], bloomW = new int[BLOOM_LEVELS], bloomH = new int[BLOOM_LEVELS];
    private int hdrW, hdrH, surfW, surfH;
    private boolean reversedZ, ready;
    private final boolean staticOcean = Boolean.getBoolean("kilauea.meerStatisch");
    private final boolean flatOcean = Boolean.getBoolean("kilauea.meerEinfach");
    private final boolean fixedOceanReflection = Boolean.getBoolean("kilauea.meerReflexionFest");
    private final boolean oceanWithoutSkyLut = Boolean.getBoolean("kilauea.meerOhneHimmelLut");
    private final boolean oceanWithoutAerial = Boolean.getBoolean("kilauea.meerOhneLuft");
    private boolean staticOceanInitialized;
    private boolean skyLutInitialized;
    private long lastNanos;
    private double simTime;
    private int frame;
    private final FloatBuffer lumRead = Buffers.newDirectFloatBuffer(4);
    private int lumPbo;
    private float[] farVP;
    private float farSplit, farTexel;
    private final double[] farEye = new double[2];
    private long lumFence;

    private String glInfo = "";
    private volatile String error;
    private double fps;
    private long statsNanos;
    private int statsFrames;
    private double gpuMs;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile boolean reloadShaders;
    private final int[] timer = new int[2];
    private int timerIdx;
    private boolean timerArmed;
    private int terrainNodes, terrainVerts;
    private volatile String terrainSource = "";
    public volatile boolean photoRequest;
    private volatile String photoMsg = "";
    private volatile long photoMsgUntil;
    /** Ordner für Fotos (F12); ohne Angabe das Arbeitsverzeichnis. */
    public volatile java.io.File photoDir;
    /** Phase 16: wird nach jedem gespeicherten Foto mit Dateiname und Größe gerufen (Protokoll). */
    public static volatile java.util.function.Consumer<String> photoHook;

    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public void requestShaderReload() { reloadShaders = true; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    /** Prozessorzeit je Bild in ms (gleitendes Mittel, „gesamt“). */
    public double cpuMs() { return cpuMs[0]; }
    public boolean terrainReady() { return lod.ready(); }
    /** Ob alle Pflanzen um die Kamera gesetzt sind (Standbilder warten darauf). */
    public boolean floraComplete() { return !showFlora || flora.field.complete(new double[]{camera.ex, camera.ey, camera.ez}); }
    public Quality quality() { return quality; }
    /** Pflanzen im Bild und ihre Dreiecke (für Prüfung und Standbilder). */
    public String floraStats() {
        return String.format(Locale.GERMAN, "%,d Pflanzen, %.2f Mio. Dreiecke, Bodendecke %,d Dreiecke", flora.field.shown, flora.triangles / 1e6, groundCover.triangles);
    }
    /** Woher die Höhen stammen (leer, solange das Gelände lädt). */
    public String terrainSource() { return terrainSource; }

    /** Qualitätsstufe setzen: Auflösung, Schattenkarten, Bloom, Kantenglättung, Feinrelief. */
    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        bloom = q.bloom;
        fxaa = q.fxaa;
        detail = q.detail;
        cascades.size = q.shadowSize;
        lod.lodRange = q.lodRange;
        switch (q) {
            case NIEDRIG -> flora.field.setRanges(350, 0, 25, 150);
            case MITTEL -> flora.field.setRanges(600, 130, 35, 200);
            case HOCH -> flora.field.setRanges(800, 200, 40, 240);
            case ULTRA -> flora.field.setRanges(1500, 350, 70, 400);
        }
    }

    // ------------------------------------------------------------------ Lebenslauf

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int major = d.getContext().getGLVersionNumber().getMajor(), minor = d.getContext().getGLVersionNumber().getMinor();
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("Kīlauea: " + vendor + " · " + renderer + " · " + version);
        if (staticOcean) System.out.println("Kīlauea: Meerdiagnose statisch (Wellen, Belichtung, Reflexionen und Schatten eingefroren).");
        if (flatOcean) System.out.println("Kīlauea: Einfarbiger Meer-Kontrolltest aktiv.");
        if (fixedOceanReflection) System.out.println("Kīlauea: Feste Wasserhimmelsreflexion als Kontrolltest aktiv.");
        if (oceanWithoutSkyLut) System.out.println("Kīlauea: Wasser-Kontrolltest ohne Himmels-LUT aktiv.");
        if (oceanWithoutAerial) System.out.println("Kīlauea: Wasser-Kontrolltest ohne Luftperspektive aktiv.");
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). Kīlauea braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        lutTex = Gpu.texture(gl, 256, 128, GL4.GL_RGBA16F, true);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        lutFbo = Gpu.fbo(gl, lutTex, 0);
        lumTex = Gpu.texture(gl, 128, 64, GL4.GL_RG16F, true);
        lumFbo = Gpu.fbo(gl, lumTex, 0);
        probeTex = Gpu.texture(gl, 1, 1, GL4.GL_RG32F, false);
        probeFbo = Gpu.fbo(gl, probeTex, 0);
        if (!makeShadowMaps(gl, cascades.size)) return;
        gl.glGenQueries(2, timer, 0);
        float[][] ring = OceanRings.build();
        oceanMesh.upload(gl, ring[0], OceanRings.indices(), new int[]{2});
        lod.init(gl);
        lod.lava = lavaLayer;
        flora.init(gl);
        groundCover.init(gl);
        steam.init(gl);
        sea.init(gl);
        fountainR.init(gl);
        littoralR.init(gl);
        pillowR.init(gl);
        clouds.init(gl);
        lakeR.init(gl, lava.lake());
        lifeR.init(gl);
        vorab();
        pendingTerrain = preTerrain;
        camera.snap();
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        ready = true;
    }

    private boolean makeShadowMaps(GL4 gl, int size) {
        int[] v = new int[1];
        if (shadowTex != 0) {
            gl.glDeleteFramebuffers(Cascades.COUNT, shadowFbo, 0);
            gl.glDeleteTextures(1, new int[]{shadowTex}, 0);
        }
        gl.glGenTextures(1, v, 0);
        shadowTex = v[0];
        shadowSize = size;
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, shadowTex);
        gl.glTexStorage3D(GL4.GL_TEXTURE_2D_ARRAY, 1, GL4.GL_DEPTH_COMPONENT32F, size, size, Cascades.COUNT);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameterfv(GL4.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_BORDER_COLOR, new float[]{1, 1, 1, 1}, 0);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
        for (int c = 0; c < Cascades.COUNT; c++) {
            gl.glGenFramebuffers(1, v, 0);
            shadowFbo[c] = v[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, shadowFbo[c]);
            gl.glFramebufferTextureLayer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, shadowTex, 0, c);
            gl.glDrawBuffer(GL.GL_NONE);
            gl.glReadBuffer(GL.GL_NONE);
            int st = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
            if (st != GL.GL_FRAMEBUFFER_COMPLETE) { fail("Schattenkarte unvollständig: 0x" + Integer.toHexString(st)); return false; }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        return true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Guter Blick auf den Schnitt: x, y, z, Blick, Neigung, Abstand (oder null, wenn er aus ist). */
    public double[] sectionView() {
        if (sectionMode == SectionRenderer.Mode.OFF) return null;
        return section.view(camera.ex, camera.ez);
    }

    /** Uhr und Kamera um dt weiterrechnen (vom Bildlauf aufgerufen; Standbilder rufen es mit festem dt). */
    public void step(double dt) {
        simTime += dt;
        if (!staticOcean && timeLapse > 0) state.clock.advance(dt * timeLapse / 3600.0);
        tour.update(dt, camera);
        if (!staticOcean) camera.update(dt);
        boolean wantBars = bars || (barsOnTour && tour.active());
        barsNow += ((wantBars ? 1 : 0) - barsNow) * (1 - Math.exp(-dt * 2.5));
        // glüht Lava in der Nähe, stellt sich das Auge auf die Glut ein: Belichtung höchstens so, dass frische
        // Lava (rund 0,55 Leuchtdichte bei 1 150 °C) hell bleibt, aber nicht weiß ausbrennt [G]
        double cap = Double.MAX_VALUE;
        com.dan.kilauea.lava.GlowMap gm = lava.glow;
        if (lavaLayer.show && !gm.empty) {
            double gx = gm.x0 + gm.size / 2, gz = gm.z0 + gm.size / 2;
            if (Math.hypot(camera.ex - gx, camera.ez - gz) - gm.size / 2 < 15_000) cap = LAVA_EXPOSURE;
        }
        state.maxExposure = cap;
        // Zeitraffer: das Licht ändert sich schneller, also auch das Auge (960-fach: rund viermal so schnell) [G]
        state.adaptBoost = timeLapse > 60 ? Math.sqrt(timeLapse / 60) : 1;
        if (!staticOcean || !staticOceanInitialized) {
            state.update(dt);
            staticOceanInitialized = true;
        }
        if (!magmaByScript) synchronized (magma) { magma.step(dt * magmaSpeed); }
        // Phase 12: Drehbuch weiterführen (Stichworte, Magma, Einsturz, Asche)
        if (lod.ready()) player.update(dt);
        // Phase 14: Messnetz mitschreiben; Rundflug am Morgen beenden; Tafel zum Ort ablaufen lassen
        if (lod.ready()) {
            monitor.passiveSO2 = stage.passiveSO2;
            monitor.sample(dt);
        }
        boolean ta = tour.active();
        if (tourWas && !ta && tourClock != null) {
            state.clock.set((int) tourClock[0], tourClock[1]);
            timeLapse = tourClock[2];
            tourClock = null;
        }
        tourWas = ta;
        if (info != null && (System.nanoTime() > infoUntil
                || (!camera.flying() && Math.hypot(camera.targetX() - infoX, camera.targetZ() - infoZ) > 5000))) info = null;
        long t0 = System.nanoTime();
        if (showSteam && lod.ready()) steam.steam.update(dt, state.windVector(), new double[]{camera.ex, camera.ey, camera.ez}, simTime);
        long tsea = System.nanoTime();
        if (lod.ready()) sea.step(dt, simTime, state, new double[]{camera.ex, camera.ey, camera.ez});
        cpu(CPU_SEA, tsea);
        cpu(CPU_STEAM, t0);
        // Fontäne über dem laufenden Schlot (Phase 8)
        long tf = System.nanoTime();
        double[][] srcs = lavaLayer.show ? lava.fountains() : new double[0][];
        lastFountains = srcs;
        fountainR.fountain.update(dt, srcs, state.windVector());
        stepEntry(dt);
        cpu(CPU_LAVA, tf);
        stepWeather(dt);
        // Phase 15: Figuren und Klang
        long tlife = System.nanoTime();
        if (Ground.grid() != null && !lifeFrozen) {
            fillWorld();
            life.update(dt, world);
        }
        soundSampler.sample(this, sound, dt);
        cpu(CPU_LIFE, tlife);
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (!ready) {
            gl.glClearColor(0.05f, 0.06f, 0.08f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        if (cascades.size != shadowSize && !makeShadowMaps(gl, cascades.size)) return;
        // GLJPanel und Offscreen zeichnen in einen Bildspeicher, der beim Tausch wechseln kann: jedes Bild neu fragen
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        if (timerArmed) {
            int[] avail = new int[1];
            gl.glGetQueryObjectiv(timer[timerIdx ^ 1], GL4.GL_QUERY_RESULT_AVAILABLE, avail, 0);
            if (avail[0] != 0) {
                long[] ns = new long[1];
                gl.glGetQueryObjecti64v(timer[timerIdx ^ 1], GL4.GL_QUERY_RESULT, ns, 0);
                gpuMs = gpuMs * 0.9 + ns[0] / 1e6 * 0.1;
            }
        }
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, timer[timerIdx]);

        long now = System.nanoTime();
        cpuSelect = 0;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);
        uploadIfReady(gl);

        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        cpuMs[CPU_SELECT] = cpuMs[CPU_SELECT] * 0.95 + cpuSelect / 1e6 * 0.05;
        cpu(CPU_TOTAL, now);
        timerIdx ^= 1;
        timerArmed = true;
        frame++;
        if (autoScale && now - lastScaleNanos > 400_000_000L && gpuMs > 0) {
            lastScaleNanos = now;
            if (gpuMs > 15.0) renderScale = Math.max(0.5, renderScale - 0.05);
            else if (gpuMs < 10.5) renderScale = Math.min(quality.renderScale, renderScale + 0.05);
        }
        stats(now, w, h);
    }

    /** Dampf vorlaufen lassen (Standbilder: die Fahne braucht Minuten, bis sie steht). */
    public void warmSteam(double seconds) {
        for (double t = 0; t < seconds; t += 0.5) steam.steam.update(0.5, state.windVector(), new double[]{camera.ex, camera.ey, camera.ez}, simTime + t);
    }

    /** Für Standbilder: Fontäne einschwingen lassen (Sekunden). */
    /** Brandung, Schaum vor der Küste und Gischt an (Phase 9); aus: nur die Dünung im Tiefen. */
    public void showSurf(boolean on) { sea.show = on; }

    /** Zustand des Meeres vor der Küste als Text (Bedienfeld, Standbilder). */
    public String seaStats() {
        var n = sea.nearshore();
        if (n == null) return "Meer vor der Küste: noch nicht gebaut";
        return String.format(Locale.GERMAN, "Küste: %d Uferpunkte, davon %d Klippe · Raster %.0f ms · Gischt %d Teilchen",
                n.coast.points.size(), n.coast.cliffs(), sea.buildMs, sea.spray.count());
    }

    /** Wetter (Phase 11): Karte nachführen, Wolken mit dem Wind, Regen und Nässe an der Kamera. */
    private void stepWeather(double dt) {
        if (staticOcean) {
            state.rain = state.wet = state.rainbow = state.mist = 0;
            return;
        }
        if (!weather.ready() && Ground.grid() != null && lod.ready()) weather.terrain(Ground.grid());
        if (!weather.ready()) return;
        // die Karte rechnet ein eigener Faden (rund 70 ms); Standbilder rechnen sie gleich hier
        double ws = state.windSpeed, wd = state.windDirDeg, hr = state.clock.hour();
        if (frozen) weather.update(ws, wd, hr);
        else if (!weatherBusy && weather.due(ws, wd, hr)) {
            weatherBusy = true;
            WEATHER_THREAD.execute(() -> {
                try { weather.update(ws, wd, hr); } finally { weatherBusy = false; }
            });
        }
        double[] wv = state.windVector();
        clouds.windX += wv[0] * dt;
        clouds.windZ += wv[1] * dt;
        if (clouds.show) {
            weather.camera(dt, camera.ex, camera.ey, camera.ez, state.clock.elevationDeg, state.clock.dir);
            state.rain = weather.rainHere;
            state.wet = weather.wetHere;
            state.rainbow = weather.rainbowHere;
            state.mist = 0.6 * weather.mistHere;
        } else state.rain = state.wet = state.rainbow = state.mist = 0;
    }

    private volatile boolean weatherBusy;
    private static final java.util.concurrent.ExecutorService WEATHER_THREAD = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Wetter");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Für Standbilder: Wetter an der Kamera einschwingen lassen (Sekunden), damit Regen und Nässe stehen. */
    public void warmWeather(double seconds) {
        for (double t = 0; t < seconds; t += 1) stepWeather(1);
    }

    /** Wolken, Regen und Vog an oder aus (Phase 11). */
    public void showWeather(boolean on) { clouds.show = on; }

    /** Wetter an der Kamera als Text. */
    public String weatherStats() {
        if (!weather.ready()) return "Wetter: Gelände lädt …";
        return String.format(Locale.GERMAN, "Wetter hier: Bedeckung %.0f %%, Regen %.0f %%, nass %.0f %%, Vog %.0f %%%s%s",
                100 * weather.at(camera.ex, camera.ez, 0), 100 * state.rain, 100 * state.wet, 100 * weather.at(camera.ex, camera.ez, 2),
                state.rainbow > 0.1 ? " · Regenbogen" : "", weather.mistHere > 0.2 ? " · in der Wolke" : "");
    }

    /** Meereseintritt (Phase 10): Dampf, Glutbrocken, Lavafälle, Kissenlava, Uniforms. */
    private void stepEntry(double dt) {
        var e = lava.seaEntry;
        double[] cam = {camera.ex, camera.ey, camera.ez};
        double[] strongest = null;
        boolean on = lavaLayer.show;
        synchronized (e) {
            entryN = on ? e.count : 0;
            for (int i = 0; i < entryN; i++) {
                entryU[4 * i] = (float) e.entries[5 * i];
                entryU[4 * i + 1] = (float) e.entries[5 * i + 1];
                entryU[4 * i + 2] = (float) e.entries[5 * i + 2];
                entryU[4 * i + 3] = 0;
            }
            if (entryN > 0) strongest = new double[]{e.entries[0], e.entries[1], e.entries[2]};
            steam.steam.entries(e.entries, 5, entryN);
            steam.steam.hot(lava.hot, on ? lava.hotCount : 0, state.rain);
            for (double[] ev : e.events)
                if (ev[0] > lastSurge) { steam.steam.surge(ev[1], ev[2], ev[3]); lastSurge = ev[0]; }
            littoral.update(dt, on ? e : null, state.windVector());
        }
        pillowR.pillows.update(dt, strongest, com.dan.kilauea.terrain.Ground::height, cam);   // auf dem Hang des Deltas
    }

    /** Für Standbilder: Meereseintritt einige Sekunden vorlaufen lassen (Kissen, Dampf, Glutbrocken). */
    public void warmEntry(double seconds) {
        for (double t = 0; t < seconds; t += 0.25) {
            stepEntry(0.25);
            steam.steam.update(0.25, state.windVector(), new double[]{camera.ex, camera.ey, camera.ez}, simTime + t);
        }
    }

    /** Beginn der Kolonie (x, z) oder null. */
    public double[] pillowAnchor() { return pillowR.pillows.anchor(); }

    /** Jüngstes Kissen (x, y, z) oder null. */
    public double[] pillowTip() { return pillowR.pillows.activeTip(); }

    /** Kissenlava, Blasen und Glutbrocken leeren (neuer Ausbruch, Standbild). */
    public void clearEntry() { pillowR.pillows.clear(); littoral.clear(); lastSurge = -1; }

    /** Zustand des Meereseintritts als Text. */
    public String entryStats() {
        String s;
        synchronized (lava.seaEntry) { s = lava.seaEntry.summary(); }
        var p = pillowR.pillows;
        return s + String.format(Locale.GERMAN, " · Kissenlava: %d Kissen, %d Knospungen in der letzten Minute, %d Blasen", p.count(), p.budsWithin(60), p.bubbleCount());
    }

    /** Kamera unter Wasser zur jüngsten Knospe der Kissenlava (oder zum Eintritt); false, wenn es keine gibt. */
    public boolean diveToPillows() {
        double[] t = pillowR.pillows.activeTip();
        if (t == null) return false;
        camera.diving = true;
        // hangabwärts der Knospe, Blick hangaufwärts auf die Kissen
        double[] dd = com.dan.kilauea.sea.Pillows.downhill(com.dan.kilauea.terrain.Ground::height, t[0], t[2]);
        camera.lookAt(t[0], t[1], t[2], Math.toDegrees(Math.atan2(dd[0], dd[1])), 12, 7);
        return true;
    }

    /** Für Standbilder: Meer vor der Küste sofort bauen und die Gischt einige Sekunden vorlaufen lassen. */
    public void warmSea(double seconds) {
        sea.buildNow(camera.ex, camera.ez);
        sea.spray.clear();
        double[] cam = {camera.ex, camera.ey, camera.ez};
        for (double t = -seconds; t < 0; t += 0.1) sea.step(0.1, simTime + t, state, cam);
    }

    public void warmFountain(double seconds) {
        fountainR.fountain.clear();
        double[][] srcs = lava.fountains();
        for (double t = 0; t < seconds; t += 0.1) fountainR.fountain.update(0.1, srcs, state.windVector());
    }

    /** Für Standbilder: Uhr und Kamera nicht aus der Wanduhr weiterrechnen. */
    public volatile boolean frozen;

    /** Ein Bild in Größe w × h rechnen und auf den Standard-Bildspeicher legen. */
    void renderFrame(GL4 gl, int w, int h) {
        float[] view = camera.view();
        float[] proj = camera.projection(w / (double) h, reversedZ);
        float[] vp = Mat4.mul(proj, view);
        float[] inv = Mat4.invert(vp);
        double[] cam = {camera.ex, camera.ey, camera.ez};
        double[] fwd = camera.forward();
        double[] wind = state.windVector();
        boolean sunUp = state.clock.elevationDeg > -1;
        cascades.update(cam, fwd, camera.fovy, w / (double) h, state.clock.dir, camera.distance(), reversedZ);
        gl.glBindVertexArray(emptyVao);

        frameNo++;
        clouds.bindWeather(gl, weather);
        mark(gl, 0);
        // 0 Pflanzen um die Kamera wählen (neue Kacheln nach und nach)
        boolean floraOn = showFlora && lod.ready();
        long t0 = System.nanoTime();
        if (floraOn) flora.update(gl, Ground.grid(), cam, vp, 40);
        t0 = cpu(CPU_FLORA, t0);
        lifeR.update(gl, life, cam[0], cam[1], cam[2]);
        // Bodendecke: nur zu Fuß (nicht in der Röhre) oder knapp über dem Boden
        boolean coverOn = showFlora && lod.ready() && Ground.grid() != null
                && (camera.walking ? camera.ey > Ground.height(camera.ex, camera.ez) - 1.0 : camera.ey - Ground.height(camera.ex, camera.ez) < 25);
        groundCover.update(gl, (float) (simTime % 100000), camera.ex, camera.ez, state.clock.day(), !coverOn);
        cpu(CPU_COVER, t0);

        // Lava: Grube und Auflage in die Höhenabfrage (einmal), neue Werte auf die Grafikkarte
        if (lod.ready()) {
            lava.installGround();
            long tl = System.nanoTime();
            lavaLayer.update(gl, lava, state.exposure, simTime);
            cpu(CPU_LAVA, tl);
        }

        section.place(gl, sectionMode, magma, cam[0], cam[2]);
        if (wantSectionView && sectionMode != SectionRenderer.Mode.OFF) {
            wantSectionView = false;
            double[] sv = section.view(camera.ex, camera.ez);
            camera.autoOrbit = false;
            camera.lookAt(sv[0], sv[1], sv[2], sv[3], sv[4], sv[5]);
        }
        if (lod.ready()) {
            rocks.intact = archIntact;
            rocks.update(gl);
        }

        // 1 Schatten: das Gelände in die drei Kaskaden (Knoten im Umkreis, ohne Ausblenden nach dem Bild)
        boolean shadowPass = shadows && sunUp && lod.ready();
        if (shadowPass) {
            gl.glViewport(0, 0, shadowSize, shadowSize);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(GL.GL_LESS);
            gl.glDepthMask(true);
            gl.glClearDepth(1.0);
            gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
            gl.glPolygonOffset(2.0f, 4.0f);
            gl.glDisable(GL.GL_CULL_FACE);
            terrainDepth.use(gl);
            terrainDepth.set(gl, "uCamPos", cam);
            terrainDepth.set(gl, "uCurve", curvature ? 1f : 0f);
            // Die ferne Kaskade (Kilometer weit, grobe Kartenpunkte) nur jedes zweite Bild neu zeichnen;
            // dazwischen gelten ihre Matrix und Grenze vom letzten Mal, passend zur Karte
            boolean farFresh = (frame & 1) == 0 || farVP == null || Math.abs(cascades.splits[2] - farSplit) > farSplit * 0.02
                    || Math.hypot(cam[0] - farEye[0], cam[2] - farEye[1]) > farSplit * 0.02;
            if (farFresh) {
                farEye[0] = cam[0];
                farEye[1] = cam[2];
                farVP = cascades.lightVP[2];
                farSplit = cascades.splits[2];
                farTexel = cascades.texel[2];
            } else {
                cascades.lightVP[2] = farVP;
                cascades.splits[2] = farSplit;
                cascades.texel[2] = farTexel;
            }
            for (int c = 0; c < Cascades.COUNT; c++) {
                if (c == 2 && !farFresh) continue;
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, shadowFbo[c]);
                gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
                terrainDepth.mat(gl, "uLightVP", cascades.lightVP[c]);
                // je Kaskade nur die Knoten in ihrem Ausschnitt (gleiche Stufen wie im Bild, darum kein Schattenflimmern)
                long ts = System.nanoTime();
                lod.select(gl, cam, cascades.lightVP[c], c == 2 ? 2 : 0);
                cpuSelect += System.nanoTime() - ts;
                lod.draw(gl, terrainDepth, detail);
                if (c < 2) {
                    rockDepth.use(gl);
                    rockDepth.set(gl, "uCamPos", cam);
                    rockDepth.set(gl, "uCurve", curvature ? 1f : 0f);
                    rockDepth.mat(gl, "uLightVP", cascades.lightVP[c]);
                    rocks.draw(gl);
                    terrainDepth.use(gl);
                }
                if (lifeR.any() && c < 2) {
                    lifeDepth.use(gl);
                    lifeDepth.set(gl, "uCamPos", cam);
                    lifeDepth.set(gl, "uCurve", curvature ? 1f : 0f);
                    lifeDepth.mat(gl, "uLightVP", cascades.lightVP[c]);
                    lifeR.draw(gl, lifeDepth, false);
                    terrainDepth.use(gl);
                }
                if (floraOn && c < 2) {
                    floraDepth.use(gl);
                    floraDepth.set(gl, "uCamPos", cam);
                    floraDepth.set(gl, "uCurve", curvature ? 1f : 0f);
                    floraDepth.set(gl, "uTime", (float) (simTime % 3600));
                    floraDepth.set(gl, "uWind", (float) wind[0], (float) wind[1]);
                    floraDepth.mat(gl, "uLightVP", cascades.lightVP[c]);
                    lavaLayer.bind(gl, floraDepth);
                    flora.drawShadow(gl, c);
                    terrainDepth.use(gl);
                }
            }
            gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
            gl.glBindVertexArray(emptyVao);
        }

        mark(gl, 1);
        // 2 Himmelstabelle
        if (!staticOcean || !skyLutInitialized) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, lutFbo);
            gl.glViewport(0, 0, 256, 128);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glDisable(GL.GL_CULL_FACE);
            skyLut.use(gl);
            skyLut.set(gl, "uSunDir", state.clock.dir);
            skyLut.set(gl, "uMoonDir", state.clock.moonDir);
            skyLut.set(gl, "uMoonLit", (float) state.clock.moonLit);
            skyLut.set(gl, "uOvercast", (float) state.overcast);
            skyLut.set(gl, "uSunPower", (float) Atmosphere.SUN_POWER);
            skyLut.set(gl, "uMie", (float) state.haze);
            skyLut.set(gl, "uMieG", 0.76f);
            skyLut.set(gl, "uScale", (float) state.exposure);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            skyLutInitialized = true;
        }

        // 3 Szene
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, shadowTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        float shadowOn = shadowPass ? 1f : 0f;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDepthMask(true);
        gl.glClearDepth(reversedZ ? 0.0 : 1.0);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);

        sky.use(gl);
        common(gl, sky, vp, inv, cam, fwd, wind, shadowOn);
        sky.set(gl, "uStars", (float) state.stars);
        sky.set(gl, "uMoonLit", (float) state.clock.moonLit);
        sky.set(gl, "uMirror", 0f);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDepthMask(true);

        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        if (lod.ready()) {
            long ts = System.nanoTime();
            lod.select(gl, cam, vp);
            cpuSelect += System.nanoTime() - ts;
            terrain.use(gl);
            common(gl, terrain, vp, inv, cam, fwd, wind, shadowOn);
            terrain.set(gl, "uDetail", (float) detail);
            terrain.set(gl, "uShowLod", showLod ? 1 : 0);
            terrain.set(gl, "uShowAge", showAge ? 1 : 0);
            double[][] su = com.dan.kilauea.terrain.Geology.SULFUR;
            float[] sv = new float[su.length * 4];
            for (int i = 0; i < su.length; i++) {
                sv[4 * i] = (float) Geo.x(su[i][0], su[i][1]);
                sv[4 * i + 1] = (float) Geo.z(su[i][0]);
                sv[4 * i + 2] = (float) su[i][2];
                sv[4 * i + 3] = (float) su[i][3];
            }
            gl.glUniform4fv(terrain.loc(gl, "uSulfur"), su.length, sv, 0);
            terrain.set(gl, "uSulfurCount", su.length);
            float[] holes = new float[16];
            if (tube.ready()) {
                System.arraycopy(tube.holeA, 0, holes, 0, 8);
                System.arraycopy(tube.holeB, 0, holes, 8, 8);
            }
            gl.glUniform4fv(terrain.loc(gl, "uHole"), 4, holes, 0);
            lod.draw(gl, terrain, detail);
            terrainNodes = lod.nodes;
            terrainVerts = lod.vertices;
        }
        mark(gl, 2);
        if (tube.ready()) {
            gl.glDisable(GL.GL_CULL_FACE);
            tubeS.use(gl);
            common(gl, tubeS, vp, inv, cam, fwd, wind, shadowOn);
            tube.draw(gl, tubeS);
        }
        if (lod.ready() && sectionMode != SectionRenderer.Mode.OFF) {
            gl.glDisable(GL.GL_CULL_FACE);
            sectionS.use(gl);
            common(gl, sectionS, vp, inv, cam, fwd, wind, shadowOn);
            lod.bindTextures(gl, sectionS, detail);
            lavaLayer.bindDeform(gl, sectionS);
            sectionS.set(gl, "uExposureInv", (float) (1 / Math.max(1e-6, state.exposure)));
            synchronized (magma) { section.draw(gl, sectionS, magma, Math.max(3600, 45 * magmaSpeed)); }
        }
        if (lod.ready()) {
            gl.glDisable(GL.GL_CULL_FACE);
            rockS.use(gl);
            common(gl, rockS, vp, inv, cam, fwd, wind, shadowOn);
            rocks.draw(gl);
        }
        if (roads.ready()) {
            gl.glDisable(GL.GL_CULL_FACE);
            roadS.use(gl);
            common(gl, roadS, vp, inv, cam, fwd, wind, shadowOn);
            lod.bindTextures(gl, roadS, detail);
            lavaLayer.bindDeform(gl, roadS);
            roads.draw(gl, roadS);
            dirtPaths.draw(gl, roadS);
            pavedPaths.draw(gl, roadS);
        }
        if (groundCover.triangles > 0) {
            gl.glDisable(GL.GL_CULL_FACE);
            groundS.use(gl);
            common(gl, groundS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, groundS);
            groundCover.draw(gl);
        }
        if (floraOn) {
            gl.glDisable(GL.GL_CULL_FACE);
            floraS.use(gl);
            common(gl, floraS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, floraS);
            flora.draw(gl, 0);
        }
        if (lifeR.any()) {
            gl.glDisable(GL.GL_CULL_FACE);
            lifeS.use(gl);
            common(gl, lifeS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, lifeS);
            lifeR.draw(gl, lifeS, false);
        }
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(emptyVao);

        if (pillowR.pillows.instCount > 0) {
            gl.glDisable(GL.GL_CULL_FACE);
            pillowS.use(gl);
            common(gl, pillowS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, pillowS);
            pillowR.draw(gl);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 3);
        if (lod.ready() && lakeR.ready() && lava.showLake && lavaLayer.show && !Double.isNaN(lava.lake().rimY)) {
            gl.glDisable(GL.GL_CULL_FACE);
            lakeR.update(gl, lava);   // vor common(): hochladen benutzt Einheit 0
            lakeS.use(gl);
            common(gl, lakeS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, lakeS);
            synchronized (lava.lake()) { lakeR.draw(gl, lakeS, lava.lake()); }
        }
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 4);
        // 3b Kopie von Farbe und Tiefe, damit das Meer den Grund sieht
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);

        // 3c Meer
        if (showOcean) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_BLEND);
            sea.upload(gl);           // vor common(): hochladen benutzt Einheit 0
            ocean.use(gl);
            common(gl, ocean, vp, inv, cam, fwd, wind, staticOcean ? 0f : shadowOn);
            gl.glActiveTexture(GL.GL_TEXTURE3);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyTex);
            gl.glActiveTexture(GL.GL_TEXTURE4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            ocean.set(gl, "uSceneColor", 3);
            ocean.set(gl, "uSceneDepth", 4);
            ocean.set(gl, "uScreen", (float) w, (float) h);
            double[] sw = state.swellVector();
            ocean.set(gl, "uSwell", (float) sw[0], (float) sw[1]);
            ocean.set(gl, "uDetail", (float) detail);
            ocean.set(gl, "uSeaY", 0f);
            ocean.set(gl, "uNear", (float) camera.near());
            ocean.set(gl, "uSsr", !staticOcean && detail > 0.5 ? 1 : 0);
            ocean.set(gl, "uSeaDebug", Boolean.getBoolean("kilauea.meerPruefbild") ? 1 : 0);
            ocean.set(gl, "uDiagnosticFlat", flatOcean ? 1 : 0);
            ocean.set(gl, "uDiagnosticFixedReflection", fixedOceanReflection ? 1 : 0);
            ocean.set(gl, "uDiagnosticNoSkyLut", oceanWithoutSkyLut ? 1 : 0);
            ocean.set(gl, "uDiagnosticNoAerial", oceanWithoutAerial ? 1 : 0);
            sea.bind(gl, ocean, state);
            if (staticOcean) {
                ocean.set(gl, "uTime", 0f);
                ocean.set(gl, "uWaterTime", 0f);
                ocean.set(gl, "uSwellPhase", 0f, 0f);
                ocean.set(gl, "uWeatherRect", 0f, 0f, 1f, 0f);
            }
            lavaLayer.bind(gl, ocean);
            if (staticOcean) {
                ocean.set(gl, "uLavaExposure", 0f);
                ocean.set(gl, "uEntryCount", 0);
            }
            oceanMesh.draw(gl);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);

        mark(gl, 5);
        // 3d Wolken, Vog, Regenschleier, Hitzeflimmern (Phase 11): Kopie des Bildes, Wolken in halber
        // Auflösung, dann zusammen zurück ins Bild
        boolean under = showOcean && camera.ey < -0.05;
        if (!under && lod.ready()) {
            gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
            gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
            gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
            gl.glBindVertexArray(emptyVao);
            boolean cl = clouds.show && weather.ready();
            if (cl) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, clouds.fbo(gl, w, h));
                gl.glViewport(0, 0, clouds.width(), clouds.height());
                cloudS.use(gl);
                common(gl, cloudS, vp, inv, cam, fwd, wind, shadowOn);
                lavaLayer.bind(gl, cloudS);
                gl.glActiveTexture(GL.GL_TEXTURE4);
                gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
                gl.glActiveTexture(GL.GL_TEXTURE0);
                cloudS.set(gl, "uSceneDepth", 4);
                cloudS.set(gl, "uCloudNoise", CloudLayer.NOISE_UNIT);
                cloudS.set(gl, "uFrame", (float) (frameNo % 64));
                cloudS.set(gl, "uSteps", detail > 0.5 ? 28 : 16);
                cloudS.set(gl, "uVogInT", thermal ? 0f : 1f);   // Wärmebild: nur Wolken verdecken (Statusbild Phase 14)
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            compS.use(gl);
            common(gl, compS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, compS);
            gl.glActiveTexture(GL.GL_TEXTURE3);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyTex);
            gl.glActiveTexture(GL.GL_TEXTURE4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0 + CloudLayer.CLOUD_UNIT);
            gl.glBindTexture(GL.GL_TEXTURE_2D, cl ? clouds.texture() : 0);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            compS.set(gl, "uSceneColor", 3);
            compS.set(gl, "uSceneDepth", 4);
            compS.set(gl, "uClouds", CloudLayer.CLOUD_UNIT);
            compS.set(gl, "uCloudsOn", cl ? 1 : 0);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 6);
        // 3e Gischt an den Klippen und Dampf (durchsichtig, nach dem Meer)
        boolean sprayOn = showOcean && sea.show && sea.spray.puffCount() > 0;
        if (sprayOn) sea.sprayR.upload(gl);
        boolean bubblesOn = pillowR.pillows.puffCount() > 0;
        if (bubblesOn) pillowR.bubbles.upload(gl);
        if (showSteam && lod.ready()) {
            steam.steam.strength = 0.45 + 0.55 * (1 - Atmosphere.smooth(10, 60, state.clock.elevationDeg));
            steam.upload(gl);
        }
        if ((showSteam || sprayOn || bubblesOn) && lod.ready()) {
            // Phase 12: in halber Auflösung, wenn die Tiefe umgekehrt ist (dann prüft der Shader selbst gegen die
            // Szene; ein Viertel der Bildpunkte). Sonst wie bisher im vollen Bild mit Tiefentest.
            boolean half = halfResSteam && reversedZ;
            if (half) {
                int hw = Math.max(1, w / 2), hh = Math.max(1, h / 2);
                if (steamFbo == 0 || steamW != hw || steamH != hh) makeSteamTarget(gl, hw, hh);
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, steamFbo);
                gl.glViewport(0, 0, hw, hh);
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                gl.glDisable(GL.GL_DEPTH_TEST);
            } else {
                gl.glEnable(GL.GL_DEPTH_TEST);
                gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            }
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
            steamS.use(gl);
            common(gl, steamS, vp, inv, cam, fwd, wind, shadowOn);
            float[] vw = view;
            steamS.set(gl, "uCamRight", vw[0], vw[4], vw[8]);
            steamS.set(gl, "uCamUp", vw[1], vw[5], vw[9]);
            gl.glActiveTexture(GL.GL_TEXTURE4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            steamS.set(gl, "uSceneDepth", 4);
            steamS.set(gl, "uScreen", (float) (half ? steamW : w), (float) (half ? steamH : h));
            steamS.set(gl, "uNear", (float) camera.near());
            lavaLayer.bind(gl, steamS);
            if (bubblesOn) pillowR.bubbles.draw(gl);
            if (sprayOn) sea.sprayR.draw(gl);
            if (showSteam) steam.draw(gl);
            gl.glBindVertexArray(emptyVao);   // die Vollbild-Pässe zeichnen ohne Puffer, brauchen aber ein VAO
            if (half) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
                gl.glViewport(0, 0, w, h);
                overlay.use(gl);
                gl.glBindTexture(GL.GL_TEXTURE_2D, steamTex);
                overlay.set(gl, "uTex", 0);
                overlay.set(gl, "uFlipY", 0);
                overlay.set(gl, "uGain", 1f);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            }
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
        mark(gl, 7);
        // 3e Fontäne: Licht, aufaddiert, ohne Tiefe zu schreiben
        if (fountainR.fountain.outCount > 0 && !Boolean.getBoolean("kilauea.ohneFontaene")) {
            fountainR.upload(gl);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            fountainS.use(gl);
            common(gl, fountainS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, fountainS);
            fountainR.draw(gl);
            gl.glBindVertexArray(emptyVao);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
        if (littoral.sparkCount() > 0) {
            littoralR.upload(gl);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            fountainS.use(gl);
            common(gl, fountainS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, fountainS);
            littoralR.draw(gl);
            gl.glBindVertexArray(emptyVao);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
        // 3f unter Wasser (Phase 10): die Wassersäule zwischen Auge und Bild
        if (showOcean && camera.ey < -0.05) {
            gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
            gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
            gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glBindVertexArray(emptyVao);
            underS.use(gl);
            common(gl, underS, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, underS);
            gl.glActiveTexture(GL.GL_TEXTURE3);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyTex);
            gl.glActiveTexture(GL.GL_TEXTURE4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            underS.set(gl, "uSceneColor", 3);
            underS.set(gl, "uSceneDepth", 4);
            underS.set(gl, "uNear", (float) camera.near());
            underS.set(gl, "uRays", detail > 0.5 ? 1 : 0);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 8);
        // Phase 14: Wärmebild statt Belichtung, Bloom und Nachbearbeitung
        boolean therm = thermal && lod.ready();
        if (therm) thermalPass(gl, w, h, vp, inv, cam, fwd, wind, shadowOn, clouds.show && weather.ready() && !under);
        else {
        // 4 Belichtungsmessung
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, lumFbo);
        gl.glViewport(0, 0, 128, 64);
        lum.use(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        lum.set(gl, "uHdr", 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        if ((frame & 3) == 0) {
            // Belichtung lesen, ohne auf die Grafikkarte zu warten: das Ergebnis geht in einen Puffer und
            // wird erst vier Bilder später abgeholt (vorher hielt das Lesen jedes vierte Bild die CPU an)
            if (lumPbo == 0) {
                int[] t = new int[1];
                gl.glGenBuffers(1, t, 0);
                lumPbo = t[0];
                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, lumPbo);
                gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, 16, null, GL4.GL_STREAM_READ);
                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0);
            }
            if (frozen) {
                // Standbilder: sofort lesen (Tempo zählt nicht, die Belichtung soll in wenigen Bildern stehen)
                gl.glBindTexture(GL.GL_TEXTURE_2D, lumTex);
                gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
                lumRead.clear();
                gl.glGetTexImage(GL.GL_TEXTURE_2D, 7, GL4.GL_RG, GL.GL_FLOAT, lumRead);
                float sum = lumRead.get(0), wsum = lumRead.get(1);
                if (wsum > 1e-6) state.measured(Math.exp(sum / wsum));
            }
            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, lumPbo);
            if (!frozen && lumFence != 0) {
                int st = gl.glClientWaitSync(lumFence, 0, 0L);
                if (st == GL4.GL_ALREADY_SIGNALED || st == GL4.GL_CONDITION_SATISFIED) {
                    lumRead.clear();
                    gl.glGetBufferSubData(GL4.GL_PIXEL_PACK_BUFFER, 0, 8, lumRead);
                    float sum = lumRead.get(0), wsum = lumRead.get(1);
                    if (wsum > 1e-6) state.measured(Math.exp(sum / wsum));
                    gl.glDeleteSync(lumFence);
                    lumFence = 0;
                }
            }
            if (!frozen && lumFence == 0) {
                gl.glBindTexture(GL.GL_TEXTURE_2D, lumTex);
                gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
                gl.glGetTexImage(GL.GL_TEXTURE_2D, 7, GL4.GL_RG, GL.GL_FLOAT, 0L);
                lumFence = gl.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            }
            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0);
        }

        // 5 Bloom
        if (bloom) {
            bloomDown.use(gl);
            bloomDown.set(gl, "uSrc", 0);
            int src = hdrTex, sw = w, sh = h;
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bloomFbo[i]);
                gl.glViewport(0, 0, bloomW[i], bloomH[i]);
                gl.glBindTexture(GL.GL_TEXTURE_2D, src);
                bloomDown.set(gl, "uTexel", 1f / sw, 1f / sh);
                bloomDown.set(gl, "uFirst", i == 0 ? 1 : 0);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
                src = bloomTex[i]; sw = bloomW[i]; sh = bloomH[i];
            }
            bloomUp.use(gl);
            bloomUp.set(gl, "uSrc", 0);
            bloomUp.set(gl, "uRadius", 1.0f);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bloomFbo[i - 1]);
                gl.glViewport(0, 0, bloomW[i - 1], bloomH[i - 1]);
                gl.glBindTexture(GL.GL_TEXTURE_2D, bloomTex[i]);
                bloomUp.set(gl, "uTexel", 1f / bloomW[i], 1f / bloomH[i]);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            }
            gl.glDisable(GL.GL_BLEND);
        }

        // 6 Nachbearbeitung, dann Kantenglättung auf den Bildschirm
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, ldrFbo);
        gl.glViewport(0, 0, w, h);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bloomTex[0]);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uBloom", 1);
        post.set(gl, "uBloomStrength", bloom ? 0.05f : 0f);
        post.set(gl, "uBloomNorm", 1f / BLOOM_LEVELS);
        post.set(gl, "uExposure", 1.0f);
        post.set(gl, "uVignette", 0.28f);
        post.set(gl, "uTime", (float) (simTime % 1000));
        post.set(gl, "uNight", (float) state.night);
        gl.glUniform4f(post.loc(gl, "uWeather"), (float) state.rain, (float) state.wet, (float) state.rainbow, (float) state.overcast);
        post.set(gl, "uBars", (float) (BAR * barsNow));
        post.set(gl, "uFade", 1f);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        aa.use(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, ldrTex);
        aa.set(gl, "uLdr", 0);
        aa.set(gl, "uTexel", 1f / w, 1f / h);
        aa.set(gl, "uOn", fxaa ? 1 : 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // Phase 12: Infotafel des Drehbuchs unten links
        com.dan.kilauea.script.Player.Caption cap = tour.active() ? tour.caption() : player.currentCaption();
        if (cap == null) cap = info;
        if (showCaption && caption.update(gl, cap, surfW, surfH)) {
            int m = Math.max(12, surfW / 64) + (int) Math.round(BAR * barsNow * surfH);
            int mx = Math.max(12, surfW / 64);
            int cw = Math.min(caption.width, surfW - 2 * mx), ch = Math.min(caption.height, surfH - 2 * m);
            gl.glViewport(mx, m, cw, ch);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
            overlay.use(gl);
            gl.glBindTexture(GL.GL_TEXTURE_2D, caption.texture());
            overlay.set(gl, "uTex", 0);
            overlay.set(gl, "uFlipY", 1);
            overlay.set(gl, "uGain", 1f);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_BLEND);
            gl.glViewport(0, 0, surfW, surfH);
        }
        drawHud(gl, therm);
        gl.glBindVertexArray(0);
        mark(gl, 9);
        readMarks(gl);
    }

    // ------------------------------------------------------------------ Phase 14: Wärmebild und Tafeln

    /** Wärmebild ins LDR-Bild: Teilchen der Fontänen als Temperatur, dann jeder Bildpunkt nach thermal.frag; Messung im Kreuz. */
    private void thermalPass(GL4 gl, int w, int h, float[] vp, float[] inv, double[] cam, double[] fwd, double[] wind, float shadowOn, boolean cloudsOn) {
        // Tiefe der fertigen Szene für den Shader
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        // Teilchen: größte Temperatur und Deckung je Bildpunkt, hinter dem Gelände verdeckt
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, heatFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        boolean parts = fountainR.fountain.outCount > 0 || littoral.sparkCount() > 0;
        if (parts) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendEquation(GL4.GL_MAX);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            fountainHeat.use(gl);
            common(gl, fountainHeat, vp, inv, cam, fwd, wind, shadowOn);
            lavaLayer.bind(gl, fountainHeat);
            if (fountainR.fountain.outCount > 0) fountainR.draw(gl);
            if (littoral.sparkCount() > 0) littoralR.draw(gl);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glBindVertexArray(emptyVao);
        }
        // Phase 15: Figuren mit ihrer Wärme (Körper, Motoren, Abgas), hinter dem Gelände verdeckt
        if (lifeR.any()) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendEquation(GL4.GL_MAX);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glDisable(GL.GL_CULL_FACE);
            lifeHeat.use(gl);
            common(gl, lifeHeat, vp, inv, cam, fwd, wind, shadowOn);
            lifeR.draw(gl, lifeHeat, true);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        thermalS.use(gl);
        common(gl, thermalS, vp, inv, cam, fwd, wind, shadowOn);
        lod.bindTextures(gl, thermalS, detail);
        lavaLayer.bind(gl, thermalS);
        boolean lakeOn = lakeR.ready() && lava.showLake && lavaLayer.show && !Double.isNaN(lava.lake().rimY);
        if (lakeOn) synchronized (lava.lake()) { lakeR.bindForThermal(gl, thermalS, lava.lake()); }
        else gl.glUniform1i(thermalS.loc(gl, "uLakeOn"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, heatTex);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
        gl.glActiveTexture(GL.GL_TEXTURE0 + THERMAL_CLOUD_UNIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsOn ? clouds.texture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        thermalS.set(gl, "uHeat", 3);
        thermalS.set(gl, "uSceneDepth", 4);
        thermalS.set(gl, "uClouds", THERMAL_CLOUD_UNIT);
        thermalS.set(gl, "uCloudsOn", cloudsOn ? 1 : 0);
        double[][] su = com.dan.kilauea.terrain.Geology.SULFUR;
        float[] sv = new float[su.length * 4];
        for (int i = 0; i < su.length; i++) {
            sv[4 * i] = (float) Geo.x(su[i][0], su[i][1]);
            sv[4 * i + 1] = (float) Geo.z(su[i][0]);
            sv[4 * i + 2] = (float) su[i][2];
            sv[4 * i + 3] = (float) su[i][3];
        }
        gl.glUniform4fv(thermalS.loc(gl, "uSulfur"), su.length, sv, 0);
        thermalS.set(gl, "uSulfurCount", su.length);
        thermalS.set(gl, "uBars", (float) (BAR * barsNow));
        thermalS.set(gl, "uScreen", (float) w, (float) h);
        thermalS.set(gl, "uProbe", 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, ldrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // Messung im Kreuz: ein Bildpunkt, abgeholt ohne Warten (Standbilder sofort)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, probeFbo);
        gl.glViewport(0, 0, 1, 1);
        thermalS.set(gl, "uProbe", 1);
        thermalS.set(gl, "uProbeUV", 0.5f, 0.5f);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        thermalS.set(gl, "uProbe", 0);
        readProbe(gl);
        gl.glViewport(0, 0, w, h);
    }

    private final FloatBuffer probeRead = Buffers.newDirectFloatBuffer(2);

    private void readProbe(GL4 gl) {
        gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
        if (frozen) {
            probeRead.clear();
            gl.glReadPixels(0, 0, 1, 1, GL4.GL_RG, GL.GL_FLOAT, probeRead);
            probeT = probeRead.get(0);
            probeKind = probeRead.get(1);
            return;
        }
        if (probePbo == 0) {
            int[] t = new int[1];
            gl.glGenBuffers(1, t, 0);
            probePbo = t[0];
            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, probePbo);
            gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, 8, null, GL4.GL_STREAM_READ);
        } else gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, probePbo);
        if (probeFence != 0) {
            int st = gl.glClientWaitSync(probeFence, 0, 0L);
            if (st == GL4.GL_ALREADY_SIGNALED || st == GL4.GL_CONDITION_SATISFIED) {
                probeRead.clear();
                gl.glGetBufferSubData(GL4.GL_PIXEL_PACK_BUFFER, 0, 8, probeRead);
                probeT = probeRead.get(0);
                probeKind = probeRead.get(1);
                gl.glDeleteSync(probeFence);
                probeFence = 0;
            }
        }
        if (probeFence == 0) {
            gl.glReadPixels(0, 0, 1, 1, GL4.GL_RG, GL.GL_FLOAT, 0L);
            probeFence = gl.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        }
        gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0);
    }

    /** Tafeln über dem fertigen Bild: Messnetz oben rechts, Skala des Wärmebilds unten rechts. */
    private void drawHud(GL4 gl, boolean therm) {
        int margin = Math.max(12, surfW / 64);
        int barPx = (int) Math.round(BAR * barsNow * surfH);
        if (monitorShown() && lod.ready()) {
            int mw = (int) Math.max(320, Math.min(470, surfW * 0.27));
            int maxH = surfH - 2 * margin - 2 * barPx;
            double tx = camera.targetX(), tz = camera.targetZ();
            if (maxH > 200) monitorHud.refresh(gl, 250, () -> com.dan.kilauea.monitor.MonitorPainter.paint(monitor.view(tx, tz, (int) (mw * 0.80)), mw, maxH), frozen);
            monitorHud.draw(gl, overlay, surfW - margin - monitorHud.width, surfH - margin - barPx - monitorHud.height, surfW, surfH);
        }
        if (therm) {
            int tw = (int) Math.max(300, Math.min(400, surfW * 0.24));
            com.dan.kilauea.monitor.Reading pr = thermalProbe(), wh = thermalWhat();
            thermalHud.refresh(gl, 250, () -> com.dan.kilauea.monitor.MonitorPainter.paintThermal(pr, wh, tw), frozen);
            thermalHud.draw(gl, overlay, surfW - margin - thermalHud.width, margin + barPx, surfW, surfH);
        }
    }

    // ------------------------------------------------------------------ Zeit je Durchgang (Zeitstempel der Grafikkarte)

    static final int MARKS = 10;
    private final int[][] stamp = new int[2][MARKS];
    private final boolean[] stampSet = new boolean[2];
    /** Geglättete Zeit (ms): Schatten, Himmel und Boden, Straßen und Pflanzen, Meer, Nachbearbeitung. */
    private final double[] passMs = new double[MARKS - 1];
    private boolean stampsMade;

    private void mark(GL4 gl, int i) {
        if (!stampsMade) {
            for (int k = 0; k < 2; k++) gl.glGenQueries(MARKS, stamp[k], 0);
            stampsMade = true;
        }
        gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP);
        if (i == MARKS - 1) stampSet[frame & 1] = true;
    }

    private void readMarks(GL4 gl) {
        int o = (frame & 1) ^ 1;
        if (!stampSet[o]) return;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        stampSet[o] = false;
    }

    /** Zeit je Durchgang als Text, etwa „Schatten 3,1 · Boden 6,0 · Pflanzen 2,2 · Meer 1,4 · Bild 1,0 ms“. */
    public String passTimes() {
        return String.format(Locale.GERMAN, "Schatten %.1f · Himmel+Boden %.1f · Straßen+Pflanzen+Figuren %.1f · Lavasee %.1f · Meer %.1f · Wolken %.1f · Dampf %.1f · Fontäne %.1f · Nachbearbeitung %.1f ms",
                passMs[0], passMs[1], passMs[2], passMs[3], passMs[4], passMs[5], passMs[6], passMs[7], passMs[8]);
    }

    // ------------------------------------------------------------ Zeit des Prozessors je Bild
    static final int CPU_TOTAL = 0, CPU_FLORA = 1, CPU_COVER = 2, CPU_SELECT = 3, CPU_STEAM = 4, CPU_LAVA = 5, CPU_SEA = 6, CPU_LIFE = 7;
    /** Geglättete Zeit (ms) des Prozessors: ganzes Bild (mit Treiber), Pflanzen, Bodendecke, Gelände-Auswahl, Dampf. */
    private final double[] cpuMs = new double[8];
    private long cpuSelect;

    private long cpu(int i, long t0) {
        long t1 = System.nanoTime();
        cpuMs[i] = cpuMs[i] * 0.95 + (t1 - t0) / 1e6 * 0.05;
        return t1;
    }

    /** Zeit des Prozessors je Bild als Text. */
    public String cpuTimes() {
        return String.format(Locale.GERMAN, "gesamt %.1f · Pflanzen %.1f · Bodendecke %.1f · Gelände-Auswahl %.1f · Dampf %.1f · Lava %.1f · Meer %.1f · Leben+Klang %.1f ms",
                cpuMs[0], cpuMs[1], cpuMs[2], cpuMs[3], cpuMs[4], cpuMs[5], cpuMs[6], cpuMs[7]);
    }

    /** Ziel-Bildspeicher des letzten Schritts (0 = Fenster; Standbilder setzen ihren eigenen). */
    int defaultFbo;

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] cam, double[] fwd, double[] wind, float shadowOn) {
        // Himmelstabelle auf Einheit 0 für jeden Pass neu binden: Hochladen (Lavasee alle 0,2 s, Raster vor der
        // Küste, Geländekacheln) bindet auf der aktiven Einheit 0 und hinterlässt dort „keine Textur“. Dann las
        // das Meer in diesen Bildern Spiegelung und Luft aus einer leeren Tabelle, und die Fläche pulsierte.
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        s.mat(gl, "uViewProj", vp);
        s.mat(gl, "uInvViewProj", inv);
        section.cutUniforms(gl, s);
        s.set(gl, "uCamPos", cam);
        s.set(gl, "uCamFwd", fwd);
        s.set(gl, "uSkyLut", 0);
        s.set(gl, "uShadow", 1);
        s.set(gl, "uSunDir", state.clock.dir);
        s.set(gl, "uSunColor", state.sunColor);
        s.set(gl, "uMoonDir", state.clock.moonDir);
        s.set(gl, "uMoonColor", state.moonColor);
        s.set(gl, "uBetaR", (float) Atmosphere.BETA_R[0], (float) Atmosphere.BETA_R[1], (float) Atmosphere.BETA_R[2]);
        s.set(gl, "uBetaM", (float) state.haze);
        s.set(gl, "uTime", (float) (simTime % 3600));
        s.set(gl, "uWind", (float) wind[0], (float) wind[1]);
        s.mats(gl, "uLightVP", cascades.lightVP);
        s.set(gl, "uSplits", cascades.splits);
        s.set(gl, "uTexel", cascades.texel);
        s.set(gl, "uZeroOne", reversedZ ? 1 : 0);
        s.set(gl, "uShadowOn", shadowOn);
        s.set(gl, "uCurve", curvature ? 1f : 0f);
        s.set(gl, "uExposure", (float) state.exposure);
        gl.glUniform4f(s.loc(gl, "uWeather"), (float) state.rain, (float) state.wet, (float) state.rainbow, (float) state.overcast);
        gl.glUniform4f(s.loc(gl, "uMist"), (float) state.mist, 40f, (float) cam[1], 0f);
        int le = s.loc(gl, "uEntry");
        if (le >= 0 && entryN > 0) gl.glUniform4fv(le, entryN, entryU, 0);
        gl.glUniform1i(s.loc(gl, "uEntryCount"), entryN);
        gl.glUniform1f(s.loc(gl, "uWaterTime"), (float) (simTime % 3600));
        // Phase 15: Scheinwerfer (cars.glsl)
        int cl = s.loc(gl, "uCarLights");
        if (cl >= 0) {
            gl.glUniform1i(cl, lifeR.carLights);
            if (lifeR.carLights > 0) {
                gl.glUniform4fv(s.loc(gl, "uCarLight"), lifeR.carLights, lifeR.carLight, 0);
                gl.glUniform4fv(s.loc(gl, "uCarDir"), lifeR.carLights, lifeR.carDir, 0);
            }
            gl.glUniform1f(s.loc(gl, "uCarExposure"), (float) state.exposure);
        }
        clouds.uniforms(gl, s, weather, state.clock.dir);
    }

    private void makeSteamTarget(GL4 gl, int w, int h) {
        if (steamFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{steamFbo}, 0);
            gl.glDeleteTextures(1, new int[]{steamTex}, 0);
        }
        steamTex = Gpu.texture(gl, w, h, GL4.GL_RGBA16F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, steamTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        steamFbo = Gpu.fbo(gl, steamTex, 0);
        steamW = w;
        steamH = h;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        caption.dispose(gl);
        monitorHud.dispose(gl);
        thermalHud.dispose(gl);
        if (steamFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{steamFbo}, 0);
            gl.glDeleteTextures(1, new int[]{steamTex}, 0);
            steamFbo = steamTex = 0;
        }
        for (Shader s : all) s.dispose(gl);
        lod.dispose(gl);
        flora.dispose(gl);
        lifeR.dispose(gl);
        sound.stop();
        roads.dispose(gl);
        dirtPaths.dispose(gl);
        tube.dispose(gl);
        lakeR.dispose(gl);
        fountainR.dispose(gl);
        littoralR.dispose(gl);
        pillowR.dispose(gl);
        clouds.dispose(gl);
        lavaLayer.dispose(gl);
        rocks.dispose(gl);
        section.dispose(gl);
        groundCover.dispose(gl);
        steam.dispose(gl);
        sea.dispose(gl);
        pavedPaths.dispose(gl);
        oceanMesh.dispose(gl);
    }

    // ------------------------------------------------------------------ Hilfen

    private void uploadIfReady(GL4 gl) {
        if (pendingTerrain != null && pendingTerrain.isDone()) {
            HeightGrid g;
            try {
                g = pendingTerrain.join();
            } catch (RuntimeException e) {
                pendingTerrain = null;
                fail("Das Gelände ließ sich nicht bauen: " + e);
                return;
            }
            pendingTerrain = null;
            lod.upload(gl, g);
            lod.uploadDetail(gl, com.dan.kilauea.terrain.DetailTiles.all());
            roads.init(gl);
            dirtPaths.init(gl);
            section.init(gl);
            tube.init(gl);
            pavedPaths.init(gl);
            terrainSource = g.source;
            System.out.printf(Locale.ROOT, "Gelände: %d × %d Zellen zu %.0f m, Höhe %.0f bis %.0f m; %s%n",
                    HeightGrid.N, HeightGrid.N, HeightGrid.CELL, g.minHeight(), g.maxHeight(), g.source);
            // der Drehpunkt lag vielleicht unter dem neuen Boden
            camera.ty = Math.max(camera.ty, Math.max(0, Ground.height(camera.tx, camera.tz)));
        }
    }

    /** Wartet (für Standbilder), bis das Gelände hochgeladen ist. */
    void awaitTerrain(GL4 gl) {
        if (pendingTerrain != null) {
            pendingTerrain.join();
            uploadIfReady(gl);
        }
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        if (hdrFbo != 0) {
            gl.glDeleteFramebuffers(3, new int[]{hdrFbo, ldrFbo, copyFbo}, 0);
            gl.glDeleteTextures(5, new int[]{hdrTex, depthTex, ldrTex, copyTex, copyDepth}, 0);
            gl.glDeleteFramebuffers(BLOOM_LEVELS, bloomFbo, 0);
            gl.glDeleteTextures(BLOOM_LEVELS, bloomTex, 0);
        }
        hdrW = w;
        hdrH = h;
        hdrTex = Gpu.texture(gl, w, h, GL4.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        copyTex = Gpu.texture(gl, w, h, GL4.GL_RGBA16F, false);
        copyDepth = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        copyFbo = Gpu.fbo(gl, copyTex, copyDepth);
        ldrTex = Gpu.texture(gl, w, h, GL.GL_RGBA8, false);
        ldrFbo = Gpu.fbo(gl, ldrTex, 0);
        // Phase 14: Teilchen der Fontänen für das Wärmebild, mit der Tiefe der Szene
        if (heatFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{heatFbo}, 0);
            gl.glDeleteTextures(1, new int[]{heatTex}, 0);
        }
        heatTex = Gpu.texture(gl, w, h, GL4.GL_RG16F, false);
        heatFbo = Gpu.fbo(gl, heatTex, depthTex);
        int bw = w, bh = h;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
            bloomW[i] = bw;
            bloomH[i] = bh;
            bloomTex[i] = Gpu.texture(gl, bw, bh, GL4.GL_R11F_G11F_B10F, false);
            bloomFbo[i] = Gpu.fbo(gl, bloomTex[i], 0);
        }
    }

    /** Für Standbilder: Zielgröße setzen (ohne Fenster). */
    void prepareTargets(GL4 gl, int w, int h) {
        surfW = w;
        surfH = h;
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
    }

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    /** Liest das fertige Bild aus dem hinteren Bildspeicher und speichert es im Hintergrund als PNG. */
    private void takePhoto(GL4 gl, int w, int h) {
        try {
            java.nio.ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
            gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
            gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
            gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
            String name = "Foto_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            java.io.File dir = photoDir != null ? photoDir : new java.io.File(System.getProperty("user.dir"));
            java.io.File file = new java.io.File(dir, name);
            Thread t = new Thread(() -> {
                try {
                    javax.imageio.ImageIO.write(toImage(bb, w, h), "png", file);
                    photoMsg = "Foto gespeichert: " + file.getName() + " (" + w + " × " + h + ")";
                    java.util.function.Consumer<String> hook = photoHook;
                    if (hook != null) hook.accept(file.getName() + " (" + w + " × " + h + ")");
                } catch (Exception ex) {
                    photoMsg = "Foto fehlgeschlagen: " + ex.getMessage();
                }
                photoMsgUntil = System.nanoTime() + 8_000_000_000L;
            }, "Foto");
            t.setDaemon(true);
            t.start();
            photoMsg = "Foto wird gespeichert …";
            photoMsgUntil = System.nanoTime() + 8_000_000_000L;
        } catch (RuntimeException ex) {
            photoMsg = "Foto fehlgeschlagen: " + ex.getMessage();
            photoMsgUntil = System.nanoTime() + 8_000_000_000L;
        }
    }

    /** RGBA-Zeilen von unten nach oben (OpenGL) in ein Bild von oben nach unten. */
    static java.awt.image.BufferedImage toImage(java.nio.ByteBuffer bb, int w, int h) {
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
        return img;
    }

    private final double[] near = new double[1];

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 250_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    /** Die Statuszeile: Karte, Tempo, Qualität, Ortszeit, Sonne, Blickziel und Höhe des Auges. */
    public String statusLine(int w, int h) {
        String time = SunClock.dateLabel(state.clock.day()) + ", " + SunClock.timeLabel(state.clock.hour()) + " HST";
        String sun = String.format(Locale.GERMAN, "Sonne %.1f°, %03.0f° (%s)", state.clock.elevationDeg, state.clock.azimuthDeg, compass(state.clock.azimuthDeg));
        Place p = Place.nearest(camera.targetX(), camera.targetZ(), near);
        String where = near[0] < 6000 ? p.label + (near[0] > 800 ? String.format(Locale.GERMAN, " (%.1f km)", near[0] / 1000) : "") : Geo.label(camera.targetX(), camera.targetZ());
        String s = String.format(Locale.GERMAN, "%s   ·   %.0f Bilder/s, GPU %.1f ms   ·   %s, %d × %d (%d %%)   ·   %s   ·   %s   ·   Blick auf %s   ·   Auge %,.0f m ü. M.   ·   Gelände %d Knoten, %.1f Mio. Punkte   ·   Pflanzen %,d (%.1f Mio. Dreiecke)",
                glInfo, fps, gpuMs, quality.label, w, h, (int) Math.round(renderScale * 100), time, sun, where, camera.ey, terrainNodes, terrainVerts / 1e6,
                flora.field.shown, flora.triangles / 1e6);
        if (System.nanoTime() < photoMsgUntil) s = photoMsg + "   ·   " + s;
        return s;
    }

    static String compass(double az) {
        String[] n = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        return n[(int) Math.round(az / 45) % 8];
    }
}
