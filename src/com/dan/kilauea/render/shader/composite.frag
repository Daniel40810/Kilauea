// composite.frag — Wolken (halbe Auflösung) über das Bild legen und über heißer Lava die Luft flimmern
// lassen (Phase 11): das Bild dahinter wird mit einem bewegten Rauschen leicht versetzt, wo der Lichtschein
// der Lava stark ist (an der Stelle im Bild oder auf halbem Weg dorthin) und die Kamera nah ist [G].
// Ausgabe: HDR-Farbe.
#include "common.glsl"
#include "lava.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform sampler2D uClouds;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform float uTime;
uniform int uZeroOne = 1;
uniform int uCloudsOn = 1;

vec3 worldAt(vec2 suv, float z) {
    float zn = uZeroOne == 1 ? z : z * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(suv * 2.0 - 1.0, zn, 1.0);
    return a.xyz / a.w;
}

void main() {
    vec2 uv = vUV;
    if (uGlowRect.w > 0.5) {
        float z = texture(uSceneDepth, vUV).r;
        bool bg = uZeroOne == 1 ? z < 1e-7 : z > 0.9999999;
        vec3 P = worldAt(vUV, bg ? (uZeroOne == 1 ? 1e-6 : 0.999999) : z);
        float dist = length(P - uCamPos);
        vec3 M = mix(uCamPos, P, 0.5);
        float heat = max(dot(lavaLight(P.xz), vec3(0.33)), dot(lavaLight(M.xz), vec3(0.33)) * step(M.y, 400.0));
        float s = smoothstep(0.0005, 0.02, heat) * smoothstep(3000.0, 150.0, dist);
        if (s > 0.0) {
            vec2 q = vUV * vec2(70.0, 40.0) + vec2(0.0, -uTime * 3.5);
            vec2 o = vec2(vnoise(q) - 0.5, vnoise(q + 17.3) - 0.5);
            uv += o * s * 0.004;
        }
    }
    vec3 scene = texture(uSceneColor, uv).rgb;
    vec4 cl = vec4(0.0, 0.0, 0.0, 1.0);
    if (uCloudsOn == 1) {
        // fünf Proben, nach ähnlicher Tiefe gewichtet: glättet das feste Körnen des Wolkenmarsches, ohne
        // Wolken über Bergkanten zu verschmieren
        vec2 px = 1.0 / vec2(textureSize(uClouds, 0));
        float zc = texture(uSceneDepth, vUV).r;
        vec4 acc = texture(uClouds, vUV);
        float wsum = 1.0;
        for (int k = 0; k < 4; k++) {
            vec2 o = vec2(k < 2 ? -1.0 : 1.0, (k & 1) == 0 ? -1.0 : 1.0) * px * 1.2;
            float zk = texture(uSceneDepth, vUV + o * 0.5).r;
            float w = 1.0 / (1.0 + 200.0 * abs(zk - zc) / max(max(zk, zc), 1e-7));
            acc += texture(uClouds, vUV + o) * w;
            wsum += w;
        }
        cl = acc / wsum;
    }
    vec3 c = max(scene * cl.a + cl.rgb, vec3(0.0));   // Lichtstrahlen ziehen Licht ab, nie unter null
    if (any(isnan(c)) || any(isinf(c))) c = scene;
    frag = vec4(c, 1.0);
}
