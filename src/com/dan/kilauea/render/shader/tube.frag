// tube.frag — Wände der Lavaröhre: dunkler Basalt mit waagerechten Bänken (frühere Füllstände), glasige
// Tropfglasur an der Decke, Wurzeln der ʻōhiʻa, die durch Risse hängen, und ein festgetretener Weg.
// Licht: Lampen am Weg (warm, mit Abstand quadratisch schwächer) und Tageslicht von den Öffnungen.
// Eingaben: vPos, vWorld, vNormal, vSV. Ausgabe: HDR-Farbe. Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
in vec3 vPos;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vSV;
out vec4 frag;
uniform vec3 uCamPos;
uniform float uExposure = 1.0;
uniform vec4 uLamp[24];
uniform int uLampCount = 0;
uniform vec3 uMouthA, uMouthB;

void main() {
    vec3 n = normalize(vNormal);
    vec3 w = vWorld;
    bool floorPt = vSV.y < -0.5;
    float s = vSV.x;
    // Bänke und Glasur
    // Rauschen nur aus Bogenlänge und Weltlage: die Lage im Ring (vSV.y) springt an der Naht von 1 auf 0
    // und zeichnete dort Streifen längs der Röhre
    vec2 lat = vec2(s + (w.x + w.z) * 0.37, w.y);
    // Fließmarken früherer Füllstände: wenige, unregelmäßige Leisten nur an den Wänden. Waagerechte
    // Linien laufen die Röhre entlang und stehen im Bild als Strahlen zum Ausgang; dicht und hart
    // gesetzt (alle 0,9 m) zerlegten sie die Röhre in Fächer (Statusbilder).
    float yy = w.y + 0.6 * vnoise(vec2(s * 0.08, 1.7));
    float ledge = smoothstep(0.08, 0.0, abs(fract(yy / 2.3) - 0.5) - 0.42) * smoothstep(0.55, 0.2, abs(n.y))
                * smoothstep(0.35, 0.6, vnoise(vec2(s * 0.12, floor(yy / 2.3))));
    float band = 0.5 + 0.5 * (vnoise(vec2(s * 0.3, yy * 0.8)) - 0.5);
    vec3 a = mix(vec3(0.040, 0.036, 0.033), vec3(0.055, 0.047, 0.040), band);
    a *= 1.0 - 0.3 * ledge;
    a *= 0.75 + 0.5 * fbm(lat * vec2(0.8, 1.6), 3);
    float glaze = smoothstep(0.3, 0.9, n.y * -1.0) * smoothstep(0.45, 0.75, vnoise(lat * vec2(2.0, 3.2)));
    float rough = mix(0.75, 0.25, glaze);
    // rötliche Oxidation in Flecken, Wurzeln an der Decke
    a = mix(a, vec3(0.08, 0.04, 0.025), smoothstep(0.65, 0.85, vnoise(lat * vec2(0.3, 0.55))) * 0.6);
    float root = smoothstep(0.985, 1.0, vnoise(lat * vec2(3.0, 7.5))) * smoothstep(-0.2, -0.7, n.y);
    a = mix(a, vec3(0.09, 0.06, 0.04), root);
    if (floorPt) {
        a = vec3(0.055, 0.050, 0.045) * (0.85 + 0.3 * fbm(w.xz * 3.0, 3));
        rough = 0.85;
        n = vec3(0.0, 1.0, 0.0);
    }
    // feine Unruhe der Wand
    vec2 g = vec2(vnoise(w.xz * 4.0 + w.y) - 0.5, vnoise(w.zx * 4.0 - w.y) - 0.5) * 0.35;
    if (!floorPt) n = normalize(n + vec3(g.x, 0.0, g.y));

    vec3 V = normalize(uCamPos - vPos);
    vec3 c = vec3(0.0);
    // Lampen: warmweiß, rund 60 Lux in 2 m (Sonne: rund 100 000)
    for (int i = 0; i < 24; i++) {
        if (i >= uLampCount) break;
        vec3 L = uLamp[i].xyz - w;
        float d2 = dot(L, L);
        vec3 l = L * inversesqrt(d2);
        float nl = max(dot(n, l), 0.0);
        vec3 h = normalize(l + V);
        float spec = pow(max(dot(n, h), 0.0), mix(8.0, 80.0, 1.0 - rough)) * (1.0 - rough) * 0.5;
        c += vec3(1.0, 0.78, 0.52) * 0.12 * uExposure * (a / PI * nl + spec * nl) / (d2 + 0.3);
    }
    // Tageslicht durch die Öffnungen
    float dA = distance(w, uMouthA), dB = distance(w, uMouthB);
    float day = exp(-dA / 7.0) + exp(-dB / 7.0);
    vec3 E = skyIrradiance(uSkyLut, n, uSunDir);
    c += a / PI * E * day * 0.35;
    float sv = sunShadow(vPos, n, uCamPos, gl_FragCoord.xy);
    c += a / PI * uSunColor * max(dot(n, uSunDir), 0.0) * sv * day;
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    frag = vec4(c, 1.0);
}
