// section.frag — was die Schnittfläche zeigt: oben endet sie am Gelände (dieselbe Höhenfunktion wie das
// Gelände), darunter der Stapel aus Lavabänken, ab rund 11 km die alte ozeanische Kruste; dazwischen
// die Gleitfläche der Südflanke. Magma leuchtet: Speicher nach Druck, Zufuhr aus der Tiefe, Verbindung
// zum Halemaʻumaʻu-Speicher, Gänge in der Riftzone (frisch hell, erstarrt dunkelrot), Beben als Ringe,
// die verblassen. Meerwasser über dem Meeresboden halb durchsichtig blau. Ausgabe: HDR-Farbe.
// Phase 7 (nach Statusbild 6): Glühen gedämpft und weich begrenzt, damit Speicher und Gang nicht weiß
// ausbrennen; Beben als kleiner Punkt mit dünnem Ring statt als gefüllte Scheibe.
#include "common.glsl"
#include "lighting.glsl"
#include "terrain_height.glsl"
in vec3 vPos;
in vec3 vWorld;
in vec2 vAY;
out vec4 frag;
uniform vec3 uCamPos;
uniform vec3 uFaceNormal;       // Normale der Fläche zur Kamera hin
uniform int uMain;              // 1 = Schnittebene (mit Magma), 0 = Stirnseite oder Boden
uniform vec4 uRes[2];           // Speicher: a, y, Halbachse waagerecht, senkrecht
uniform float uResP[2];         // Druck 0..1 (Bruchgrenze)
uniform vec4 uLink;             // Verbindung: a0, y0, a1, y1 (Südcaldera → Halemaʻumaʻu)
uniform vec4 uDike[6];          // Gänge: a0, a1, Oberkante, Unterkante
uniform float uDikeI[6];        // 1 = läuft, 0,3 = erstarrt
uniform int uDikeCount;
uniform vec4 uQuake[96];        // Beben: a, y, Magnitude, Sichtbarkeit 0..1
uniform int uQuakeCount;
uniform float uDecol;           // Höhe der Gleitfläche (m)
uniform float uTime;
uniform float uExposureInv = 1.0;   // nicht mehr verwendet (Phase 8): das Glühen ist schon vorbelichtet

float ellipse(vec2 p, vec2 c, vec2 r) { vec2 d = (p - c) / r; return length(d); }

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
    return length(p - a - ab * t);
}

