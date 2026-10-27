// terrain.frag — das Gelände seit Phase 2: Farbe nach der Nutzungsart aus ALKIS (der Wald bleibt Platzhalter bis zu
// forest in Phase 9), Fels nach der Neigung, dazu die Marken (marken.glsl). Seit Phase 8 der Boden nach der Bodenkarte
// (uBoden: Moor, Heide, Blockfeld, Fels): Heide als Mosaik aus Besenheide, Heidelbeere und Drahtschmiele nach der
// Jahreszeit (uBodenJahr), Moor aus Torfmoos (grün und rot), Wollgras im Juni und dunklen Schlenken, Blockfeld aus
// grauen Blöcken mit Flechten und Moos in den Fugen, Fels aus com.dan.cliff (Granit mit Wollsäcken, cliff.glsl). Farben linear,
// aus der Karte des Imports (tools.GelaendeImport.FARBE). Ohne ALKIS (Niedersachsen, „ohne Angabe“) steht Fichtenwald
// mit etwas Totholz [G].
// Licht seit Phase 3: Sonne und Mond aus com.dan.sky (vorbelichtet), Himmelslicht als Bestrahlung/π (uSkyAmb), die
// Schatten der Kuppe je Sonnenstand (uShadeMain/uShadeFern) und der Wolken (uCloudShadow: R unter der Schicht, G über
// ihr, dazwischen nach der Höhe), Dunst aus der Sicht mit der Farbe des Rundumbilds. Ohne com.dan.sky der
// Platzhalter-Himmel von Phase 2.
#include "common.glsl"
#include "terrain_height.glsl"
#include "marken.glsl"
#include "/com/dan/cliff/gl/cliff.glsl"
#include "/com/dan/rime/gl/rime.glsl"
uniform float uRaueisZweig[12];            // Phase 10: Dicke des Eises (m) je Band zu 50 m ab 600 m: Zweige (Wald, Heide)
uniform float uRaueisFels[12];             // … und Fels, Blöcke, Boden
uniform sampler2D uBoden;                  // Phase 8: Bodenkarte RGBA = Moor, Heide, Blockfeld, Fels
uniform vec4 uBodenRect;                   // Ecke x, z, Kantenlänge, an
uniform vec4 uBodenJahr;                   // Wollgras blüht, Besenheide blüht, Heidelbeere rot, Winter (je 0..1), in 1 050 m
uniform vec4 uBodenJahrUnten;              // Phase 11: dasselbe in 750 m (rund 12 Tage früher)
vec4 bodenJahr;                            // je Bildpunkt nach der Höhe gemischt
uniform float uSchnee[16];                 // Phase 11: Schneehöhe (m) je Stufe zu 50 m ab 400 m
uniform int uSchneeOn;
uniform vec2 uSchneeNass;                  // Nässe des Schnees, Schnee in den Kronen (0..1)
uniform sampler2D uTrasseMaske;            // Phase 12: G Schotter, B Planum (Raster des Bahnkörpers)
uniform sampler2D uDrift;                  // Verwehung: R Faktor/4, G Wechte (Raster der Waldkarte)
uniform int uFelsOn;                       // Granit aus cliff.glsl
uniform sampler2D uWald;                   // Phase 9: Waldkarte RGBA = Bestockung, Anteil tot, Windflüchter, Buche
uniform vec4 uWaldRect;                    // Ecke x, z, Breite (x), an
uniform float uWaldRectZ;                  // Tiefe (z)
uniform vec2 uWaldNah;                     // bis x zeigt der Grund den Waldboden (dort stehen Bäume), ab y die Kronen
uniform sampler2D uShadeMain, uShadeFern;   // Phase 3: Anteil direkten Lichts (Schatten der Kuppe)
uniform vec3 uShadeRectMain, uShadeRectFern; // Ecke x, z, Kantenlänge
uniform int uShadeOn;
uniform sampler2D uCloudShadow;            // Phase 3: Wolkenschatten (com.dan.cloud), RG
uniform vec4 uCloudRect;                   // Ecke x, z, Kantenlänge, an
uniform vec2 uCloudLayer;                  // Basis und Obergrenze der unteren Schicht (m über NHN)
in vec3 vWorld;
in vec3 vNormal;
in float vDist;
out vec4 frag;

vec3 srgb(vec3 c) { return pow(c, vec3(2.2)); }

