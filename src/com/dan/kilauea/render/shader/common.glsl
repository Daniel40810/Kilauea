// common.glsl — Grundbausteine für alle Shader der Kīlauea-Szene.
// Zweck: Rauschen, Himmelstabelle, Erdkrümmung, Luftperspektive. Eingaben: Uniforms unten.
// Koordinaten: x Osten, y oben, z Süden, Meter; Ursprung Halemaʻumaʻu, Meeresspiegel bei y = 0.
// Qualitätsstufe: alle. Ersatz: keiner nötig (nur Funktionen).

const float PI = 3.14159265359;
const float R_EARTH = 6371008.8;

// ------------------------------------------------------------ Zufall und Rauschen
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Wertrauschen 0..1
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i), b = hash12(i + vec2(1, 0)), c = hash12(i + vec2(0, 1)), d = hash12(i + vec2(1, 1));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int oct) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < oct; i++) { s += a * vnoise(p); p = p * 2.03 + vec2(17.1, 9.7); a *= 0.5; }
    return s;
}

float fadeOut(float high, float low, float x) {
    return 1.0 - smoothstep(low, high, x);
}

// ------------------------------------------------------------ Erdkrümmung
// Die Szene liegt in einer Tangentialebene. Ein Punkt in waagerechter Entfernung d von der Kamera
// liegt um d²/2R tiefer (100 km: 785 m). Gelände, Schatten und Meer rechnen dieselbe Absenkung.
uniform float uCurve = 1.0;      // 1 an, 0 aus (Prüfbilder)
vec3 curved(vec3 p, vec3 cam) {
    vec2 d = p.xz - cam.xz;
    return vec3(p.x, p.y - uCurve * dot(d, d) / (2.0 * R_EARTH), p.z);
}

// ------------------------------------------------------------ Himmelstabelle
// Die Himmelstabelle (sky LUT) speichert die Strahldichte des Himmels über dem Horizont:
// u = Richtung (0 Norden, im Uhrzeigersinn), v = Wurzel der Höhe (feiner am Horizont).
vec2 skyUV(vec3 d) {
    float az = atan(d.x, -d.z);
    float el = asin(clamp(d.y, 0.0, 1.0));
    return vec2(fract(az / (2.0 * PI)), sqrt(el / (0.5 * PI)));
}

vec3 skyDir(vec2 uv) {
    float az = uv.x * 2.0 * PI;
    float el = uv.y * uv.y * 0.5 * PI;
    return vec3(cos(el) * sin(az), sin(el), -cos(el) * cos(az));
}

// Mittleres Himmelslicht auf eine Fläche mit Normale n (grob: Zenit und Horizont gemischt).
vec3 skyIrradiance(sampler2D lut, vec3 n, vec3 sunDir) {
    vec3 zen = textureLod(lut, vec2(0.0, 0.98), 2.0).rgb;
    vec2 hz = vec2(fract(atan(n.x, -n.z) / (2.0 * PI)), 0.25);
    vec3 side = textureLod(lut, hz, 3.0).rgb;
    vec3 sideSun = textureLod(lut, vec2(fract(atan(sunDir.x, -sunDir.z) / (2.0 * PI)), 0.25), 3.0).rgb;
    vec3 hor = mix(side, sideSun, 0.35);
    float up = 0.5 + 0.5 * n.y;
    // Licht vom Boden zurückgeworfen (Basalt ist dunkel: Albedo rund 0,10)
    vec3 bounce = 0.10 * zen * (1.0 - up);
    return PI * (mix(hor, zen, up) * up * 0.9 + bounce);
}

// ------------------------------------------------------------ Luftperspektive
uniform vec3 uBetaR;           // Rayleigh-Streuung am Meeresspiegel je Meter, RGB
uniform float uBetaM;          // Dunst (Mie) am Meeresspiegel je Meter
uniform vec4 uWeather;         // Regen, Nässe, Regenbogen, Bedeckung (alle 0..1)
uniform vec4 uMist;            // Bodennebel: Dichte, Schichthöhe (m), Höhe des Auges (m), unbenutzt

