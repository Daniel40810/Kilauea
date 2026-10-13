// thermal.frag — Wärmebild (Phase 14): jeder Bildpunkt bekommt aus Tiefe und Modell eine Temperatur und die
// Farbe der Skala (monitor/Thermal.java, dieselben Zahlen). Eingaben: Tiefe der Szene (uSceneDepth), Teilchen
// der Fontänen (uHeat: größte Temperatur, Deckung), Wolken (uClouds, Durchlass in a), Lava und See (lava.glsl,
// uLakeTex), Gelände (terrain_height.glsl: Bewuchs, Alter). Ausgabe: fertige Farbe (sRGB, ohne Belichtung) oder,
// mit uProbe = 1, die Messung im Kreuz: (Temperatur °C, Art) in einen 1 × 1-Speicher.
// Regeln: Lava nach Oberfläche und Kern mit offenen Rissen, gemischt nach Stefan–Boltzmann [A]; Boden aus der
// Luft nach Höhe (24 °C an der Küste, 6,5 K/km) und der Sonne auf dunklem Gestein [G]; Wald nah an der Luft [G];
// Meer 25 °C [L], am Eintritt wärmer [G]; Dampfstellen rund 70 °C [G]; Himmel kalt, Wolken wie die Luft [G].
// Dampf und Vog fehlen absichtlich: langwelliges Infrarot sieht durch dünne Fahnen. Stufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lava.glsl"
#include "terrain_height.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uSceneDepth;
uniform sampler2D uHeat;           // RG16F: Temperatur der heißesten Teilchen (°C), Deckung
uniform sampler2D uClouds;         // Wolken in halber Auflösung, Durchlass in a
uniform int uCloudsOn = 0;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform int uZeroOne = 1;
uniform vec3 uSunDir;
uniform sampler2D uLakeTex;        // wie lake.frag: R Naht, G Alter der Kruste, B Platte, A auf dem See
uniform vec4 uLakeTexRect;
uniform float uLakeY = -1.0e9;
uniform vec4 uSulfur[8];
uniform int uSulfurCount = 0;
uniform vec4 uEntry[8];
uniform int uEntryCount = 0;
uniform float uBars = 0.0;
uniform int uProbe = 0;            // 1: nur die Messung im Kreuz ausgeben
uniform vec2 uProbeUV = vec2(0.5);
uniform vec2 uScreen = vec2(1920.0, 1080.0);

const int SKY = 0, GROUND = 1, FOREST = 2, LAVA = 3, LAKE = 4, SEA = 5, VENT = 6, FOUNTAIN = 7, CLOUD = 8, LIFE = 9;

float scaleT(float t) {
    if (t <= 60.0) return max(0.0, 0.45 * (t + 20.0) / 80.0);
    return min(1.0, 0.45 + 0.55 * log(1.0 + (t - 60.0) / 40.0) / log(1.0 + 1140.0 / 40.0));
}

vec3 ironbow(float u) {
    const vec4 S[7] = vec4[](vec4(0.00, 0.00, 0.00, 0.03), vec4(0.18, 0.12, 0.03, 0.42), vec4(0.36, 0.50, 0.05, 0.58),
        vec4(0.52, 0.86, 0.20, 0.30), vec4(0.68, 0.98, 0.48, 0.05), vec4(0.86, 1.00, 0.82, 0.22), vec4(1.00, 1.00, 1.00, 0.92));
    u = clamp(u, 0.0, 1.0);
    for (int i = 1; i < 7; i++)
        if (u <= S[i].x) return mix(S[i - 1].yzw, S[i].yzw, (u - S[i - 1].x) / (S[i].x - S[i - 1].x));
    return S[6].yzw;
}

float kelvin4(float c) { float k = c + 273.15; return k * k * k * k; }
float fromK4(float v) { return pow(max(v, 1.0), 0.25) - 273.15; }
// Helligkeitstemperatur einer Fläche aus Kruste und Glut, Anteil a offen
float bright(float crust, float glow, float a) { return fromK4(mix(kelvin4(crust), kelvin4(glow), clamp(a, 0.0, 1.0))); }

