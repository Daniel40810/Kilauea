// sea.glsl — Dünung vor der Küste (Phase 9), gemeinsam für ocean.vert und ocean.frag.
// Zweck: zwei Wellensysteme (Südswell und Passatsee) laufen aus dem tiefen Wasser auf die Küste zu,
// steilen auf, brechen und laufen als Schaumwalze auf das Ufer. Formeln wie sea/Waves.java:
// Dispersion nach Eckart (1952), Aufsteilen K_s = √(c_g,tief / c_g), Brechen bei H > 0,78 d (McCowan),
// Brecherart nach der Iribarren-Zahl [L]; im flachen Wasser Phase φ = −2ω s / √(g d) − ω t [A].
// Eingaben: uSeaTex (Raster um die Kamera: Tiefe, Uferabstand, Richtung zum Meer), uSeaRect, uSwellA/B,
// uSwellPhase (ω t, auf der CPU in double gerechnet, damit Gischt und Bild zusammenpassen).
// Koordinatenraum: Welt (m). Qualitätsstufe: alle. Ersatz: uSeaRect.w = 0 → nur die Dünung im Tiefen.
uniform sampler2D uSeaTex;
uniform vec4 uSeaRect = vec4(0.0);      // x0, z0 (Ecke), Seite (m), an
uniform vec4 uSwellA = vec4(0.0, 1.0, 0.0, 14.0);   // Laufrichtung x, z; Höhe H₀ (m); Periode (s)
uniform vec4 uSwellB = vec4(-1.0, 0.0, 0.0, 8.0);   // Passatsee: ebenso
uniform vec2 uSwellPhase = vec2(0.0);   // ω t für A und B (mod 2π)

const float SEA_G = 9.81;
const float SEA_GAMMA = 0.78;

struct SeaS { float depth; float s; vec2 n; float valid; };

SeaS seaAt(vec2 w) {
    SeaS S;
    S.depth = 1000.0; S.s = 5000.0; S.n = vec2(0.0, 1.0); S.valid = 0.0;
    if (uSeaRect.w < 0.5) return S;
    vec2 uv = (w - uSeaRect.xy) / uSeaRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return S;
    vec4 t = texture(uSeaTex, uv);
    S.depth = t.x; S.s = t.y;
    S.n = length(t.zw) > 1e-3 ? normalize(t.zw) : vec2(0.0, 1.0);
    vec2 e = min(uv, 1.0 - uv);
    S.valid = smoothstep(0.0, 0.05, min(e.x, e.y));
    return S;
}

float seaK(float w, float d) {
    float k0 = w * w / SEA_G;
    return k0 / sqrt(tanh(k0 * max(d, 0.05)));
}

// Aufsteilen K_s
float seaShoal(float w, float d) {
    float k = seaK(w, d), kd = k * max(d, 0.05);
    float n = kd > 10.0 ? 0.5 : 0.5 * (1.0 + 2.0 * kd / sinh(2.0 * kd));
    float cg = n * w / k;
    return sqrt(SEA_G / (2.0 * w) / cg);
}

struct SeaW { float eta; vec2 grad; float foam; float wash; };