// Optische Tiefe von exp(-y/H) entlang eines Strahls y = y0 + dy·t, t = 0..len (y unter 0 zählt wie 0).
// Als Differenz zweier fallender Exponentialfunktionen gerechnet: bleibt auch aus 100 km Höhe endlich.
float layerDepth(float y0, float dy, float len, float H) {
    float y1 = max(y0 + dy * len, 0.0);
    y0 = max(y0, 0.0);
    if (abs(y1 - y0) < 1e-3 * H) return exp(-y0 / H) * len;
    return (exp(-y0 / H) - exp(-y1 / H)) * len * H / (y1 - y0);
}

// Für den Bodennebel (mit Teilung an y = 0, falls eine Kamera unter dem Wasser rechnet)
float mistDepth(float y0, float dy, float len, float H) {
    float y1 = y0 + dy * len;
    if (y0 >= 0.0 && y1 >= 0.0) return layerDepth(y0, dy, len, H);
    if (y0 <= 0.0 && y1 <= 0.0) return layerDepth(-y0, -dy, len, H);
    float t = -y0 / dy;
    if (y0 > 0.0) return layerDepth(y0, dy, t, H) + layerDepth(0.0, -dy, len - t, H);
    return layerDepth(-y0, -dy, t, H) + layerDepth(0.0, dy, len - t, H);
}

// Luft zwischen Auge (Höhe camY) und einem Punkt in Richtung dir, Entfernung dist: Rayleigh mit
// Skalenhöhe 8 km, Dunst mit 1,2 km. Vom Mauna Kea aus ist die Luft klar, über der Küste diesig.
vec3 aerial(vec3 color, vec3 dir, float dist, sampler2D lut, float camY) {
    float dR = layerDepth(camY, dir.y, dist, 8000.0);
    float dM = layerDepth(camY, dir.y, dist, 1200.0);
    // am Rand der Welt (ab 140 km) ganz im Dunst, damit keine Kante zu sehen ist
    vec3 T = exp(-(uBetaR * dR + uBetaM * 1.11 * dM)) * (1.0 - smoothstep(140000.0, 210000.0, dist));
    vec2 uv = skyUV(normalize(vec3(dir.x, max(dir.y, 0.0) + 0.02, dir.z)));
    vec3 inscat = textureLod(lut, uv, 3.0).rgb;
    vec3 res = color * T + inscat * (1.0 - T);
    if (uMist.x > 0.002) {
        float od = uMist.x * 0.014 * mistDepth(uMist.z, dir.y, dist, uMist.y);
        float Tm = exp(-min(od, 24.0));
        vec3 mc = mix(inscat, vec3(dot(inscat, vec3(0.3333))), 0.30) * 1.05;
        res = res * Tm + mc * (1.0 - Tm);
    }
    return res;
}

// ------------------------------------------------------------ Farben
vec3 srgbToLinear(vec3 c) { return pow(c, vec3(2.2)); }

// Oberfläche: Grundfarbe, Rauheit, Metall, Normale im Tangentenraum
struct Surf {
    vec3 albedo;
    float rough;
    float metal;
    vec3 nT;
};

// ------------------------------------------------------------ Schnitt (Phase 6)
// Eine senkrechte Ebene klappt den Berg auf: alles auf der Seite der Kamera (side > 0) und innerhalb
// des Abschnitts entlang der Ebene (s0..s1) wird nicht gezeichnet; dahinter steht die Schnittfläche.
uniform vec4 uCutPlane = vec4(0.0);   // Normale (x, z) zur Kamera hin, Abstand, an (1)
uniform vec4 uCutAxis = vec4(0.0);    // Richtung entlang der Ebene (x, z), s0, s1
uniform float uCutWidth = 40000.0;    // so weit reicht der herausgenommene Block zur Kamera hin
bool cutAway(vec3 p) {
    if (uCutPlane.w < 0.5) return false;
    float side = dot(uCutPlane.xy, p.xz) + uCutPlane.z;
    float s = dot(uCutAxis.xy, p.xz);
    return side > 0.0 && side < uCutWidth && s > uCutAxis.z && s < uCutAxis.w;
}
