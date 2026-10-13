// fountain.frag — Glut der Fontänenteilchen nach ihrer Temperatur (Planck, lava.glsl), weich zum Rand;
// Peles Haar als goldener Glanz im Sonnen- und Himmelslicht. Ausgabe: zum Aufaddieren (Licht); mit THERMAL
// (Phase 14) Temperatur und Deckung für das Wärmebild.
#include "common.glsl"
#include "lighting.glsl"
#include "lava.glsl"
in vec2 vUV;
in float vTemp;
in vec3 vPos;
in float vCore;
out vec4 frag;
uniform vec3 uCamPos;
void main() {
    float r = length(vec2(vUV.x, vUV.y * 0.6));
    float a = smoothstep(1.0, 0.2, r);
    // Kern: dicht, aber nicht ganz deckend, mit Fetzen
    if (vCore > 0.5) a = smoothstep(1.0, 0.0, r) * (0.25 + 0.2 * vnoise(vUV * 3.0 + vPos.y * 0.05)) ;
    if (a <= 0.0) discard;
#ifdef THERMAL
    // Phase 14, Wärmebild: Temperatur und Deckung des Teilchens (Mischung MAX); Peles Haar ist kalt
    frag = vec4(vTemp < 0.0 ? 0.0 : vTemp, vTemp < 0.0 ? 0.0 : min(1.0, a * (vCore > 0.5 ? 1.6 : 1.0)), 0.0, 1.0);
    return;
#endif
    vec3 c;
    if (vTemp < 0.0) {
        // Peles Haar: dünne Glasfäden, golden; leuchten nur im Licht
        vec3 E = skyIrradiance(uSkyLut, vec3(0.0, 1.0, 0.0), uSunDir);
        c = vec3(0.55, 0.42, 0.18) * (uSunColor * 0.25 + E / PI) * 0.4 * a;
    } else {
        c = glowRGB(vTemp) * uLavaExposure * a;
    }
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    // nur die Lufttrübung, ohne zusätzliches Himmelslicht (die Teilchen werden addiert)
    c *= exp(-dist * (uBetaM + 0.5 * dot(uBetaR, vec3(0.333))));
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);   // nie NaN in den Bloom
    frag = vec4(c, 0.0);
}
