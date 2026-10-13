// underwater.frag — die Wassersäule, wenn das Auge unter dem Meeresspiegel ist (Phase 10): alles hinter
// dem Wasser wird nach dem Weg geschwächt, dazu das Streulicht des Wassers und Lichtstrahlen, die das
// Wellenmuster der Oberfläche aus dem Sonnenlicht schneidet (dieselbe Kaustik wie auf dem Grund).
// Eingaben: Kopie von Farbe und Tiefe der Szene. Ausgabe: HDR-Farbe. Qualitätsstufe: Strahlen ab Mittel.
#include "common.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "underwater.glsl"
#include "lava.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform float uNear = 0.5;
uniform int uRays = 1;

vec3 worldAt(vec2 suv, float z) {
    float zn = uZeroOne == 1 ? z : z * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(suv * 2.0 - 1.0, zn, 1.0);
    return a.xyz / a.w;
}

void main() {
    vec3 scene = texture(uSceneColor, vUV).rgb;
    float z = texture(uSceneDepth, vUV).r;
    bool bg = uZeroOne == 1 ? z < 1e-7 : z > 0.9999999;
    vec3 far = worldAt(vUV, bg ? (uZeroOne == 1 ? 1e-6 : 0.999999) : z);
    vec3 dir = normalize(far - uCamPos);
    float dist = bg ? 400.0 : length(far - uCamPos);
    // Weg im Wasser: bis zum Treffer oder bis zur Oberfläche
    if (dir.y > 1e-4) dist = min(dist, max(0.0, -uCamPos.y) / dir.y + 0.05);
    vec3 sigma = waterSigma(uCamPos.xz + dir.xz * min(dist, 40.0) * 0.5);
    vec3 T = exp(-sigma * dist);
    // Streulicht: Sonne und Himmel in der Tiefe der Kamera
    float depth = max(-uCamPos.y, 0.0);
    vec3 sd = sunUnderDir(uSunDir);
    vec3 E = uSunColor * max(uSunDir.y, 0.0) + skyIrradiance(uSkyLut, vec3(0.0, 1.0, 0.0), uSunDir);
    vec3 inscat = waterScatter(uCamPos.xz) * E / PI * exp(-sigma * depth);
    // Glut eines Eintritts färbt das trübe Wasser von oben
    inscat += waterScatter(uCamPos.xz) * lavaLight(uCamPos.xz) * uLavaExposure * 3.0 / PI * exp(-sigma * depth);
    vec3 c = scene * T + inscat * (1.0 - T);
    // Lichtstrahlen: entlang des Blicks die Kaustik an der Stelle der Oberfläche, über der die Probe liegt
    if (uRays == 1 && sd.y > 0.0) {
        float L = min(dist, 35.0);
        vec3 rays = vec3(0.0);
        const int N = 14;
        for (int i = 0; i < N; i++) {
            float t = (float(i) + 0.5 + 0.5 * hash12(vUV * 977.0 + float(i))) / float(N) * L;
            vec3 q = uCamPos + dir * t;
            if (q.y > 0.0) break;
            float up = -q.y / sd.y;
            vec2 s = q.xz + sd.xz * up;
            float k = pow(caustic(s * 0.6, uWaterTime * 0.7), 2.0);
            rays += k * exp(-sigma * (t + up));
        }
        float phase = 0.25 + 1.5 * pow(max(dot(dir, sd), 0.0), 6.0);
        c += rays * (L / float(N)) * uSunColor * waterScatter(uCamPos.xz) * phase * 0.9 * (1.0 - clamp(turbidity(uCamPos.xz) * 0.4, 0.0, 0.9));
    }
    if (any(isnan(c)) || any(isinf(c))) c = scene;
    frag = vec4(c, 1.0);
}
