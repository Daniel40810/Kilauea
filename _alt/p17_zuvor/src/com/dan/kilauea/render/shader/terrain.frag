// terrain.frag — Gestein und Grün der Insel (Phase 3).
// Lava nach Alter und Art: frische Pāhoehoe glasig schwarz mit Loben und Stricken, ʻaʻā als rauer
// Klinker, mit den Jahren grau, mit Flechten silbrig, dann braun verwittert und zu Boden. Darauf der
// Bewuchs nach Regen und Alter: Farn und Gras in Flecken, ʻōhiʻa-Wald als Kronendach, Baumfarne im
// Nassen. Asche und Bims, Schwefel an den Dampfstellen, schwarzer Sand am Wasser, geschichteter Basalt
// in Steilwänden, Meeresboden.
// Eingaben: vPos (abgesenkt), vWorld, vNormal; Texturen uCover (Bewuchs, Bäume, Asche, Küste) und
// uGeo (Alterscode, ʻaʻā, Flechten, Regen), je 75 m. Ausgabe: HDR-Farbe (vorbelichtet).
// Koordinatenraum: Welt. Qualitätsstufe: uDetail 0..1 schaltet Feinnormalen (NIEDRIG 0).
// Feines (Stricke, Klinker, Kronen) wird nach der Größe eines Bildpunkts ausgeblendet, damit nichts flimmert.
// Ersatz: bei Fehler zeigt der Renderer die Meldung; kein stilles Ersatzmaterial.
// Phase 8: darüber die Lava dieses Laufs (lava.glsl): frisch glasig schwarz, Kruste mit Rissen, in denen
// die Glut nach Planck zu sehen ist (Anteil nach Oberflächen- und Kerntemperatur), treibende Schollen
// längs des Fließens, ʻaʻā mit glühenden Lücken, Oberlichter, nachglühendes Gestein; dazu der
// Lichtschein der Lava auf alles in der Nähe.
#include "common.glsl"
#include "lighting.glsl"
#include "cars.glsl"
#include "shadow.glsl"
#include "terrain_height.glsl"
#include "underwater.glsl"

in vec3 vPos;
in vec3 vWorld;
in vec3 vNormal;
in float vLod;
out vec4 frag;

uniform vec3 uCamPos;
uniform float uDetail = 1.0;
uniform int uShowLod = 0;      // Prüfbild: Detailstufen einfärben
uniform int uShowAge = 0;      // Prüfbild: Alter der Lava einfärben
uniform vec4 uSulfur[8];       // Dampf- und Schwefelstellen: x, z, Halbmesser (m), Ringbreite (0 = Scheibe)
uniform int uSulfurCount = 0;
uniform vec4 uHole[4];         // Öffnungen der Lavaröhre: je (x0, z0, x1, z1) und (Boden, Decke, Halbmesser, –)

// Liegt w in einer Öffnung der Röhre? Dann fehlt dort das Gelände (Überhang über dem Eingang).
bool inHole(vec3 w) {
    for (int k = 0; k < 2; k++) {
        vec4 a = uHole[2 * k], b = uHole[2 * k + 1];
        if (b.z <= 0.0) continue;
        vec2 d = a.zw - a.xy;
        float t = clamp(dot(w.xz - a.xy, d) / max(dot(d, d), 1e-6), 0.0, 1.0);
        float lat = length(w.xz - (a.xy + d * t));
        if (lat < b.z && w.y > b.x + 0.25 && w.y < b.y) return true;
    }
    return false;
}

// ------------------------------------------------------------ Zellen (Voronoi)
// Ergebnis: x = Abstand zum nächsten Kern (Zellen), y = Abstand zur Kante (F2−F1), z = Zufall der Zelle
vec3 voro(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0, id = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = hash22(i + g);
            vec2 r = g + o - f;
            float d = dot(r, r);
            if (d < d1) { d2 = d1; d1 = d; id = hash12(i + g + 7.7); }
            else if (d < d2) d2 = d;
        }
    d1 = sqrt(d1);
    d2 = sqrt(d2);
    return vec3(d1, d2 - d1, id);
}