vec3 artColor(uint a) {
    if (a == 1u) return srgb(vec3(0.176, 0.290, 0.216));   // Wald: Fichte
    if (a == 2u) return srgb(vec3(0.314, 0.412, 0.290));   // Gehölz
    if (a == 3u) return srgb(vec3(0.470, 0.400, 0.385));   // Heide: Besenheide und Krähenbeere, im Herbst braun (Phase 3: weniger violett)
    if (a == 4u) return srgb(vec3(0.498, 0.392, 0.282));   // Moor und Sumpf
    if (a == 5u) return srgb(vec3(0.667, 0.549, 0.510));   // Fels: Brockengranit
    if (a == 6u) return srgb(vec3(0.486, 0.604, 0.361));   // Wiese und Feld
    if (a == 7u) return srgb(vec3(0.275, 0.439, 0.561));   // Wasser
    if (a == 8u) return srgb(vec3(0.560, 0.540, 0.505));   // Siedlung (Phase 3: dunkler, im Licht von com.dan.sky zu hell)
    if (a == 9u) return srgb(vec3(0.784, 0.745, 0.659));   // Weg und Platz
    if (a == 10u) return srgb(vec3(0.353, 0.353, 0.373));  // Straße
    if (a == 11u) return srgb(vec3(0.380, 0.375, 0.300));  // Bahn: seit Phase 12 Böschung und Planum (den Schotter trägt der Bahnkörper)
    return srgb(vec3(0.20, 0.30, 0.23));                   // ohne Angabe: Wald [G]
}

// Phase 8: Wertrauschen in der Ebene (Gitter je Einheit, weich), 0..1
float bodenHash(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float bodenNoise(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(bodenHash(i), bodenHash(i + vec2(1, 0)), f.x), mix(bodenHash(i + vec2(0, 1)), bodenHash(i + vec2(1, 1)), f.x), f.y);
}
// Mosaik mit Flecken der Größe s (m); in der Ferne (Bildpunkt pxm m) zum Mittel 0,5
float bodenFleck(vec2 p, float s, float pxm) { return mix(bodenNoise(p / s), 0.5, smoothstep(0.15 * s, 0.6 * s, pxm)); }

// Heide: Besenheide (blüht im August violett), Heidelbeere (im Herbst rot), Drahtschmiele (strohig); im Winter braun
vec3 heideColor(vec2 p, float pxm) {
    float a = bodenFleck(p, 3.0, pxm), b = bodenFleck(p + 31.0, 14.0, pxm);
    vec3 besen = mix(srgb(vec3(0.31, 0.27, 0.21)), srgb(vec3(0.40, 0.29, 0.35)), 0.6 * bodenJahr.y * smoothstep(0.25, 0.8, a));
    vec3 beere = mix(srgb(vec3(0.31, 0.34, 0.21)), srgb(vec3(0.50, 0.25, 0.17)), bodenJahr.z);
    vec3 gras = srgb(vec3(0.50, 0.47, 0.33));
    vec3 c = mix(besen, beere, smoothstep(0.2, 0.8, b));   // weiche Übergänge: kein Tarnmuster
    c = mix(c, gras, 0.3 * smoothstep(0.55, 0.8, a * 0.5 + b * 0.5));
    return mix(c, srgb(vec3(0.36, 0.29, 0.23)), bodenJahr.w);
}
// Moor: Torfmoos grün und rot, Wollgras im Juni weiß, Schlenken dunkel (nur, wo es ganz Moor ist)
vec3 moorColor(vec2 p, float m, float pxm) {
    float a = bodenFleck(p, 2.5, pxm), b = bodenFleck(p + 17.0, 11.0, pxm), w = bodenFleck(p + 53.0, 1.6, pxm);
    vec3 c = mix(srgb(vec3(0.36, 0.38, 0.19)), srgb(vec3(0.40, 0.20, 0.15)), smoothstep(0.58, 0.82, a));
    c = mix(c, srgb(vec3(0.42, 0.38, 0.25)), 0.3 * smoothstep(0.45, 0.75, b));   // Seggen, Rasenbinse
    c = mix(c, srgb(vec3(0.86, 0.86, 0.80)), bodenJahr.x * 0.14 * (0.4 + w));   // ein weißer Schleier; die Schöpfe selbst zeichnet die Bodendecke
    c = mix(c, srgb(vec3(0.33, 0.29, 0.22)), bodenJahr.w);
    float pool = smoothstep(0.85, 0.95, m) * smoothstep(0.72, 0.8, bodenFleck(p + 7.0, 6.0, pxm));
    return mix(c, srgb(vec3(0.16, 0.19, 0.22)), pool);   // Schlenke: Wasser, spiegelt den Himmel matt
}
// Blockfeld: graue Blöcke mit Flechten, dunkle Fugen mit Moos
vec3 blockColor(vec2 p, float pxm) {
    float a = bodenFleck(p, 1.2, pxm), l = bodenFleck(p + 9.0, 0.7, pxm);
    vec3 stein = mix(srgb(vec3(0.40, 0.38, 0.35)), srgb(vec3(0.44, 0.45, 0.37)), 0.5 * smoothstep(0.5, 0.75, l));
    // zwischen den Blöcken, die der Durchgang „Fels“ zeichnet: Schatten, Moos und Rohhumus
    return mix(srgb(vec3(0.13, 0.15, 0.08)), stein, 0.6 * smoothstep(0.4, 0.65, a));
}

