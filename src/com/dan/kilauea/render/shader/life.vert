// life.vert — Figuren als Instanzen (Phase 15): Nēnē, Vögel, Hubschrauber, Wanderer, Autos.
// Zweck: Teile bewegen (Flügel schlagen, Beine und Arme schwingen, Hals neigt sich, Rotoren drehen),
// dann die Figur kippen, rollen, drehen, skalieren und auf ihren Platz stellen (mit Erdkrümmung).
// Eingaben: aPos, aNormal, aColor (rgb, Teil in a), aPivot (Drehpunkt des Teils); je Figur aI0 (x, y, z, Gier),
// aI1 (Maßstab, Neigung Nase hoch +, Rollen rechts tief +, Takt), aI2 (Farbe für den Platzhalter 1,0,1),
// aI3 (Flügel, Schritt, Hals, Licht). Mit DEPTH_ONLY nur die Lage für die Schattenkarte.
// Ausgaben: vPos, vNormal, vColor, vEmit (Licht), vHot (heißes Teil). Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aColor;
layout(location = 3) in vec3 aPivot;
layout(location = 4) in vec4 aI0;
layout(location = 5) in vec4 aI1;
layout(location = 6) in vec4 aI2;
layout(location = 7) in vec4 aI3;

uniform vec3 uCamPos;
#ifdef DEPTH_ONLY
uniform mat4 uLightVP;
#else
uniform mat4 uViewProj;
out vec3 vPos;
out vec3 vNormal;
out vec3 vColor;
out float vEmit;
out float vHot;
#endif

vec3 rotX(vec3 v, float a) { float c = cos(a), s = sin(a); return vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c); }
vec3 rotY(vec3 v, float a) { float c = cos(a), s = sin(a); return vec3(c * v.x - s * v.z, v.y, s * v.x + c * v.z); }
vec3 rotZ(vec3 v, float a) { float c = cos(a), s = sin(a); return vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z); }

void main() {
    int part = int(aColor.a + 0.5);
    vec3 p = aPos, n = aNormal, pv = aPivot;
    float side = pv.x >= 0.0 ? 1.0 : -1.0;
    float ph = aI1.w;
    if (part == 1 || part == 2) {            // Flügel: um die Längsachse am Schultergelenk
        float a = aI3.x * side;
        p = rotZ(p - pv, a) + pv; n = rotZ(n, a);
    } else if (part == 3) {                   // Hauptrotor
        p = rotY(p - pv, ph) + pv; n = rotY(n, ph);
    } else if (part == 4) {                   // Heckrotor
        float a = ph * 5.35;
        p = rotX(p - pv, a) + pv; n = rotX(n, a);
    } else if (part == 5 || part == 6) {      // Beine
        float a = 0.45 * sin(ph) * aI3.y * side;
        p = rotX(p - pv, a) + pv; n = rotX(n, a);
    } else if (part == 9 || part == 10) {     // Arme gegen die Beine
        float a = -0.38 * sin(ph) * aI3.y * side;
        p = rotX(p - pv, a) + pv; n = rotX(n, a);
    } else if (part == 7) {                   // Hals und Kopf: senken (+) oder heben
        p = rotX(p - pv, aI3.z) + pv; n = rotX(n, aI3.z);
    } else if (part == 12) {                  // Schwanzspieße
        float a = 0.12 * sin(ph * 0.9);
        p = rotY(p - pv, a) + pv; n = rotY(n, a);
    }
    // Figur: Maßstab, Neigung (Nase hoch +), Rollen (rechts tief +), Gier
    p *= aI1.x;
    p = rotX(p, -aI1.y); n = rotX(n, -aI1.y);
    p = rotZ(p, aI1.z); n = rotZ(n, aI1.z);
    float c = cos(aI0.w), s = sin(aI0.w);
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    n = vec3(c * n.x - s * n.z, n.y, s * n.x + c * n.z);
    vec3 cp = curved(aI0.xyz + p, uCamPos);
#ifdef DEPTH_ONLY
    gl_Position = uLightVP * vec4(cp, 1.0);
#else
    vNormal = n;
    bool paint = aColor.r > 0.99 && aColor.g < 0.01 && aColor.b > 0.99;
    vColor = paint ? aI2.rgb : aColor.rgb;
    // Licht: Scheinwerfer hell, Rück- und Blitzlicht schwächer (Leuchtdichte in Einheiten der Sonne, vorbelichtet) [G]
    vEmit = part == 8 ? aI3.w * (aColor.g > 0.5 ? 30.0 : 1.5) : 0.0;
    vHot = part == 11 ? 1.0 : (part == 8 ? 0.15 : 0.0);
    vPos = cp;
    gl_Position = uViewProj * vec4(cp, 1.0);
#endif
}