// ------------------------------------------------------------ Kleinrelief der Lava (m)
// Pāhoehoe: Loben von 3 bis 12 m, die sich aufwölben (Tumuli), Fugen dazwischen, auf jedem Lobus
// Stricke quer zur Fließrichtung. ʻaʻā: Klinker aus Brocken von 0,3 bis 2 m.
// fw: Größe eines Bildpunkts (m). down: Richtung bergab (eben).
// Lage im Lobenraum: stark verwirbelt und längs der Fließrichtung gestreckt (Loben sind Zungen, keine Fliesen)
vec2 lobeSpace(vec2 p, vec2 down) {
    vec2 w = p + 4.0 * (vec2(fbm(p * 0.06, 3), fbm(p * 0.06 + 3.1, 3)) - 0.5);
    vec2 perp = vec2(-down.y, down.x);
    return vec2(dot(w, down) * 0.75, dot(w, perp) * 1.25) / 6.0;
}

float lavaRelief(vec2 p, float aa, float young, float fw, vec2 down) {
    float h = 0.0;
    float fL = smoothstep(2.0, 0.5, fw);
    if (fL > 0.0 && aa < 0.95) {
        vec3 v = voro(lobeSpace(p, down));
        float dome = (1.0 - smoothstep(0.0, 0.9, v.x)) * (0.2 + 0.4 * v.z);
        // Fugen nur stückweise: Loben überlappen, viele Ränder sind verschmolzen
        float seam = smoothstep(0.12, 0.0, v.y) * smoothstep(0.35, 0.65, vnoise(p * 0.15 + v.z * 5.0));
        float lobe = dome * 0.5 - seam * 0.16;
        // kleine Zehen (1–2 m) auf den Loben
        float fT = smoothstep(0.6, 0.12, fw);
        if (fT > 0.0) lobe += (1.0 - voro(p * 0.7 + 2.0 * vnoise(p * 0.3)).x) * 0.06 * fT;
        // Stricke: quer zur Fließrichtung, je Lobus etwas gedreht und gebogen
        float fR = smoothstep(0.12, 0.02, fw);
        if (fR > 0.0) {
            float a = (v.z - 0.5) * 1.2;
            vec2 d = mat2(cos(a), -sin(a), sin(a), cos(a)) * down;
            float bend = 1.6 * vnoise(p * 0.45 + v.z * 9.0);
            float rope = sin(dot(p, d) * 9.0 + bend * 6.0);
            rope *= (1.0 - smoothstep(0.55, 0.95, v.x)) * (0.5 + 0.5 * young);
            lobe += rope * 0.022 * fR;
        }
        h += lobe * (1.0 - aa) * fL;
    }
    float fA = smoothstep(1.5, 0.15, fw);
    if (fA > 0.0 && aa > 0.05) {
        vec2 q = p + 0.8 * vec2(vnoise(p * 0.7), vnoise(p * 0.7 + 4.0));
        vec3 c1 = voro(q * 1.3), c2 = voro(q * 2.9 + 3.3);
        float rubble = max((1.0 - c1.x) * (0.3 + 0.5 * c1.z), (1.0 - c2.x) * 0.25) + 0.15 * vnoise(p * 6.0) + 0.3 * fbm(p * 0.3, 2);
        h += pow(rubble, 1.4) * 1.2 * aa * fA;
    }
    return h * (0.35 + 0.65 * young + 0.3 * aa);
}

// ------------------------------------------------------------ Farben (linear, nach Fotos [G])
vec3 lavaColor(vec2 p, float age, float aa, float rain) {
    // frisch: schwarz mit glasigem Schimmer; ʻaʻā braunschwarz
    vec3 fresh = mix(vec3(0.030, 0.031, 0.036), vec3(0.040, 0.034, 0.030), aa);
    // Jahrzehnte: grauer, die Glashaut platzt ab
    vec3 grey = mix(vec3(0.058, 0.057, 0.059), vec3(0.052, 0.046, 0.042), aa);
    // Jahrhunderte: grau-braun verwittert, im Feuchten rötlicher (Eisen)
    vec3 old = mix(vec3(0.080, 0.072, 0.064), vec3(0.10, 0.070, 0.052), smoothstep(1500.0, 3500.0, rain));
    // Jahrtausende: Boden (im Trockenen bleibt es Gestein)
    vec3 soil = mix(vec3(0.11, 0.090, 0.070), vec3(0.14, 0.080, 0.050), smoothstep(1500.0, 4000.0, rain));
    // Verwitterung geht im Nassen schnell, im Trockenen kaum voran
    float eff = age * clamp(rain / 2500.0, 0.15, 2.0);
    float t1 = smoothstep(1.0, 25.0, age), t2 = smoothstep(150.0, 1500.0, eff), t3 = smoothstep(4000.0, 30000.0, eff);
    vec3 c = mix(fresh, grey, t1);
    c = mix(c, old, t2);
    c = mix(c, soil, t3);
    // ʻaʻā: rot oxidierte Brocken in Flecken
    c = mix(c, vec3(0.09, 0.045, 0.03), aa * 0.25 * smoothstep(3.0, 30.0, age) * smoothstep(0.55, 0.8, vnoise(p * 0.9)) * (1.0 - t3));
    // Flecken und Schlieren: kein Feld ist einheitlich
    c *= 0.82 + 0.36 * fbm(p * 0.012, 3);
    return c;
}