// Ein Wellensystem an p. A: Richtung, Höhe, Periode; ph: ω t. Ergebnis wird aufaddiert.
void seaSwell(vec2 p, SeaS S, vec4 A, float ph, float fade, inout SeaW r) {
    float H0 = A.z, T = A.w;
    if (H0 < 0.01) return;
    vec2 dir = A.xy;
    float w = 6.2831853 / T, k0 = w * w / SEA_G, L0 = 6.2831853 / k0;
    // Abschattung: an Küsten, die der Welle abgewandt sind, kommt wenig an (Beugung grob) [G]
    float face = smoothstep(-0.35, 0.55, -dot(dir, S.n));
    float expo = mix(1.0, 0.2 + 0.8 * face, S.valid * fadeOut(2500.0, 300.0, S.s));
    float H = H0 * expo;
    // im Tiefen: ein langer Zug mit leicht verschiedener Richtung daneben
    float phO = k0 * dot(dir, p) - ph;
    // zweiter Zug gleicher Periode, 14° gedreht: Kämme werden lang, aber nicht endlos gerade (Schwebung)
    vec2 dir2 = normalize(dir + vec2(-dir.y, dir.x) * 0.25);
    float phO2 = k0 * dot(dir2, p) - ph + 1.3;
    float etaO = 0.5 * H * (0.75 * cos(phO) + 0.35 * cos(phO2));
    vec2 gradO = -0.5 * H * k0 * (0.75 * sin(phO) * dir + 0.35 * sin(phO2) * dir2);
    // vor der Küste: Kämme parallel zum Ufer, aufgesteilt, gebrochen
    float d = max(S.depth, 0.05);
    float near = S.valid * fadeOut(24.0, 9.0, S.depth) * step(-2.0, S.s);
    float etaN = 0.0, foam = 0.0, wash = 0.0;
    vec2 gradN = vec2(0.0);
    if (near > 0.0) {
        float c = sqrt(SEA_G * d);
        float sPos = max(S.s, 0.0);
        // Phase: die Dünung trifft das Ufer an jeder Stelle etwas früher oder später (schräger Einfall), davor
        // laufen die Kämme parallel zum Ufer mit √(g d) heran
        vec2 ps = p - S.n * S.s;
        float phN = k0 * dot(dir, ps) - ph - 2.0 * w * sPos / c;
        float ks = seaShoal(w, d);
        float ratio = H * ks / (SEA_GAMMA * d);
        float br = smoothstep(0.85, 1.15, ratio);                       // bricht
        float Hl = min(H * ks, SEA_GAMMA * d);
        float m = d / max(sPos, 2.0);                                   // mittlere Neigung bis zum Ufer
        float xi = m / sqrt(max(H, 0.02) / L0);                         // Iribarren
        float cliff = smoothstep(2.5, 4.5, xi);
        // Kamm nach vorn geneigt, wo er bricht
        float sk = phN - 0.45 * br * sin(phN);
        etaN = 0.5 * Hl * cos(sk);
        gradN = -0.5 * Hl * sin(sk) * (1.0 - 0.45 * br * cos(phN)) * (-2.0 * w / c) * S.n;
        // Schaum: Walze am Kamm und weiße Spur dahinter, nur wo die Welle bricht
        float u = fract(phN / 6.2831853);
        float bore = smoothstep(0.93, 0.995, u) * fadeOut(1.0, 0.995, u) + fadeOut(0.03, 0.0, u);
        float trail = exp(-(1.0 - u) * 5.0) * 0.55 + exp(-(1.0 - u) * 1.2) * 0.15;
        foam = br * (1.0 - cliff) * (bore + trail);
        // an der Klippe: kein Brecher, das Wasser schlägt an die Wand und läuft weiß zurück
        // im tiefen Wasser vor der Wand gilt die Phase der Dünung (wie die Gischt auf der CPU); Wellengruppen
        float grp = 0.5 + 0.5 * cos(phO / 7.0 + p.x * 0.006 + p.y * 0.004);
        float hit = pow(0.5 + 0.5 * cos(phO), 6.0) * (0.3 + 0.7 * grp * grp);
        wash = cliff * fadeOut(12.0, 0.0, S.s) * (0.08 + 0.92 * hit) * clamp(H / 1.0, 0.2, 1.3);
        // Auflaufen auf dem flachen Ufer (Schwall)
        float run = exp(-(1.0 - u) * 3.0);                              // nach dem Kamm, dann läuft es ab
        foam += (1.0 - cliff) * fadeOut(8.0, 0.0, S.s) * clamp(H, 0.0, 1.0) * (0.08 + 0.92 * run);
    }
    r.eta += fade * mix(etaO, etaN, near);
    r.grad += fade * mix(gradO, gradN, near);
    r.foam += near * foam;
    r.wash += near * wash;
}

SeaW seaWaves(vec2 p, SeaS S, float fade) {
    SeaW r;
    r.eta = 0.0; r.grad = vec2(0.0); r.foam = 0.0; r.wash = 0.0;
    seaSwell(p, S, uSwellA, uSwellPhase.x, fade, r);
    seaSwell(p, S, uSwellB, uSwellPhase.y, fade, r);
    return r;
}