float airT(float h) { return 24.0 - 0.0065 * max(h, 0.0); }

vec3 worldAt(vec2 suv, float z) {
    float zn = uZeroOne == 1 ? z : z * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(suv * 2.0 - 1.0, zn, 1.0);
    return a.xyz / a.w;
}

float sulfurAt(vec2 p) {
    float s = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= uSulfurCount) break;
        vec4 q = uSulfur[i];
        float d = length(p - q.xy) + 25.0 * (vnoise(p * 0.03 + float(i)) - 0.5);
        float v = q.w > 0.0 ? smoothstep(q.w, 0.0, abs(d - q.z)) : smoothstep(q.z, q.z * 0.3, d);
        s = max(s, v);
    }
    return s;
}

// Temperatur (°C) und Art an einer Stelle des Bildes
vec2 measure(vec2 uv) {
    float z = texture(uSceneDepth, uv).r;
    bool bg = uZeroOne == 1 ? z < 1e-7 : z > 0.9999999;
    vec3 P = worldAt(uv, bg ? (uZeroOne == 1 ? 1e-6 : 0.999999) : z);
    vec3 dir = normalize(P - uCamPos);
    // Neigung der Fläche aus den Nachbarn im Bild (für die Sonne), vor jeder Verzweigung
    vec3 n = normalize(cross(dFdx(P), dFdy(P)));
    if (dot(n, -dir) < 0.0) n = -n;
    if (uProbe == 1 || any(isnan(n))) n = vec3(0.0, 1.0, 0.0);
    float T;
    int kind;
    float sun = max(uSunDir.y, 0.0);
    float day = smoothstep(-0.05, 0.25, uSunDir.y);
    float overcast = uWeather.w;
    if (bg) {
        // klarer Himmel: am Zenit rund −40 °C, zum Horizont hin wie die Luft
        float el = max(dir.y, 0.0);
        T = mix(airT(uCamPos.y) - 4.0, -40.0, pow(el, 0.35));
        T = mix(T, airT(uCamPos.y) - 6.0, overcast * 0.7);
        kind = SKY;
    } else {
        float dist = length(P - uCamPos);
        // Erdkrümmung zurückrechnen: die Szene ist um d²/2R abgesenkt gezeichnet
        vec2 d2 = P.xz - uCamPos.xz;
        float y = P.y + uCurve * dot(d2, d2) / (2.0 * R_EARTH);
        vec2 w = P.xz;
        float ground = solidHeight(w, dist);
        float air = airT(y);
        vec2 guv = gridCoord(w) / uGridN;
        vec4 cov = textureLod(uCover, guv, 0.0);
        vec4 geo = textureLod(uGeo, guv, 0.0);
        float young = youngOf(lavaAge(geo.x));
        if (y < 0.6 && ground < -0.5) {
            T = 25.0;
            kind = SEA;
            for (int i = 0; i < 8; i++) {
                if (i >= uEntryCount) break;
                float de = length(w - uEntry[i].xy);
                T = max(T, 25.0 + 45.0 * exp(-de / (30.0 + 4.0 * sqrt(max(uEntry[i].z, 0.0)))));
            }
        } else {
            // Boden: Luft, dazu Sonne auf dunklem, jungem Gestein; Wald kaum wärmer; nachts kälter
            float veg = cov.x;
            float absorb = mix(0.55, 1.0, young) * (1.0 - 0.8 * veg);
            float heat = 30.0 * absorb * max(dot(n, uSunDir), 0.0) * day * (1.0 - 0.75 * overcast);
            T = air + heat - 4.0 * (1.0 - day);
            kind = veg > 0.45 ? FOREST : GROUND;
            if (y > ground + 2.5) { T = air + 2.0 * day - 2.0 * (1.0 - day); kind = FOREST; }   // Kronendach
            float sf = uSulfurCount > 0 ? sulfurAt(w) : 0.0;
            if (sf > 0.05) {
                float vent = sf * smoothstep(0.45, 0.75, fbm(w * 0.09, 4) + 0.1 * vnoise(w * 0.6));
                if (vent > 0.15) { T = mix(T, 72.0, vent); kind = VENT; }
            }
            // Lava des Modells
            LavaS L = lavaSample(w);
            if (L.cover > 0.15 && abs(y - L.top) < 6.0) {
                float share = crackShare(L.surf, L.core);
                if ((L.flags & LAVA_SKY) != 0) share = max(share, 0.6);
                float tl = bright(L.surf, L.core, share);
                T = mix(T, max(T, tl), smoothstep(0.15, 0.55, L.cover));
                if (tl > T - 1.0 || tl > 40.0) kind = LAVA;
            }
            // Lavasee
            if (uLakeOn == 1 && abs(y - uLakeY) < 4.0 && lakeShape(w) < 1.06) {
                vec4 t = texture(uLakeTex, (w - uLakeTexRect.xy) / uLakeTexRect.z);
                float age = t.g * t.g * 7200.0;
                float surfT = 20.0 + 1130.0 / (1.0 + pow(max(age, 0.0) / 10.0, 0.25));
                float open = max(crackShare(surfT, 1150.0), smoothstep(0.05, 0.6, t.r) * t.a);
                open = max(open, smoothstep(0.965, 1.0, lakeShape(w)) * 0.5);
                T = bright(surfT, 1150.0, open);
                kind = LAKE;
            }
        }
    }
    // Teilchen der Fontänen davor
    vec2 ht = texture(uHeat, uv).rg;
    if (ht.y > 0.02 && ht.x > T) {
        T = fromK4(mix(kelvin4(T), kelvin4(ht.x), clamp(ht.y, 0.0, 1.0)));
        if (ht.y > 1.5) kind = LIFE;            // Phase 15: Figuren schreiben die Deckung 2
        else if (ht.y > 0.3) kind = FOUNTAIN;
    }
    // dichte Wolken verdecken; Dunst, Vog und dünne Schleier lässt das langwellige Infrarot durch [A]
    if (uCloudsOn == 1) {
        float trans = texture(uClouds, uv).a;
        float k = clamp((0.6 - trans) / 0.6, 0.0, 1.0);
        k *= k;
        if (k > 0.0) T = fromK4(mix(kelvin4(T), kelvin4(airT(1400.0) - 2.0), k));
        if (k > 0.6 && T < 100.0) kind = CLOUD;   // nur, wenn die Wolke die Messung bestimmt
    }
    return vec2(T, float(kind));
}