// Kronendach (ʻōhiʻa, darunter Baumfarne)
vec3 canopyColor(float rain, float crown, float id) {
    vec3 ohia = mix(vec3(0.020, 0.036, 0.014), vec3(0.040, 0.058, 0.022), id);
    ohia = mix(ohia, vec3(0.085, 0.030, 0.020), smoothstep(0.93, 0.98, id));          // rote Blüte, junges Laub
    ohia = mix(ohia, vec3(0.050, 0.055, 0.040), smoothstep(0.85, 0.92, id) * 0.6);    // graugrüne Kronen
    vec3 fern = vec3(0.045, 0.075, 0.020);                                             // hāpuʻu, hellgrün
    vec3 c = mix(fern, ohia, smoothstep(0.25, 0.6, crown));
    c *= mix(0.55, 1.1, crown);
    return mix(c * 0.85, c, smoothstep(1500.0, 3000.0, rain));
}

// Niedriger Bewuchs: Uluhe-Farn im Nassen, Büsche, trockenes Gras
vec3 lowVegColor(vec2 p, float rain) {
    vec3 uluhe = vec3(0.060, 0.090, 0.022);
    vec3 shrub = vec3(0.050, 0.055, 0.030);
    vec3 grass = vec3(0.140, 0.115, 0.065);
    float w = smoothstep(800.0, 2200.0, rain), m = smoothstep(500.0, 1100.0, rain);
    vec3 c = mix(grass, shrub, m);
    c = mix(c, uluhe, w);
    return c * (0.8 + 0.4 * fbm(p * 0.03, 3));
}

// Asche und Tephra: Keanakākoʻi in der Kaʻū-Wüste (graubeige), Schlacke in der Höhe
vec3 ashColor(vec2 p, float h) {
    float n = fbm(p * 0.003, 4);
    vec3 kau = mix(vec3(0.115, 0.105, 0.088), vec3(0.165, 0.145, 0.115), n);
    vec3 cinder = mix(vec3(0.12, 0.070, 0.048), vec3(0.17, 0.11, 0.075), n);
    return mix(kau, cinder, smoothstep(2200.0, 3200.0, h));
}

// Schlacke der hohen Kegel (Mauna Kea): rotbraun
vec3 alpine(vec2 p) { return mix(vec3(0.13, 0.075, 0.05), vec3(0.18, 0.12, 0.08), fbm(p * 0.004, 4)); }

// Geschichteter Basalt in Steilwänden (Caldera, Pali, Klippe): Bänke früherer Ströme, je 1 bis 8 m dick,
// leicht geneigt und auskeilend; dazwischen rötlich verwitterte Lagen und ʻaʻā-Schutt; senkrechte Rinnen
// von Steinschlag und Regen (Statusbild Phase 4: die Bänke waren zu gleichmäßig und zu hell).
vec3 cliffRock(vec3 w) {
    float along = w.x * 0.8 + w.z * 0.6;
    float y = w.y + 9.0 * (vnoise(w.xz * 0.003) - 0.5) + 2.5 * (vnoise(w.xz * 0.02 + 3.0) - 0.5);
    // ungleich dicke Bänke: Dicke je Lage aus einem Zufallswert
    float y2 = y / 3.2;
    float layer = floor(y2 + 0.6 * vnoise(vec2(along * 0.01, floor(y2))));
    float r = hash12(vec2(layer, 1.7));
    float f = fract(y2 + 0.6 * vnoise(vec2(along * 0.01, floor(y2))));
    float thin = step(0.62, r);                                     // jede dritte Lage dünn und dunkler (Schlacke)
    vec3 basalt = mix(vec3(0.048, 0.044, 0.041), vec3(0.075, 0.066, 0.058), hash12(vec2(layer, 5.3)));
    vec3 oxid = vec3(0.10, 0.060, 0.042);
    vec3 a = mix(basalt, oxid, smoothstep(0.80, 0.93, r) * 0.85);
    a = mix(a, vec3(0.035, 0.032, 0.031), thin * 0.6);
    // Fugen zwischen den Bänken
    float joint = smoothstep(0.07, 0.0, f) + smoothstep(0.95, 1.0, f);
    a *= 1.0 - 0.35 * joint;
    // senkrechte Rinnen und Fahnen
    float runnel = vnoise(vec2(along * 0.35, w.y * 0.025));
    a *= 0.82 + 0.36 * runnel;
    return a * (0.85 + 0.3 * vnoise(w.xz * 0.2 + w.y * 0.3));
}

