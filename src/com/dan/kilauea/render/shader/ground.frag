// ground.frag — Licht auf der Bodendecke: dünne Flächen beidseitig mit Durchscheinen, Steine nur von
// vorn. Ausblenden am Rand der Reichweite als Raster (ohne Sortieren durchsichtiger Flächen).
#include "common.glsl"
#include "lighting.glsl"
#include "cars.glsl"
#include "shadow.glsl"
in vec3 vPos;
in vec3 vNormal;
in vec3 vColor;
in float vSolid;
in float vFade;
out vec4 frag;
uniform vec3 uCamPos;

void main() {
    if (cutAway(vPos)) discard;
    // Raster-Ausblenden: Bayer 4×4
    ivec2 q = ivec2(gl_FragCoord.xy) & 3;
    int m = (q.x ^ q.y) * 4 + q.y;
    float th = (float(((m * 9) & 15)) + 0.5) / 16.0;
    if (vFade > th) discard;
    vec3 V = normalize(uCamPos - vPos);
    vec3 n = normalize(vNormal);
    if (vSolid < 0.5 && dot(n, V) < 0.0) n = -n;
    Surf sf;
    sf.albedo = vColor;
    sf.rough = vSolid > 0.5 ? 0.85 : 0.8;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos + uSunDir * 0.3, n, uCamPos, gl_FragCoord.xy);
    vec3 c = shadeSurface(sf, n, V, 1.0, sv);
    c += sf.albedo / PI * carLight(vPos, n);   // Phase 15: Scheinwerfer
    if (vSolid < 0.5) {
        float back = max(dot(-n, uSunDir), 0.0) * max(dot(-V, uSunDir), 0.0);
        c += uSunColor * sv * vColor * vec3(0.9, 1.2, 0.5) * back * 0.15;
    }
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    frag = vec4(c, 1.0);
}
