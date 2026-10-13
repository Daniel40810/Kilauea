// pillow.frag — Kissenlava: schwarze, glasige Rinde mit Brotkrustenrissen; frisch geknospt glüht die Haut
// kurz orange, danach glimmt es noch in den Rissen (Planck, lava.glsl). Licht unter Wasser mit Kaustik.
// Ausgabe: HDR-Farbe (der Nebel unter Wasser kommt im eigenen Pass).
#include "common.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "underwater.glsl"
#include "lava.glsl"
in vec3 vPos;
in vec3 vN;
in vec3 vLocal;
in vec4 vGlow;
out vec4 frag;
uniform vec3 uCamPos;

vec3 pvoro(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 r = g + hash22(i + g) - f;
            float d = dot(r, r);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
        }
    return vec3(sqrt(d1), sqrt(d2) - sqrt(d1), 0.0);
}

void main() {
    vec3 N = normalize(vN);
    vec3 V = normalize(uCamPos - vPos);
    float seed = vGlow.z * 31.0;
    // Brotkruste: Schollen von rund 15 cm, dazu Längsrisse entlang des Wachsens
    vec3 v1 = pvoro(vLocal.xz * 3.2 + seed);
    float fwc = length(fwidth(vLocal.xz * 3.2));
    float crack = smoothstep(0.10 + fwc, 0.0, v1.y);
    float spread = smoothstep(0.035, 0.0, abs(fract(vLocal.x * 1.6 + seed) - 0.5) * 0.25) * smoothstep(-0.1, 0.4, vLocal.y);
    crack = max(crack, spread * 0.7);
    vec3 albedo = vec3(0.032, 0.031, 0.036) * (0.8 + 0.4 * vnoise(vLocal.xz * 9.0 + seed));
    Surf sf;
    sf.albedo = albedo * (1.0 - 0.5 * crack);
    sf.rough = mix(0.28, 0.75, crack);
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos, N, uCamPos, gl_FragCoord.xy);
    vec3 c = shadeSurface(sf, N, V, 1.0, sv);
    c = underwaterLight(c, sf.albedo, vPos, N, sv, uSunColor, uSunDir, uWaterTime);
    // Glut: Haut kurz nach dem Knospen, danach die Risse; die Haut kühlt im Wasser in Sekunden [G]
    float tSkin = mix(500.0, 1120.0, clamp(vGlow.x, 0.0, 1.0));
    float tCrack = mix(650.0, 1140.0, sqrt(clamp(vGlow.y, 0.0, 1.0)));
    vec3 e = glowRGB(tSkin) * (1.0 - crack) + glowRGB(tCrack) * crack;
    c += e * uLavaExposure;
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);
    frag = vec4(c, 1.0);
}
