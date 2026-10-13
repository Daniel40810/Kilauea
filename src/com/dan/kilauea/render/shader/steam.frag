// steam.frag — Licht im Dampf: weiß, streut Sonnenlicht nach vorn (Henyey-Greenstein), dazu Himmelslicht;
// die Gasfahne ist leicht bläulich-grau (Schwefeldioxid, Vog). Weiche Kanten an Gelände (Tiefenvergleich).
// Phase 9: Gischt (Art 2) rein weiß; Phase 10: Blasen unter Wasser (Art 3) als helle Ringe; Phase 12: Asche (Art 4) braungrau; alles wird vom Lichtschein der Lava von unten angestrahlt (lava.glsl).
// Ausgabe: vormultipliziert (Farbe·Deckkraft, Deckkraft).
#include "common.glsl"
#include "lighting.glsl"
#include "lava.glsl"
#include "underwater.glsl"
in vec2 vUV;
in vec3 vPos;
in vec4 vData;
out vec4 frag;
uniform vec3 uCamPos;
uniform sampler2D uSceneDepth;
uniform vec2 uScreen;
uniform float uNear;
uniform int uZeroOne = 1;

void main() {
    float r = length(vUV);
    if (r > 1.0) discard;
    float seed = vData.y;
    float n = fbm(vUV * 1.7 + seed * 37.0, 4);
    bool bubble = vData.z > 2.5 && vData.z < 3.5;
    bool ash = vData.z > 3.5;            // Phase 12: Asche und Staub
    bool spray = vData.z > 1.5 && vData.z < 2.5;
    float puff = smoothstep(1.0, spray ? 0.0 : 0.25, r + (n - 0.5) * (spray ? 0.9 : ash ? 1.0 : 0.7));   // Gischt weicher
    if (bubble) puff = smoothstep(1.0, 0.85, r) * (0.35 + 0.65 * smoothstep(0.5, 0.95, r)) + 0.8 * smoothstep(0.35, 0.0, length(vUV - vec2(-0.35, 0.35)));
    float alpha = vData.x * puff;
    // weich am Gelände: Abstand des Teilchens zur Szene dahinter
    float zs = texture(uSceneDepth, gl_FragCoord.xy / uScreen).r;
    float zp = gl_FragCoord.z;
    if (uZeroOne == 1) {
        float ds = uNear / max(zs, 1e-7), dp = uNear / max(zp, 1e-7);
        alpha *= clamp((ds - dp) / (bubble ? 0.3 : 4.0), 0.0, 1.0);
    }
    if (alpha < 0.002) discard;
    vec3 V = normalize(uCamPos - vPos);
    float g = 0.6, cosT = dot(-V, uSunDir);
    float hg = (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cosT, 1.5) / (4.0 * PI);
    vec3 E = skyIrradiance(uSkyLut, vec3(0.0, 1.0, 0.0), uSunDir);
    vec3 albedo = vData.z < 0.5 ? vec3(0.78, 0.80, 0.84) : vData.z < 1.5 ? vec3(0.92) : ash ? vec3(0.40, 0.36, 0.32) : vec3(0.97);
    vec3 c = albedo * (uSunColor * (0.12 + hg * 1.6) * (0.6 + 0.4 * n) + E / PI * 0.9 + lavaLight(vPos.xz) * uLavaExposure / PI * 0.6);
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    if (bubble) {
        // unter Wasser: Licht von oben durch die Wassersäule, Glanz am Rand (der Nebel kommt im eigenen Pass)
        c = vec3(0.9) * (E / PI + uSunColor * 0.25) * exp(-waterSigma(vPos.xz) * max(-vPos.y, 0.0)) + lavaLight(vPos.xz) * uLavaExposure * 0.2 / PI;
    } else c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    frag = vec4(c * alpha, alpha);
}