void main() {
    vec2 p = vAY;                       // a (entlang der Fläche), y
    float camDist = length(vWorld - uCamPos);
    float hTop = terrainHeight(vWorld.xz, camDist);
    if (p.y > max(hTop, 0.0) + 1.0) discard;
    // Stirnseiten und Boden: nur Gestein, ohne Magma
    bool mainFace = uMain == 1;
    float px = max(length(fwidth(p)), 1.0);
    vec3 col;
    vec3 glow = vec3(0.0);
    if (p.y > hTop) {
        // Meerwasser über dem Grund
        float depth = -p.y;
        col = mix(vec3(0.03, 0.09, 0.12), vec3(0.01, 0.025, 0.05), clamp(depth / 3000.0, 0.0, 1.0));
    } else {
        float below = hTop - p.y;
        // Lavabänke: dünne Lagen, nach unten dichter und dunkler; unter dem Meer Kissenlava und Schutt
        float lay = fract(p.y / 60.0 + 0.6 * vnoise(vec2(p.x * 0.0006, p.y * 0.002)));
        float bandL = smoothstep(0.0, 0.08, lay) * smoothstep(1.0, 0.85, lay);
        vec3 pile = mix(vec3(0.075, 0.065, 0.058), vec3(0.11, 0.095, 0.08), bandL);
        pile *= 0.9 + 0.2 * vnoise(p * 0.004);
        pile = mix(pile, vec3(0.06, 0.06, 0.065), smoothstep(-500.0, -3000.0, p.y) * 0.5);
        vec3 crust = mix(vec3(0.055, 0.07, 0.065), vec3(0.07, 0.085, 0.08), vnoise(p * 0.002));
        float decol = uDecol + 900.0 * (vnoise(vec2(p.x * 0.0002, 1.3)) - 0.5);
        col = p.y > decol ? pile : crust;
        // Schnittkante an der Oberfläche als helle Linie, damit das Profil des Berges zu sehen ist
        col = mix(col, vec3(0.30, 0.27, 0.22), smoothstep(3.0 * px, 0.0, below));
        // Erdmantel unter der Kruste (ab rund 17 km)
        col = mix(col, vec3(0.09, 0.07, 0.05), smoothstep(-16800.0, -17200.0, p.y));
        // Gleitfläche
        float dl = abs(p.y - decol);
        float dash = step(0.35, fract(p.x / 800.0));
        glow += vec3(0.25, 0.75, 0.9) * 0.6 * smoothstep(3.0 * px, 0.0, dl) * dash;
        // Tiefenlinien alle 2 km
        float gy = abs(fract(p.y / 2000.0 + 0.5) - 0.5) * 2000.0;
        col *= 1.0 - 0.25 * smoothstep(1.5 * px, 0.0, gy);
    }
    // Meeresspiegel
    col = mix(col, vec3(0.2, 0.45, 0.55), smoothstep(1.5 * px, 0.0, abs(p.y)) * 0.8);

    // ---------------- Magma (nur auf der Schnittebene)
    if (mainFace) {
    // Zufuhr aus der Tiefe unter den Südcaldera-Speicher, Verbindung nach oben
    float feed = smoothstep(260.0, 120.0, abs(p.x - uRes[1].x - 0.04 * (uRes[1].y - p.y))) * step(p.y, uRes[1].y);
    float link = smoothstep(150.0, 60.0, segDist(p, uLink.xy, uLink.zw));
    vec3 magma = vec3(1.0, 0.32, 0.06);
    glow += magma * 1.4 * max(feed * (0.6 + 0.4 * vnoise(vec2(p.y * 0.002 - uTime * 0.3, p.x * 0.01))), link);
    for (int i = 0; i < 2; i++) {
        float e = ellipse(p, uRes[i].xy, uRes[i].zw);
        float body = smoothstep(1.0, 0.9, e);
        float rim = smoothstep(1.15, 1.0, e) * (1.0 - body);
        float pr = clamp(uResP[i], 0.0, 1.2);
        // innen Zellen wie konvektierende Schmelze, zum Rand dunkler (kühlere Hülle)
        float cells = vnoise(p * 0.006 + vec2(uTime * 0.05, -uTime * 0.03));
        vec3 c = mix(vec3(0.45, 0.07, 0.02), vec3(1.1, 0.36, 0.07), pr) * (0.65 + 0.45 * cells) * mix(0.55, 1.0, smoothstep(1.0, 0.4, e));
        glow += c * body * 1.1 + vec3(0.9, 0.38, 0.12) * rim * pr * 0.8;
    }
    for (int i = 0; i < 6; i++) {
        if (i >= uDikeCount) break;
        vec4 d = uDike[i];
        float inside = step(min(d.x, d.y), p.x) * step(p.x, max(d.x, d.y)) * step(d.w, p.y) * step(p.y, d.z);
        float tip = smoothstep(600.0, 0.0, abs(p.x - d.y)) * inside;
        float hot = uDikeI[i];
        vec3 c = mix(vec3(0.30, 0.05, 0.02), vec3(0.9, 0.25, 0.05), hot);
        glow += inside * c * (0.6 + 0.3 * vnoise(p * 0.003)) + tip * hot * vec3(1.0, 0.42, 0.12);
    }
    // Beben: Ringe nach Magnitude, die verblassen
    for (int i = 0; i < 96; i++) {
        if (i >= uQuakeCount) break;
        vec4 q = uQuake[i];
        float r = max(60.0 + 50.0 * q.z, 3.0 * px);
        float d = length(p - q.xy);
        float w = max(0.12 * r, 1.5 * px);
        float ring = smoothstep(w, 0.0, abs(d - r));
        float core = smoothstep(max(0.16 * r, 2.0 * px), 0.0, d);
        glow += vec3(1.0, 0.88, 0.5) * (0.55 * ring + core) * q.w * 0.8;
    }

    }

    // Licht: Fläche zur Kamera, Himmel und etwas Sonne
    vec3 n = uFaceNormal;
    vec3 E = skyIrradiance(uSkyLut, n, uSunDir);
    // als Schaubild etwas heller als echter Basalt, und mit wenig Dunst (sonst verschwindet er auf 40 km im Blau)
    // weich begrenzen: viele Beben übereinander oder ein heißer Speicher dürfen nicht weiß ausbrennen
    glow = glow / (1.0 + 0.7 * max(glow.r, max(glow.g, glow.b)));
    // das Bild ist vorbelichtet: ein Wert von 1 ist auf dem Schirm hell, egal ob Tag oder Nacht (nicht durch die Belichtung teilen)
    vec3 c = col * 2.2 * (E / PI * 1.2 + uSunColor * 0.25 * max(dot(n, uSunDir), 0.3)) + glow * 0.55;
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist * 0.15, uSkyLut, uCamPos.y);
    frag = vec4(c, 1.0);
}