void main() {
    if (uProbe == 1) {
        frag = vec4(measure(uProbeUV), 0.0, 1.0);
        return;
    }
    vec2 m = measure(vUV);
    vec3 c = ironbow(scaleT(m.x));
    // Messkreuz in der Mitte
    vec2 px = (vUV - 0.5) * uScreen;
    float r = length(px);
    float arm = step(abs(px.x), 0.8) * step(6.0, abs(px.y)) * step(abs(px.y), 22.0) + step(abs(px.y), 0.8) * step(6.0, abs(px.x)) * step(abs(px.x), 22.0);
    float ring = smoothstep(1.3, 0.3, abs(r - 13.0));
    float mark = clamp(arm + ring, 0.0, 1.0);
    c = mix(c, vec3(1.0), mark * 0.85);
    c = mix(c, vec3(0.0), clamp(step(abs(px.x), 2.0) * step(6.0, abs(px.y)) * step(abs(px.y), 23.0) + step(abs(px.y), 2.0) * step(6.0, abs(px.x)) * step(abs(px.x), 23.0), 0.0, 1.0) * (1.0 - mark) * 0.5);
    if (vUV.y < uBars || vUV.y > 1.0 - uBars) c = vec3(0.0);
    frag = vec4(c, dot(c, vec3(0.299, 0.587, 0.114)));
}