// Anteil direkten Lichts aus den Schatten der Kuppe; am Rand des Hauptrasters in den Fernring übergeblendet
float groundLight(vec2 w) {
    if (uShadeOn == 0) return 1.0;
    float f = 1.0 - smoothstep(0.0, uNaht, insideDist(w));
    float a = f < 1.0 ? textureLod(uShadeMain, (w - uShadeRectMain.xy) / uShadeRectMain.z, 0.0).r : 1.0;
    vec2 fu = (w - uShadeRectFern.xy) / uShadeRectFern.z;
    float b = f > 0.0 && all(greaterThanEqual(fu, vec2(0.0))) && all(lessThanEqual(fu, vec2(1.0))) ? textureLod(uShadeFern, fu, 0.0).r : 1.0;
    return mix(a, b, f);
}
// Wolkenschatten: unter der unteren Schicht R, über ihr nur hohe Schicht (G), in ihr nach der Höhe
float cloudLight(vec2 w, float y) {
    if (uCloudRect.w <= 0.0) return 1.0;
    vec2 uv = (w - uCloudRect.xy) / uCloudRect.z;
    vec2 c = textureLod(uCloudShadow, uv, 0.0).rg;
    float s = mix(c.r, c.g, smoothstep(uCloudLayer.x, uCloudLayer.y, y));
    // außerhalb der Karte (24 km um das Auge) kein Schatten: sonst zog der Rand der Karte Streifen bis zur Kimm (Phase 4)
    vec2 e = min(uv, 1.0 - uv);
    return mix(1.0, s, smoothstep(0.0, 0.08, min(e.x, e.y)));
}

