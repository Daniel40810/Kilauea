// clouds.frag — Passatwolken, Vog, Regenschleier und Lichtstrahlen (Phase 11), in halber Auflösung.
// Der Blick wird durch die Wolkenschicht (Untergrenze bis Passatinversion) gerechnet: Dichte aus der
// Wetterkarte und einer 3D-Rauschtextur, Licht von Sonne und Mond mit Schatten in der Wolke (drei Proben
// zur Sonne), Himmelslicht und von unten die Glut der Lava. Unter den Wolken: Vog (gelbgrauer Dunst aus der
// Gasfahne), Regenschleier und Licht, das zwischen den Wolken hindurchfällt.
// Ausgabe: Streulicht (vorbelichtet) und Durchlässigkeit. Qualitätsstufe: Schritte nach uSteps.
#include "common.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "lava.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uSceneDepth;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform float uFrame = 0.0;
uniform int uSteps = 28;
// Phase 15 (Statusbild 14): im Wärmebild zählt die Durchlässigkeit nur die Wolken. Vog und Regen lässt das
// langwellige Infrarot weitgehend durch; aus 5,8 km Höhe bestimmte der lange Weg durch den Vog sonst die
// Messung, und die ganze Insel lag „in der Wolke“.
uniform float uVogInT = 1.0;

vec3 worldAt(vec2 suv, float z) {
    float zn = uZeroOne == 1 ? z : z * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(suv * 2.0 - 1.0, zn, 1.0);
    return a.xyz / a.w;
}

float hg(float mu, float g) { return (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * mu, 1.5)); }

void main() {
    float z = texture(uSceneDepth, vUV).r;
    bool bg = uZeroOne == 1 ? z < 1e-7 : z > 0.9999999;
    vec3 far = worldAt(vUV, bg ? (uZeroOne == 1 ? 1e-6 : 0.999999) : z);
    vec3 dir = normalize(far - uCamPos);
    float tScene = bg ? 1e9 : length(far - uCamPos);
    float jit = hash12(gl_FragCoord.xy);   // fest je Bildpunkt: ohne zeitliche Glättung würde ein wechselnder Versatz flimmern
    float mu = dot(dir, uSunDir);
    vec3 sky = skyIrradiance(uSkyLut, vec3(0.0, 1.0, 0.0), uSunDir) / PI;
    vec3 scat = vec3(0.0);
    float T = 1.0;
    float base = uCloudLayer.x, top = uCloudLayer.y;

    // ---- unter der Passatinversion: Vog, Regen, Lichtstrahlen (8 Proben im Teil des Blicks unter der Obergrenze;
    // Vog und Regen liegen in der durchmischten Luft darunter, auch dort, wo das Gelände höher als die Wolkenbasis ist)
    float tA = 0.0, tB = min(tScene, 1e6);
    if (uCamPos.y >= top) {
        if (dir.y < -1e-4) tA = (uCamPos.y - top) / -dir.y; else tB = -1.0;
    } else if (dir.y > 1e-4) tB = min(tB, (top - uCamPos.y) / dir.y);
    tB = min(tB, tA + 25000.0);
    if (tB > tA && uWeatherRect.w > 0.5) {
        float ds = (tB - tA) / 8.0;
        for (int i = 0; i < 8; i++) {
            float t = tA + (float(i) + jit) * ds;
            vec3 q = uCamPos + dir * t;
            vec4 w = weatherAt(q.xz);
            // Vog liegt durchmischt unter der Inversion; dicht wie an Vog-Tagen in Kona (Sicht rund 10–20 km) [G]
            float vog = w.b * 4.0e-4 * smoothstep(top + 200.0, top - 600.0, q.y);
            float rain = w.g * 2.2e-4 * smoothstep(top, base, q.y);
            float sh = cloudShadow(q, uSunDir);
            float sig = vog + rain;
            vec3 S = uSunColor * sh * (hg(mu, 0.7) * vec3(1.0, 0.9, 0.7) * vog + 0.08 * rain)
                   + sky * (vog * vec3(0.95, 0.85, 0.65) + rain * 0.55)
                   // Lichtstrahlen: der Dunst der Luft ist schon im Bild; wo Wolken die Sonne verdecken, fehlt
                   // sein Licht, so werden die Strahlen zwischen den Wolken sichtbar
                   - uSunColor * (1.0 - sh) * uBetaM * exp(-max(q.y, 0.0) / 1200.0) * min(hg(mu, 0.76), 2.0) * 0.5;
            float tr = exp(-sig * ds * uVogInT);
            scat += T * S * ds;
            T *= tr;
        }
    }

    // ---- die Wolkenschicht
    float t0 = 0.0, t1 = -1.0;
    if (abs(dir.y) < 1e-4) {
        if (uCamPos.y > base && uCamPos.y < top) { t0 = 0.0; t1 = 30000.0; }
    } else {
        float ta = (base - uCamPos.y) / dir.y, tb = (top - uCamPos.y) / dir.y;
        t0 = max(min(ta, tb), 0.0);
        t1 = max(ta, tb);
    }
    // über der Schicht zählt nur der Weg ab dem Eintritt (Blick aus dem Flugzeug oder aus dem All)
    float above = uCamPos.y > top && dir.y < -1e-4 ? (uCamPos.y - top) / -dir.y : 0.0;
    t1 = min(t1, min(tScene, t0 + 45000.0));
    if (t1 > t0 && T > 0.02 && uWeatherRect.w > 0.5) {
        int n = uSteps;
        float ds = (t1 - t0) / float(n);
        ds = max(ds, 25.0);
        vec3 moonL = uMoonColor;
        for (int i = 0; i < 64; i++) {
            if (i >= n) break;
            float t = t0 + (float(i) + jit) * ds;
            if (t > t1) break;
            vec3 p = uCamPos + dir * t;
            float d = cloudDensity(p);
            if (d < 0.002) continue;
            float sig = d * 0.012;
                        float od = 0.0;
            // vier Proben zur Sonne, wachsende Schritte (60, 180, 420, 900 m): dicke Wolken bekommen graue Böden
            od += cloudDensity(p + uSunDir * 60.0) * 0.012 * 120.0;
            od += cloudDensity(p + uSunDir * 180.0) * 0.012 * 120.0;
            od += cloudDensity(p + uSunDir * 420.0) * 0.012 * 360.0;
            od += cloudDensity(p + uSunDir * 900.0) * 0.012 * 600.0;
            float Tl = exp(-od) * (uSunDir.y > -0.05 ? 1.0 : 0.0);
            float powder = 1.0 - exp(-sig * 160.0);
            float ph = mix(hg(mu, 0.55), hg(mu, -0.25), 0.3);
            float hh = clamp((p.y - base) / (top - base), 0.0, 1.0);
            vec3 S = uSunColor * Tl * ph * (0.6 + 0.4 * powder) * 2.2
                   + moonL * hg(dot(dir, uMoonDir), 0.55) * 1.5
                   + sky * (0.35 + 0.65 * hh)
                   + lavaLight(p.xz) * uLavaExposure * exp(-max(p.y - base, 0.0) / 300.0) * 0.35 / PI;   // Glut von unten
            float tr = exp(-sig * ds);
            scat += T * S * (1.0 - tr) * 0.9;
            T *= tr;
            if (T < 0.02) break;
        }
        // ferne Wolken verblassen im Dunst
        float f = exp(-max(t0 - above, 0.0) / 60000.0);
        scat *= f;
        T = mix(1.0, T, f);
    }
    if (any(isnan(scat)) || isnan(T)) { scat = vec3(0.0); T = 1.0; }
    frag = vec4(scat, T);
}
