// ocean.frag — Pazifik. Phase 1: Wellenzüge aus Passatsee und Südswell, Glanz von Sonne und Mond,
// Spiegelung des Himmels, Blick durch klares Wasser auf Sand und Fels (Kopie der Szene).
// Phase 9: Dünung aus sea.glsl (Aufsteilen, Brecher, Schaumwalzen, Schwall an Klippen), Spiegelung der
// Insel im Bildraum (Strahl durch die Tiefe der Szene, sonst Himmel), Glut der Lava auf Wasser und Schaum.
// Eingaben: vPos (abgesenkt), vFlat, Szenenfarbe und -tiefe. Ausgabe: HDR-Farbe.
// Koordinatenraum: Welt. Qualitätsstufe: uDetail steuert die Zahl der Wellenzüge. Ersatz: keiner.
#include "common.glsl"
#include "shadow.glsl"
#include "sea.glsl"
#include "lava.glsl"
#include "underwater.glsl"

in vec3 vPos;
in vec2 vFlat;
in float vEta;
out vec4 frag;

uniform sampler2D uSkyLut;
uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform mat4 uInvViewProj;
uniform mat4 uViewProj;
uniform vec2 uScreen;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uMoonDir;
uniform vec3 uMoonColor;
uniform float uExposure = 1.0;
uniform float uTime;
uniform vec2 uWind;          // Windrichtung (wohin) mal Stärke in m/s
uniform vec2 uSwell;         // Richtung des Swells (wohin), Länge = Höhe in m
uniform float uDetail = 1.0;
uniform float uNear = 0.5;
uniform int uSsr = 1;
uniform int uDiagnosticFlat = 0;
uniform int uDiagnosticFixedReflection = 0;
uniform int uDiagnosticNoSkyLut = 0;
uniform int uDiagnosticNoAerial = 0;
uniform int uSeaDebug = 0;        // Prüfbild: R Brecher, G Schwall an der Klippe, B Saum

// Ein Wellenzug: Richtung, Wellenlänge, Steilheit. Liefert die Ableitungen der Höhe.
vec2 wave(vec2 p, vec2 dir, float lambda, float steep, float t, float footprint) {
    float k = 2.0 * PI / lambda;
    float c = sqrt(9.81 / k);
    float a = steep / k;
    float fade = fadeOut(4.0, 1.5, footprint * k);
    float ph = k * dot(dir, p) - k * c * t;
    return dir * (a * k * cos(ph)) * fade;
}

vec3 worldAt(vec2 suv, float z) {
    float zn = uZeroOne == 1 ? z : z * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(suv * 2.0 - 1.0, zn, 1.0);
    return a.xyz / a.w;
}

bool isBackground(float z) { return uZeroOne == 1 ? z < 1e-7 : z > 0.9999999; }