void main() {
    vec2 p = vWorld.xz;
    bodenJahr = mix(uBodenJahrUnten, uBodenJahr, smoothstep(750.0, 1050.0, vWorld.y));
    // Normale im Bildpunkt aus der Höhe (fein in der Nähe, gröber in der Ferne). Nie enger als das Raster an diesem Ort:
    // sonst zeichnen sich die Kanten der bilinearen Zellen als Streifen ab (Befund Phase 2); im Kern mit DGM1 bis 1 m.
    float kern = uKernOn == 1 ? smoothstep(0.0, uKNaht, insideOf(p, uKernGrid, uKernN)) : 0.0;
    float e = clamp(vDist * 0.003, mix(uGrid.z * 0.8, 1.0, kern), 80.0);
    float lvl = heightLevel(vDist) - 1.0;
    float hx = heightAt(p + vec2(e, 0.0), lvl) - heightAt(p - vec2(e, 0.0), lvl);
    float hz = heightAt(p + vec2(0.0, e), lvl) - heightAt(p - vec2(0.0, e), lvl);
    vec3 n = normalize(mix(vNormal, normalize(vec3(-hx, 2.0 * e, -hz)), 1.0 - smoothstep(4000.0, 12000.0, vDist)));

    // Nutzungsart weich an den Rändern: die vier nächsten Zellen, bilinear gewichtet (vier Abrufe statt einer je Art)
    vec3 alb = vec3(0.0);
    float waldig = 0.0;
    {
        vec2 u = (p - uGrid.xy) / uGrid.z - 0.5;
        ivec2 i = ivec2(floor(u));
        vec2 f = u - vec2(i);
        bool drin = insideDist(p) > 0.0;
        for (int b = 0; b <= 1; b++)
            for (int c = 0; c <= 1; c++) {
                ivec2 q = clamp(i + ivec2(c, b), ivec2(0), ivec2(int(uGridN) - 1));
                uint a = drin ? texelFetch(uSource, q, 0).r : 0u;
                float wt = (c == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
                alb += artColor(a) * wt;
                if (a <= 1u) waldig += wt;
            }
    }
    // Phase 9: der Wald nach der Waldkarte. Nah, wo die Bäume aus com.dan.forest stehen, der Waldboden (Nadelstreu unter
    // lebenden Fichten, Reitgras und Moos unter dem Totholz); fern, wo keine Bäume mehr gezeichnet werden, die Kronen von
    // oben: dunkle Fichten, silbergraues Totholz, Buchen. Offenes Land in der Nutzung „Wald“ (Moor, Heide) wird Gras.
    vec4 wk = vec4(0.0);
    if (uWaldRect.w > 0.0) {
        vec2 wu = (p - uWaldRect.xy) / vec2(uWaldRect.z, uWaldRectZ);
        if (all(greaterThan(wu, vec2(0.0))) && all(lessThan(wu, vec2(1.0)))) wk = textureLod(uWald, wu, 0.0);
        float pxm0 = length(fwidth(p));
        // offenes Land in der Nutzung „Wald“: Reitgras und Zwergsträucher, an der Baumgrenze wie die Heide
        alb = mix(alb, mix(srgb(vec3(0.36, 0.37, 0.25)), heideColor(p, pxm0), smoothstep(0.1, 0.5, wk.b)), waldig);
        float fl = bodenFleck(p, 18.0, pxm0);
        // Totholz von oben: graue Stämme über grünem Jungwuchs und Reitgras, insgesamt graubraun mit grünem Stich
        vec3 tot = mix(srgb(vec3(0.37, 0.34, 0.30)), srgb(vec3(0.26, 0.31, 0.20)), 0.15 + 0.25 * fl);
        vec3 krone = mix(srgb(vec3(0.17, 0.27, 0.20)), tot, wk.g * (0.8 + 0.25 * fl));
        krone = mix(krone, srgb(vec3(0.28, 0.40, 0.20)), wk.a);
        vec3 grund = mix(srgb(vec3(0.27, 0.22, 0.16)), srgb(vec3(0.35, 0.36, 0.23)), wk.g * (0.6 + 0.4 * fl));
        float nah = uWaldNah.y > 0.0 ? 1.0 - smoothstep(uWaldNah.x, uWaldNah.y, vDist) : 0.0;
        alb = mix(alb, mix(krone, grund, nah), wk.r);
        waldig = 0.0;   // die Streuung unten trägt jetzt die Karte
    }
    // Totholz im Fichtenwald (seit 2018) als Streuung je 15 m, in der Ferne zum Mittel ausgeblendet, wo die Zellen im Bild
    // kleiner als rund vier Bildpunkte werden [G, die Anteile kommen in Phase 9 aus dem DOP20]
    float streu = fract(sin(dot(floor(p / 15.0), vec2(12.9898, 78.233))) * 43758.5453);
    float pxm = length(fwidth(p));   // Meter je Bildpunkt
    // gemittelt, wo eine Zelle unter rund zehn Bildpunkte fällt (Ferne) oder über 60 Bildpunkte groß wird (vor dem Auge)
    float grob = max(smoothstep(0.8, 2.5, pxm), 1.0 - smoothstep(0.15, 0.3, pxm));
    float tot = mix(step(0.6, streu), 0.4, grob) * 0.15 * waldig;   // Phase 3: schwächer (im diffusen Licht unter Wolken trat das Schachbrett hervor)
    alb = mix(alb, srgb(vec3(0.52, 0.50, 0.47)), tot);
    // Phase 8: Boden nach der Bodenkarte (Heide, Moor, Blockfeld)
    vec4 bod = vec4(0.0);
    if (uBodenRect.w > 0.0) {
        vec2 bu = (p - uBodenRect.xy) / uBodenRect.z;
        if (all(greaterThan(bu, vec2(0.0))) && all(lessThan(bu, vec2(1.0)))) bod = textureLod(uBoden, bu, 0.0);
        alb = mix(alb, heideColor(p, pxm), bod.g);
        alb = mix(alb, moorColor(p, bod.r, pxm), bod.r);
        alb = mix(alb, blockColor(p, pxm), bod.b * 0.85);
    }
    // steiler Fels: Klippen und Blockhänge zeigen Granit (seit Phase 8 aus cliff.glsl, mit Wollsäcken in der Nähe)
    float felsW = max(smoothstep(0.62, 0.45, n.y), bod.a);
    if (uFelsOn == 1) {
        if (felsW > 0.004 && vDist < 6000.0) {
            CliffSample cs = cliffMaterial(vWorld, n, 0.0, 0.0, pxm);
            alb = mix(alb, cs.albedo, felsW);
            n = normalize(mix(n, cs.normal, felsW));
        } else alb = mix(alb, srgb(vec3(0.60, 0.55, 0.50)), felsW);
    } else alb = mix(alb, srgb(vec3(0.62, 0.52, 0.49)), felsW);
    // Phase 12: der Bahnkörper: Schotter unter dem Gleis, das Planum daneben als Kies, die Böschungen begrünt; die
    // Nutzung „Bahn“ des 12,5-m-Rasters fern davon nicht mehr als Schotterband (dort gilt die Umgebung)
    if (uTrasseOn == 1) {
        vec2 tu = (p - uTrasseRect.xy) / uTrasseRect.zw;
        vec4 tm = vec4(0.0);
        if (all(greaterThan(tu, vec2(0.0))) && all(lessThan(tu, vec2(1.0)))) tm = textureLod(uTrasseMaske, tu, 0.0);
        float kies = bodenFleck(p, 1.5, pxm);
        vec3 planum = mix(srgb(vec3(0.42, 0.39, 0.35)), srgb(vec3(0.36, 0.36, 0.30)), 0.4 * kies);
        vec3 schotter = mix(srgb(vec3(0.40, 0.37, 0.35)), srgb(vec3(0.30, 0.27, 0.25)), kies);
        alb = mix(alb, planum, tm.b * 0.8);
        alb = mix(alb, schotter, tm.g);
    }
    // Phase 11: Schnee nach der Höhe (Stufen aus com.dan.season) und der Verwehung; im Wald verweht wenig, von fern
    // decken die Kronen (lebende Fichten dunkel, Totholz licht, darin der Schnee am Boden); steiler Fels hält keinen
    if (uSchneeOn == 1) {
        float u = clamp((vWorld.y - 400.0) / 50.0, 0.0, 15.0);
        int i0 = int(floor(u)), i1 = min(i0 + 1, 15);
        float d = mix(uSchnee[i0], uSchnee[i1], u - float(i0));
        vec4 dr = vec4(0.25, 0.0, 0.0, 0.0);
        if (uWaldRect.w > 0.0) {
            vec2 du = (p - uWaldRect.xy) / vec2(uWaldRect.z, uWaldRectZ);
            if (all(greaterThan(du, vec2(0.0))) && all(lessThan(du, vec2(1.0)))) dr = textureLod(uDrift, du, 0.0);
        }
        float f = dr.r * 4.0;
        f = mix(f, 1.0, 0.7 * wk.r);
        float ds = d * f;
        // fleckig ausapern: kleine Mulden und Steine (Rauschen über rund 6 und 30 m)
        float fl = bodenFleck(p, 6.0, pxm) * 0.6 + bodenFleck(p, 30.0, pxm) * 0.4;
        float c = smoothstep(0.01 + 0.08 * fl, 0.06 + 0.12 * fl, ds);
        c = max(c, smoothstep(0.3, 0.8, dr.g) * step(0.03, d));                       // Wechten weiß an der Kante
        c *= 1.0 - smoothstep(0.5, 0.75, felsW) * smoothstep(0.62, 0.35, n.y);       // steiler Fels bleibt dunkel
        c *= smoothstep(0.25, 0.55, n.y);                                            // an Steilhängen rutscht er ab
        vec3 schnee = mix(vec3(0.82, 0.85, 0.90), vec3(0.62, 0.64, 0.66), 0.5 * uSchneeNass.x);
        // vom Wind gezeichnet: gestreckte Flecken (rund 4 m) und Mulden, kaum heller oder dunkler [G]
        schnee *= 0.93 + 0.05 * bodenFleck(p * vec2(1.0, 0.35), 4.0, pxm) + 0.03 * fl;
        // fern über dem Wald: Kronen decken einen Teil; Totholz und Jungwuchs lassen viel Boden sehen
        float nahW = uWaldNah.y > 0.0 ? 1.0 - smoothstep(uWaldNah.x, uWaldNah.y, vDist) : 0.0;
        float sicht = mix(1.0, mix(0.35 + 0.5 * wk.g, 1.0, nahW), wk.r);
        float krone = wk.r * (1.0 - nahW) * uSchneeNass.y * 0.5 * smoothstep(0.02, 0.2, d);
        alb = mix(alb, schnee, clamp(c * sicht + krone, 0.0, 1.0));
    }
    // Phase 10: Raueis nach der Höhe (Bänder aus com.dan.rime): Zweige der Bäume und Zwergsträucher fangen auf der
    // Luvseite, von oben gesehen wird die Fläche weiß, je dicker, desto mehr; Fels nach seiner Luvseite (rime.glsl)
    if (uRimeArt != 0) {
        float u = clamp((vWorld.y - 600.0) / 50.0, -1.0, 11.0);
        int i0 = int(floor(max(u, 0.0))), i1 = min(i0 + 1, 11);
        float f = clamp(u - float(i0), 0.0, 1.0), lo = u < 0.0 ? 1.0 + u : 1.0;
        float dz = mix(uRaueisZweig[i0], uRaueisZweig[i1], f) * lo, dfs = mix(uRaueisFels[i0], uRaueisFels[i1], f) * lo;
        // Pflanzen: ihre Zweige stehen senkrecht, die Luvseite zählt – von oben deckt das Eis nach seiner Länge
        float cz = rimeLocal(normalize(uRimeVon * 0.7 + vec3(0.0, 0.3, 0.0) + n * 0.3), dz, uRimeArt, uRimeVon);
        cz = max(cz, smoothstep(0.02, 0.15, dz) * 0.85 * float(uRimeArt == 1));
        float cf = rimeLocal(n, dfs, uRimeArt, uRimeVon) * rimeFeder(vWorld, uRimeVon, 1.0);
        float rr = 0.5;
        alb = rimeApplyLocal(alb, max(cz * (1.0 - felsW), cf * felsW), uRimeArt, rr);
    }
    // Fernring: außerhalb des Hauptrasters keine Nutzung, ein Mittel aus Wald und Feld nach der Höhe
    float fern = 1.0 - smoothstep(0.0, uNaht, insideDist(p));
    alb = mix(alb, mix(srgb(vec3(0.42, 0.50, 0.32)), srgb(vec3(0.20, 0.30, 0.22)), smoothstep(250.0, 500.0, vWorld.y)), fern);

    vec3 col;
    float B;
    if (uSkyMode == 1) {
        // com.dan.sky: Leuchtdichte = Albedo/π · Bestrahlung; uSkyAmb ist schon Bestrahlung/π
        float shade = groundLight(p) * cloudLight(p, vWorld.y);
        vec3 direct = (sunLight() * max(dot(n, uSun), 0.0) + moonLight() * max(dot(n, uMoonDir), 0.0)) * shade;
        vec3 amb = uSkyAmb * (0.6 + 0.4 * n.y);
        col = alb * (direct / 3.14159265 + amb);
        // Marken so hell wie heller Grund im vollen Licht (statt der Himmelshelligkeit des Platzhalters)
        B = 0.32 * dot(sunLight() * max(uSun.y, 0.0) / 3.14159265 + uSkyAmb + moonLight() * max(uMoonDir.y, 0.0) / 3.14159265, vec3(0.2126, 0.7152, 0.0722));
    } else {
        vec3 L = sunLight();
        float sun = max(dot(n, uSun), 0.0);
        B = skyBrightness(sunElevDeg());
        vec3 amb = vec3(0.30, 0.38, 0.52) * B / 0.32 * (0.55 + 0.45 * n.y);
        col = alb * (L * sun + amb);
    }
    col = applyMarks(col, p, vWorld.y, vDist, B);
    vec3 v = vWorld - uCam;
    float dist = length(v);
    col = aerial(col, v / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