// ------------------------------------------------------------ Lava dieses Laufs (Phase 8)
// Lage im Fließraum: längs der Richtung dir um stretch gestreckt
vec2 flowSpace(vec2 q, vec2 dir, float stretch) {
    vec2 perp = vec2(-dir.y, dir.x);
    return vec2(dot(q, dir) / stretch, dot(q, perp));
}

// Leuchten der Lava an p (Engine-Einheiten, noch nicht vorbelichtet). fw: Größe eines Bildpunkts (m).
vec3 lavaEmission(vec2 p, LavaS L, float fw) {
    bool roofed = (L.flags & LAVA_TUBE) != 0 && (L.flags & LAVA_SKY) == 0;
    float share = roofed ? 0.0 : crackShare(L.surf, L.core);
    // Rand eines Stroms, der noch vorrückt: die Kruste reißt auf, die Glut quillt durch (Front, Zehen) [G]
    float rim = (1.0 - smoothstep(0.55, 0.95, L.cover)) * smoothstep(0.03, 0.2, L.h) * smoothstep(0.002, 0.03, length(L.vel));
    if (!roofed) share = max(share, 0.35 * rim);
    vec3 eCore = glowRGB(L.core), eSurf = glowRGB(L.surf);
    vec3 e = eSurf;
    if (share > 0.0 && L.core > 520.0) {
        float mobile = smoothstep(0.01, 0.06, L.h);
        vec2 vel = L.vel * mobile;
        // treibende Kruste: zwei Phasen, die sich überblenden (Fließkarte), damit nichts endlos gestreckt wird
        const float P = 6.0;
        float t1 = fract(uLavaTime / P), t2 = fract(uLavaTime / P + 0.5);
        float w1 = 1.0 - abs(1.0 - 2.0 * t1);
        vec2 dir = length(vel) > 0.05 ? normalize(vel) : vec2(1.0, 0.0);
        float stretch = 1.0 + 2.5 * smoothstep(0.5, 3.0, length(vel));   // Kanäle und Lavafälle: lange Schlieren
        // Schollen: frisch klein (0,6 m), mit dem Alter der Kruste größer (bis 4 m) [G]
        float plate = mix(0.6, 4.0, smoothstep(30.0, 7200.0, L.age));
        vec2 q1 = p - vel * t1 * P, q2 = p - vel * t2 * P + 13.7;
        float wd = clamp(share / 2.2, 0.0, 0.6);
        vec3 a1 = voro(flowSpace(q1, dir, stretch) / plate), a2 = voro(flowSpace(q2, dir, stretch) / plate);
        // Kante der Risse mit ihrer Breite: feine Fugen alter Kruste bleiben fein (sonst glühen Flecken von
        // ein paar Prozent, wo nur 0,3 % offen sind)
        float ed = max(wd * 0.5, 0.0015);
        float m = smoothstep(wd + ed, max(wd - ed, 0.0), a1.y) * w1 + smoothstep(wd + ed, max(wd - ed, 0.0), a2.y) * (1.0 - w1);
        // ʻaʻā: glühende Lücken zwischen den Brocken
        if (L.aa > 0.02) {
            vec3 b1 = voro(q1 / 1.1), b2 = voro(q2 / 1.1);
            float wa = clamp(share / 1.6, 0.0, 0.5);
            float ea = max(wa * 0.5, 0.002);
            float ma = smoothstep(wa + ea, max(wa - ea, 0.0), b1.y) * w1 + smoothstep(wa + ea, max(wa - ea, 0.0), b2.y) * (1.0 - w1);
            m = mix(m, ma, L.aa);
        }
        m = mix(m, 1.0, smoothstep(0.45, 0.85, share));        // ganz offen: frischer Kanal, Front
        m = mix(m, share, smoothstep(0.5, 2.0, fw / (plate * max(2.0 * wd, 0.004))));   // Riss schmaler als ein Bildpunkt: im Mittel
        e = mix(eSurf, eCore, m);
    }
    // Oberlicht: ein Loch im Dach, unten die Glut
    if ((L.flags & LAVA_SKY) != 0 && L.h > 0.05) {
        float d = length(p - L.center) + 1.2 * (vnoise(p * 0.8) - 0.5);
        e = mix(e, eCore * 0.7, smoothstep(3.6, 2.2, d));
    }
    return e;
}

