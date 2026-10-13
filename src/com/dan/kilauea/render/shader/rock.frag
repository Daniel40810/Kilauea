// rock.frag — Basalt der Meeresklippe: Bänke früherer Ströme (dicker und dünner, leicht gewellt), rötlich
// verwitterte Lagen, dunkel und glänzend, wo die Brandung ihn nässt; Salz und Flechten oben heller.
// Eingaben: vPos, vWorld, vNormal. Ausgabe: HDR-Farbe. Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
in vec3 vPos;
in vec3 vWorld;
in vec3 vNormal;
out vec4 frag;
uniform vec3 uCamPos;

void main() {
    if (cutAway(vPos)) discard;
    vec3 V = normalize(uCamPos - vPos);
    vec3 n = normalize(vNormal);
    if (dot(n, V) < 0.0) n = -n;
    vec3 w = vWorld;
    vec2 lat = vec2(w.x + w.z, w.y);
    // Bänke: Dicke 0,8 bis 3 m, Fugen dunkel
    float y = w.y + 0.8 * vnoise(lat * vec2(0.04, 0.0)) + 0.3 * vnoise(lat * 0.2);
    float layer = floor(y / 1.9);
    float f = fract(y / 1.9);
    float joint = smoothstep(0.06, 0.0, f) + smoothstep(0.94, 1.0, f);
    float tone = hash12(vec2(layer, 3.7));
    vec3 a = mix(vec3(0.045, 0.042, 0.040), vec3(0.085, 0.075, 0.065), tone);
    a = mix(a, vec3(0.11, 0.06, 0.04), smoothstep(0.82, 0.95, hash12(vec2(layer, 9.1))) * 0.8);   // oxidierte Lage
    a *= 0.8 + 0.4 * fbm(lat * vec2(0.6, 1.2), 3);
    a *= 1.0 - 0.45 * joint;
    // oben Salz und Flechten, unten nass
    a = mix(a, vec3(0.16, 0.16, 0.15), smoothstep(0.55, 0.9, n.y) * smoothstep(0.4, 0.7, vnoise(w.xz * 0.3)) * 0.5);
    float wet = smoothstep(3.5, 0.5, w.y);
    a *= 1.0 - 0.45 * wet;
    // feine Unruhe der Normale
    vec2 g = vec2(vnoise(lat * 1.3) - 0.5, vnoise(lat.yx * 1.3 + 4.0) - 0.5) * 0.4;
    n = normalize(n + vec3(g.x, g.y * 0.3, g.y));
    Surf sf;
    sf.albedo = a;
    sf.rough = mix(0.8, 0.25, wet);
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos, n, uCamPos, gl_FragCoord.xy);
    vec3 c = shadeSurface(sf, n, V, 1.0, sv);
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    frag = vec4(c, 1.0);
}