void main() {
    if (cutAway(vPos)) discard;
    if (uDiagnosticFlat == 1) {
        frag = vec4(0.0, 0.45, 1.0, 1.0);
        return;
    }
    vec3 v = uCamPos - vPos;
    float dist = length(v);
    v /= dist;
    vec2 p = vFlat;
    float footprint = max(length(fwidth(p)), 1e-3);
    vec2 suv = gl_FragCoord.xy / uScreen;

    float wind = length(uWind);
    vec2 wd = wind > 0.01 ? uWind / wind : vec2(1.0, 0.0);
    float rough = clamp(wind / 9.0, 0.15, 1.2);
    vec2 grad = vec2(0.0);
    vec2 warp = vec2(vnoise(p * 0.004), vnoise(p * 0.004 + 9.0)) - 0.5;
    // Windsee: 12 Züge um die Windrichtung, 0,3 m bis 40 m
    int trains = uDetail > 0.5 ? 12 : 7;
    for (int i = 0; i < 12; i++) {
        if (i >= trains) break;
        float fi = float(i);
        float ang = (hash12(vec2(fi, 3.7)) - 0.5) * 1.8;
        vec2 dir = vec2(cos(ang) * wd.x - sin(ang) * wd.y, sin(ang) * wd.x + cos(ang) * wd.y);
        float lambda = 40.0 * pow(0.66, fi) * (0.8 + 0.4 * hash12(vec2(fi, 9.1)));
        if (footprint * 6.2831853 / lambda > 4.0) continue;
        float steep = (0.04 + 0.06 * fi / 11.0) * rough;
        vec2 pw = p + hash22(vec2(fi, 1.3)) * 300.0 + warp * lambda * 2.0;
        grad += wave(pw, dir, lambda, steep, uTime, footprint);
    }
    // Dünung und Brandung (sea.glsl), mit der Fläche aus ocean.vert deckungsgleich
    SeaS S = seaAt(p);
    SeaW SW = seaWaves(p, S, fadeOut(3500.0, 1200.0, length(p - uCamPos.xz)));
    // ferne Dünung nur noch als Glanz, mit dem Bildpunkt ausgeblendet
    float swellFoot = fadeOut(60.0, 15.0, footprint);
    grad += SW.grad * mix(0.35, 1.0, swellFoot);
    // feine Kräuselung
    float fine = 0.3 * rough * fadeOut(0.5, 0.05, footprint);
    if (fine > 0.002) {
        vec2 q = p * 1.8 + wd * uTime * 0.7;
        float e = 0.05, n0 = fbm(q, 3), nx = fbm(q + vec2(e, 0.0), 3), nz = fbm(q + vec2(0.0, e), 3);
        grad += vec2(nx - n0, nz - n0) / e * fine * 0.1;
    }
    float steepness = length(grad);
    vec3 n = normalize(vec3(-grad.x, 1.0, -grad.y));
    float ndv = clamp(dot(n, v), 0.02, 1.0);
    float F = 0.02 + 0.98 * pow(clamp(1.0 - ndv, 0.0, 1.0), 5.0);
    vec3 light = uSunColor * max(uSunDir.y, 0.0) + uMoonColor * max(uMoonDir.y, 0.0);
    if (uDiagnosticNoSkyLut == 0) light += skyIrradiance(uSkyLut, vec3(0, 1, 0), uSunDir);
    // ---- von unten (Phase 10): im Snellschen Fenster der Himmel, gebrochen; außerhalb Totalreflexion.
    // Der Nebel unter Wasser kommt im eigenen Pass dazu.
    if (uCamPos.y < vPos.y - 0.02) {
        vec3 I = -v;
        vec3 Nd = -n;
        vec3 tr = refract(I, Nd, WATER_N);
        vec3 scat = waterScatter(p) * light / PI;
        vec3 col = scat;
        if (dot(tr, tr) > 1e-6) {
            tr = normalize(tr);
            float ci = clamp(dot(-I, Nd), 0.0, 1.0);
            float Fr = 0.02 + 0.98 * pow(1.0 - clamp(dot(tr, n), 0.0, 1.0), 5.0);
            vec3 sky = uDiagnosticNoSkyLut == 1
                    ? vec3(0.08, 0.16, 0.22)
                    : textureLod(uSkyLut, skyUV(vec3(tr.x, max(tr.y, 0.0), tr.z)), 0.0).rgb;
            float sunSpot = pow(max(dot(tr, uSunDir), 0.0), 600.0) * 40.0 + pow(max(dot(tr, uSunDir), 0.0), 30.0) * 0.6;
            col = mix(sky + uSunColor * sunSpot, scat, Fr);
            col += lavaLight(p) * uLavaExposure * 0.1 / PI;
        }
        if (any(isnan(col)) || any(isinf(col))) col = vec3(0.0);
        frag = vec4(col, 1.0);
        return;
    }
    float sv = sunShadow(vPos, vec3(0.0, 1.0, 0.0), uCamPos, gl_FragCoord.xy);

    // ---- Wasserkörper: klares, tiefblaues Pazifikwasser; über Sand türkis
    vec3 deep = vec3(0.004, 0.016, 0.034) * light / PI;
    vec3 body = deep;
    ivec2 ip = ivec2(gl_FragCoord.xy);
    float zb = texelFetch(uSceneDepth, ip, 0).r;
    float depthV = 400.0;                         // senkrechte Wassertiefe unter diesem Punkt
    float thick = 400.0;
    if (!isBackground(zb)) {
        vec3 bed = worldAt(suv, zb);
        thick = distance(bed, vPos);
        depthV = max(vPos.y - bed.y, 0.0);
        vec2 ruv = clamp(suv + n.xz * 0.015 * clamp(thick, 0.0, 4.0) / (1.0 + dist * 0.02), vec2(0.002), vec2(0.998));
        vec3 bedC = texture(uSceneColor, ruv).rgb;
        // Weg hin (schräg) und Licht herab (senkrecht): Rot zuerst geschluckt
        // nur der Weg durch das Wasser zum Auge: das Licht hinab rechnet seit Phase 10 der Grund selbst
        vec3 T = exp(-thick * waterSigma(p));
        vec3 scatter = waterScatter(p) * light / PI;
        body = bedC * T + mix(scatter, deep, smoothstep(5.0, 60.0, depthV)) * (1.0 - T);
    }
    body *= mix(0.65, 1.0, sv);
    // ---- Meereseintritt (Phase 10): braune Trübe aus Glassand vor der Küste
    float turb = turbidity(p);
    if (turb > 0.01) body = mix(body, vec3(0.050, 0.042, 0.028) * light / PI, clamp(turb * 0.45, 0.0, 0.85));

    // ---- Brandung. Mit Raster: Brecher und Schwall aus sea.glsl; sonst nach der Tiefe im Bild (Phase 1)
    float foam = 0.0;
    float lace = fbm(p * 0.25 + vec2(uTime * 0.1, 0.0), 4);
    if (S.valid > 0.01) {
        float ragged = smoothstep(0.3, 0.75, lace + 0.12);
        foam += S.valid * clamp(SW.foam, 0.0, 1.5) * ragged;
        foam += S.valid * clamp(SW.wash, 0.0, 1.5) * smoothstep(0.2, 0.75, lace + 0.15 * SW.wash);
        // Saum, wo Wasser und Fels sich treffen
        foam += S.valid * fadeOut(0.8, 0.0, depthV) * 0.6 * smoothstep(0.35, 0.8, vnoise(p * 0.9 + uTime * 0.3));
    }
    if (S.valid < 0.99 && depthV < 12.0) {
        float zone = fadeOut(12.0, 1.5, depthV);
        float ph = depthV / 2.2 + uTime * 0.18 + 0.6 * fbm(p * 0.02, 2);
        float line = pow(0.5 + 0.5 * sin(ph * 6.2831853), 6.0);
        float lc = smoothstep(0.35, 0.75, lace);
        float f0 = zone * (0.55 * line + 0.45 * lc * fadeOut(5.0, 0.3, depthV));
        f0 += fadeOut(1.2, 0.0, depthV) * (0.6 + 0.4 * vnoise(p * 1.5 + uTime));
        foam += (1.0 - S.valid) * f0;
    }
    // kochendes Wasser über der Lava: weiße, wallende Flecken, rasch bewegt
    for (int i = 0; i < 8; i++) {
        if (i >= uEntryCount) break;
        float de = length(p - uEntry[i].xy);
        float reach = 25.0 + 6.0 * sqrt(max(uEntry[i].z, 0.0)) / 10.0;
        if (de > reach) continue;
        float boil = fadeOut(reach, reach * 0.3, de) * smoothstep(0.35, 0.75, fbm(p * 0.4 + vec2(uTime * 0.9, -uTime * 0.7), 3));
        foam += boil * 1.2;
    }
    // Schaumkronen bei frischem Passat
    foam += smoothstep(6.0, 11.0, wind) * smoothstep(0.12, 0.3, steepness) * smoothstep(0.45, 0.75, fbm(p * 0.6 + uTime * 0.15, 3)) * 0.6;

    // ---- Spiegelung des Himmels
    vec3 r = reflect(-v, n);
    r.y = abs(r.y);
    vec3 refl = uDiagnosticNoSkyLut == 1 || uDiagnosticFixedReflection == 1
            ? vec3(0.08, 0.16, 0.22)
            : textureLod(uSkyLut, skyUV(r), 2.0).rgb;
    // Spiegelung der Insel im Bildraum: Strahl in wachsenden Schritten, Treffer gegen die Tiefe der Szene
    if (uSsr == 1 && uZeroOne == 1 && dist < 6000.0 && F > 0.015) {
        vec3 rr = reflect(-v, n);
        rr.y = max(rr.y, 0.01);
        float t = 1.5 + dist * 0.004;
        vec3 hitC = vec3(0.0);
        float hitW = 0.0;
        for (int i = 0; i < 28; i++) {
            vec3 Q = vPos + rr * t;
            vec4 cq = uViewProj * vec4(Q, 1.0);
            if (cq.w <= 0.0) break;
            vec2 uvq = cq.xy / cq.w * 0.5 + 0.5;
            if (any(lessThan(uvq, vec2(0.0))) || any(greaterThan(uvq, vec2(1.0)))) break;
            float zs = textureLod(uSceneDepth, uvq, 0.0).r;
            if (!isBackground(zs) && worldAt(uvq, zs).y > -0.3) {   // nicht den Grund unter Wasser spiegeln
                float ds = uNear / max(zs, 1e-9);
                float gap = cq.w - ds;
                if (gap > 0.0 && gap < max(3.0, t * 0.35)) {
                    vec2 edge = min(uvq, 1.0 - uvq);
                    hitW = smoothstep(0.0, 0.08, min(edge.x, edge.y)) * smoothstep(0.0, 0.15, rr.y + 0.1);
                    hitC = textureLod(uSceneColor, uvq, 0.0).rgb;
                    break;
                }
            }
            t *= 1.32;
        }
        refl = mix(refl, hitC, hitW);
    }
    // Glutreflex: der Lichtschein über der Lava spiegelt sich als orangefarbener Glanz, auch wo die Lava selbst
    // nicht im Bild ist (Spiegelpunkt rund 15 m über dem Wasser) [A, grobe Näherung]
    {
        vec3 rg = reflect(-v, n);
        vec2 pr = vPos.xz + rg.xz * (15.0 / max(rg.y, 0.08));
        refl += lavaLight(pr) * uLavaExposure * (0.35 / PI);
    }

    // Sonnen- und Mondglanz (GGX); bei Gegenrichtung ist der Halbvektor undefiniert.
    vec3 sunHalf = uSunDir + v;
    float sunHalf2 = dot(sunHalf, sunHalf);
    float nh = sunHalf2 > 1e-8 ? max(dot(n, sunHalf * inversesqrt(sunHalf2)), 0.0) : 0.0;
    float alpha = mix(0.05, 0.20, clamp(rough, 0.0, 1.0)) + clamp(footprint * 0.002, 0.0, 0.15);
    float a2 = alpha * alpha;
    float D = a2 / (PI * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0));
    float Fs = sunHalf2 > 1e-8 ? 0.02 + 0.98 * pow(clamp(1.0 - dot(sunHalf * inversesqrt(sunHalf2), v), 0.0, 1.0), 5.0) : 0.0;
    float ndl = max(dot(n, uSunDir), 0.0);
    vec3 spec = sunHalf2 > 1e-8
            ? uSunColor * D * Fs * ndl / (4.0 * ndv * max(ndl, 0.05) + 1e-3) * ndl
            : vec3(0.0);
    vec3 moonHalf = uMoonDir + v;
    float moonHalf2 = dot(moonHalf, moonHalf);
    float nhm = moonHalf2 > 1e-8 ? max(dot(n, moonHalf * inversesqrt(moonHalf2)), 0.0) : 0.0;
    float Dm = a2 / (PI * pow(nhm * nhm * (a2 - 1.0) + 1.0, 2.0));
    float ndlm = max(dot(n, uMoonDir), 0.0);
    if (moonHalf2 > 1e-8)
        spec += uMoonColor * Dm * 0.02 * ndlm / (4.0 * ndv * max(ndlm, 0.05) + 1e-3) * ndlm;
    spec *= sv;

    float fm = clamp(foam, 0.0, 1.0);
    if (uSeaDebug == 1) { frag = vec4(vec3(clamp(SW.foam, 0.0, 1.0), clamp(SW.wash, 0.0, 1.0), fadeOut(0.8, 0.0, depthV)) * 0.2 * light / PI + vec3(0.0, 0.0, 0.0), 1.0); return; }
    vec3 glow = lavaLight(p) * uLavaExposure;                   // Lichtschein der Lava (Phase 8)
    body += vec3(0.02, 0.03, 0.035) * glow / PI;
    vec3 foamC = vec3(0.82, 0.84, 0.82) * (light * mix(0.55, 1.0, sv) + glow) / PI;
    vec3 c = mix(mix(body, foamC, fm), refl, F * (1.0 - fm)) + spec * (1.0 - fm);
    if (uDiagnosticNoSkyLut == 0 && uDiagnosticNoAerial == 0) c = aerial(c, -v, dist, uSkyLut, uCamPos.y);
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);
    frag = vec4(c, 1.0);
}