float sulfurAt(vec2 p) {
    float s = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= uSulfurCount) break;
        vec4 q = uSulfur[i];
        float d = length(p - q.xy) + 25.0 * (vnoise(p * 0.03 + float(i)) - 0.5);
        float v = q.w > 0.0 ? smoothstep(q.w, 0.0, abs(d - q.z)) : smoothstep(q.z, q.z * 0.3, d);
        s = max(s, v);
    }
    return s;
}

void main() {
    if (cutAway(vPos)) discard;
    if (inHole(vWorld)) discard;
    vec3 n0 = normalize(vNormal);
    vec2 p = vWorld.xz;
    float h = vWorld.y;
    float fw = max(length(fwidth(p)), 1e-3);
    // Texturen im 75-m-Raster, leicht verwirbelt gelesen, damit Grenzen nicht wie Kacheln aussehen
    vec2 warp = (vec2(fbm(p * 0.012, 3), fbm(p * 0.012 + 7.3, 3)) - 0.5) * 70.0;
    vec4 cov = texture(uCover, gridCoord(p + warp) / uGridN);
    vec4 geo = texture(uGeo, gridCoord(p + warp * 0.4) / uGridN);
    float veg = cov.x, trees = cov.y, ashW = cov.z, shore = cov.w;
    float age = lavaAge(geo.x), aa = geo.y, lichen = geo.z, rain = geo.w * 8000.0;
    float young = youngOf(age);
    vec2 down = length(n0.xz) > 1e-4 ? normalize(n0.xz) : vec2(0.0, 1.0);
    float steep = smoothstep(0.80, 0.60, n0.y);

    // ---------------- Lava
    vec3 a = lavaColor(p, age, aa, rain);
    // Pāhoehoe glänzt jung (Glashaut), ʻaʻā ist matt
    float rough = mix(mix(0.32, 0.8, smoothstep(0.5, 20.0, age)), 0.85, aa);
    rough = mix(rough, 0.9, smoothstep(60.0, 600.0, age));
    float ao = 1.0;
    if (uDetail > 0.01 && fw < 2.0) {
        vec3 v = voro(lobeSpace(p, down));
        float seam = smoothstep(0.10, 0.0, v.y) * smoothstep(0.35, 0.65, vnoise(p * 0.15 + v.z * 5.0))
                * smoothstep(3.0, 0.8, fw) * (1.0 - aa) * (0.4 + 0.6 * young);
        a *= 1.0 - 0.45 * seam;                         // Fugen zwischen den Loben
        a *= 0.9 + 0.2 * v.z * (1.0 - aa);              // jeder Lobus etwas anders
        ao *= 1.0 - 0.35 * aa * smoothstep(1.5, 0.2, fw); // Klinker schattet sich selbst
    }
    // Flechten (Stereocaulon): silbergraue Flecken auf Strömen von einigen Jahrzehnten
    float lichenMask = lichen * smoothstep(0.4, 0.7, fbm(p * 0.08, 3) + 0.2 * lichen) * 0.7;
    a = mix(a, vec3(0.14, 0.145, 0.138), lichenMask * 0.7);
    rough = mix(rough, 0.95, lichenMask);

    // ---------------- Asche, Bims, Höhenschlacke
    float ashMask = ashW * smoothstep(0.15, 0.55, ashW + 0.3 * (fbm(p * 0.02, 3) - 0.5));
    float ripple = 0.5 + 0.5 * sin(dot(p, vec2(0.94, -0.34)) * 1.3 + 4.0 * vnoise(p * 0.05));   // Rippeln quer zum Passat
    vec3 ashC = ashColor(p, h) * (0.97 + 0.05 * ripple * smoothstep(0.3, 0.05, fw));
    a = mix(a, ashC, ashMask);
    rough = mix(rough, 0.95, ashMask);
    a = mix(a, alpine(p), smoothstep(2900.0, 3400.0, h) * smoothstep(3000.0, 8000.0, age) * 0.8);   // Mauna Kea, nicht Mauna Loa

    // ---------------- Bewuchs: in Flecken über der Lava, nicht als Schleier
    float patchN = fbm(p * 0.045, 3) * 0.7 + 0.3 * vnoise(p * 0.4);
    float vegMask = smoothstep(-0.06, 0.06, veg - patchN * 0.95 - 0.03);
    float treeMask = smoothstep(-0.08, 0.08, trees - fbm(p * 0.02 + 5.0, 3) * 0.9) * vegMask;
    vec3 crown = (treeMask > 0.01 && fw < 6.0) ? voro(p / 5.5) : vec3(0.6, 0.3, 0.5);
    float crownShape = 1.0 - smoothstep(0.2, 0.95, crown.x);
    vec3 low = lowVegColor(p, rain);
    vec3 vegC = mix(low, canopyColor(rain, crownShape, crown.z), treeMask);
    // aus der Ferne mittelt sich das Kronendach (Farbe wie die gezeichneten Bäume im Mittel)
    vegC = mix(vegC, mix(low, vec3(0.022, 0.038, 0.015), treeMask), smoothstep(1.5, 6.0, fw) * treeMask * 0.7);
    a = mix(a, vegC, vegMask);
    rough = mix(rough, 0.9, vegMask);
    ao *= mix(1.0, mix(0.65, 0.6 + 0.4 * crownShape, smoothstep(6.0, 1.5, fw)), treeMask);

    // ---------------- Schwefel an den Dampfstellen
    float sulf = uSulfurCount > 0 ? sulfurAt(p) * smoothstep(0.5, 0.75, fbm(p * 0.09, 4) + 0.1 * vnoise(p * 0.6)) : 0.0;
    // gelbe Schwefelkrusten in Flecken, sonst weißlich gebleichter, zersetzter Basalt
    vec3 sulfC = mix(vec3(0.16, 0.15, 0.125), vec3(0.24, 0.20, 0.055), smoothstep(0.55, 0.8, vnoise(p * 0.35)));
    a = mix(a, sulfC, sulf * 0.8 * (1.0 - vegMask));
    rough = mix(rough, 0.85, sulf);

    // ---------------- Steilwand, Küste, Meeresboden
    a = mix(a, cliffRock(vWorld), steep * (1.0 - 0.5 * treeMask));
    // Schutthalde am Fuß der Wände: mittlere Neigung, grob gefleckt
    float talus = smoothstep(0.93, 0.82, n0.y) * (1.0 - steep) * (1.0 - vegMask);
    a = mix(a, vec3(0.062, 0.057, 0.052) * (0.75 + 0.5 * vnoise(p * 0.7)), talus * 0.7);
    rough = mix(rough, 0.85, steep);
    float wetSand = shore * smoothstep(6.0, 0.5, h);
    float sand = shore * (1.0 - steep);
    a = mix(a, vec3(0.030, 0.029, 0.030) * (0.85 + 0.3 * vnoise(p * 2.0)), sand);
    a *= 1.0 - 0.45 * wetSand;
    rough = mix(rough, 0.25, wetSand);
    if (h < -0.5) { a = mix(vec3(0.05, 0.05, 0.045), a, 0.4); rough = 0.8; }

    // ---------------- Lava dieses Laufs: deckt alles darunter zu
    LavaS L = lavaSample(p);
    float lc = smoothstep(0.2, 0.6, L.cover);
    if (lc > 0.0) {
        // frisch: glasig schwarz mit bläulich-silbrigem Schimmer (Pāhoehoe), ʻaʻā braunschwarz und matt [G nach Fotos]
        vec3 fresh = mix(vec3(0.045, 0.046, 0.050), vec3(0.036, 0.031, 0.027), L.aa) * (0.85 + 0.3 * vnoise(p * 0.21));
        a = mix(a, fresh, lc);
        rough = mix(rough, mix(0.42, 0.88, L.aa), lc);
        aa = mix(aa, L.aa, lc);
        young = mix(young, 1.0, lc);
        // im Meer erstarrt: schwarzer Glassand am Rand des Deltas (Phase 10)
        if ((L.flags & LAVA_SEA) != 0) {
            float sandK = smoothstep(2.5, -1.0, L.top) * (1.0 - smoothstep(0.02, 0.1, L.h)) * lc;
            a = mix(a, vec3(0.020, 0.019, 0.021) * (0.85 + 0.3 * vnoise(p * 3.0)), sandK);
            rough = mix(rough, 0.65, sandK);
        }
        if (length(L.vel) > 0.05) {
            vec2 dd = mix(down, normalize(L.vel), lc);
            if (length(dd) > 1e-3) down = normalize(dd);   // nie einen Nullvektor normieren (NaN frisst sich über den Bloom ins ganze Bild)
        }
        ao = mix(ao, 1.0, lc);
    }

    // ---------------- Normale: Kleinrelief der Lava (drei Abfragen, nur wo es Bildpunkte trägt)
    vec3 n = n0;
    float lavaShow = mix((1.0 - vegMask) * (1.0 - ashMask) * (1.0 - steep * 0.7) * (1.0 - sand), 1.0, lc);
    if (uDetail > 0.01 && fw < 1.2 && h > -2.0 && lavaShow > 0.02) {
        float e = max(fw * 0.5, 0.02);
        float h0 = lavaRelief(p, aa, young, fw, down);
        float hx = lavaRelief(p + vec2(e, 0.0), aa, young, fw, down);
        float hz = lavaRelief(p + vec2(0.0, e), aa, young, fw, down);
        vec2 g = vec2(hx - h0, hz - h0) / e * lavaShow * uDetail;
        vec3 t = normalize(cross(n0, vec3(0.0, 0.0, 1.0)));
        vec3 b = cross(t, n0);
        n = normalize(n0 - t * g.x - b * g.y);
    }

    Surf sf;
    sf.albedo = a;
    sf.rough = rough;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos, n0, uCamPos, gl_FragCoord.xy);
    vec3 V = normalize(uCamPos - vPos);
    vec3 c = shadeSurface(sf, n, V, ao, sv);
    // Lichtschein der Lava (von unten und von der Seite, ohne Abschattung) und die Glut selbst
    c += sf.albedo * lavaLight(p) / PI * uLavaExposure;
    c += sf.albedo / PI * carLight(vPos, n);   // Phase 15: Scheinwerfer
    if (lc > 0.0) c += lc * lavaEmission(p, L, fw) * uLavaExposure;
    // unter dem Meeresspiegel: Licht durch die Wassersäule, Kaustik (Phase 10)
    if (vWorld.y < 0.0) c = underwaterLight(c, sf.albedo, vWorld, n, sv, uSunColor, uSunDir, uWaterTime);

    if (uShowAge != 0) {
        vec3 k = age < 10.0 ? vec3(1.0, 0.2, 0.05) : age < 30.0 ? vec3(1.0, 0.55, 0.1) : age < 60.0 ? vec3(1.0, 0.9, 0.2)
               : age < 120.0 ? vec3(0.55, 0.9, 0.25) : age < 250.0 ? vec3(0.25, 0.75, 0.75) : age < 1000.0 ? vec3(0.25, 0.38, 0.75)
               : age < 10000.0 ? vec3(0.45, 0.25, 0.55) : vec3(0.32, 0.2, 0.2);
        c = mix(c, k * (dot(c, vec3(0.333)) * 2.0 + 1e-4) * 1.4, 0.75);
    }
    if (uShowLod != 0) {
        vec3 lc = 0.5 + 0.5 * cos(6.2832 * (floor(vLod) * 0.13 + vec3(0.0, 0.33, 0.67)));
        c = mix(c, lc * (dot(c, vec3(0.333)) * 2.0 + 1e-4), 0.6);
        c *= 0.6 + 0.4 * fract(vLod);
    }

    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);   // nie NaN in den Bloom
    frag = vec4(c, 1.0);
}
