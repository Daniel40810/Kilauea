// road.frag — Belag der Parkstraßen: alter Asphalt mit Flicken und Rissen, gelbe Doppellinie in der
// Mitte, weiße Randlinien, Bankett aus Lavasplitt, das in das Gelände übergeht. Linien werden aus der
// Ferne zu ihrer mittleren Farbe gemittelt (kein Flimmern).
// Eingaben: vPos, vWorld, vUS (Querlage, Bogenlänge), vNormal. Ausgabe: HDR-Farbe.
// Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lighting.glsl"
#include "cars.glsl"
#include "shadow.glsl"
in vec3 vPos;
in vec3 vWorld;
in vec2 vUS;
in vec3 vNormal;
in float vLava;
out vec4 frag;
uniform vec3 uCamPos;
uniform float uPaved, uHalf;
uniform int uKind = 0;         // 0 Straße, 1 Pfad (Erde, Splitt, Fußspuren), 2 befestigter Weg
uniform vec3 uAsphalt, uWorn, uVerge, uLineWhite, uLineYellow;

// Anteil eines Streifens [a, b] im Bildpunkt (gemittelt über die Breite fu)
float stripe(float u, float a, float b, float fu) {
    float lo = clamp((u - a) / fu + 0.5, 0.0, 1.0), hi = clamp((u - b) / fu + 0.5, 0.0, 1.0);
    return lo - hi;
}

void main() {
    if (vLava > 0.35) discard;
    if (cutAway(vPos)) discard;
    float u = vUS.x, s = vUS.y, au = abs(u);
    vec2 p = vWorld.xz;
    float fu = max(fwidth(u), 1e-3);
    // Asphalt: in den Fahrspuren abgefahren, Flicken, feine Risse
    float wear = smoothstep(0.4, 0.9, fbm(p * 0.08, 3)) * 0.6 + 0.4 * smoothstep(0.5, 1.4, abs(au - 1.5) < 0.6 ? 1.0 : 0.0);
    vec3 a = mix(uAsphalt, uWorn, wear * 0.6);
    a *= 0.85 + 0.3 * vnoise(p * 3.0);
    float crack = smoothstep(0.04, 0.0, abs(vnoise(p * 0.7 + 3.0) - 0.5)) * smoothstep(0.5, 0.05, fu);
    a *= 1.0 - 0.35 * crack;
    // Markierung: gelbe Doppellinie (je 10 cm, 10 cm Abstand), weiße Randlinien
    float yl = stripe(au, 0.05, 0.15, fu);
    float wl = stripe(au, uPaved - 0.32, uPaved - 0.20, fu);
    vec3 yC = uLineYellow * (0.7 + 0.3 * vnoise(p * 1.5)), wC = uLineWhite * (0.7 + 0.3 * vnoise(p * 1.5 + 9.0));
    if (uKind == 0) {
        a = mix(a, yC, yl);
        a = mix(a, wC, wl);
    } else if (uKind == 1) {
        // Pfad: festgetretene Erde und Splitt, in der Mitte heller und glatter
        a = mix(uWorn, uAsphalt, smoothstep(0.0, uPaved, au)) * (0.8 + 0.4 * fbm(p * 2.0, 3));
    }
    // Bankett: Splitt, zum Rand in den Boden übergehend
    float v = smoothstep(uPaved, uPaved + 0.15, au);
    vec3 vc = uVerge * (0.8 + 0.4 * fbm(p * 0.6, 3));
    // Pfade laufen am Rand in den Boden aus (Splitt verliert sich), Straßen enden am Bankett
    if (uKind != 0 && au > uPaved) discard;
    a = mix(a, vc, v);
    float rough = mix(0.8, 0.95, v);
    Surf sf;
    sf.albedo = a;
    sf.rough = rough;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    vec3 n = normalize(vNormal);
    float sv = sunShadow(vPos, n, uCamPos, gl_FragCoord.xy);
    vec3 V = normalize(uCamPos - vPos);
    vec3 c = shadeSurface(sf, n, V, 1.0, sv);
    c += sf.albedo / PI * carLight(vPos, n);   // Phase 15: Scheinwerfer
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    frag = vec4(c, 1.0);
}
