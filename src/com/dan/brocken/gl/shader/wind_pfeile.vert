// wind_pfeile.vert — Phase 4: Pfeile des Windfelds über dem Gelände. Je Instanz ein Pfeil auf einem Raster um den
// Blickpunkt (uArrowGrid: Ecke x, z, Abstand, Pfeile je Seite) in uArrowH m über Grund; sechs Ecken je Pfeil als
// Linien (Schaft, zwei Flügel). Wind aus der 3D-Textur von com.dan.wind (wind.glsl, mit dem Faktor uGain), Grund aus
// terrain_height.glsl wie das Gelände, Erdkrümmung wie terrain.vert. Länge nach der Geschwindigkeit, Farbe von blau
// (still) über weiß und gelb bis rot (Sturm).
#include "common.glsl"
#include "terrain_height.glsl"
#include "/com/dan/wind/gl/wind.glsl"
uniform vec4 uArrowGrid;
uniform float uArrowH, uArrowScale, uGain;
out vec3 vCol;
out float vFade;

vec3 speedColor(float s) {
    vec3 a = vec3(0.35, 0.55, 1.0), b = vec3(0.95, 0.95, 0.92), c = vec3(1.0, 0.82, 0.25), d = vec3(1.0, 0.30, 0.20);
    return s < 6.0 ? mix(a, b, s / 6.0) : s < 15.0 ? mix(b, c, (s - 6.0) / 9.0) : mix(c, d, clamp((s - 15.0) / 15.0, 0.0, 1.0));
}

void main() {
    int n = int(uArrowGrid.w);
    int inst = gl_InstanceID, vid = gl_VertexID;
    vec2 p = uArrowGrid.xy + (vec2(inst % n, inst / n) + 0.5) * uArrowGrid.z;
    float d0 = distance(p, uCam.xz);
    float g = terrainHeight(p, d0);
    vec3 v = windSample(p, uArrowH).xyz * uGain;
    float s = length(v);
    vec3 dir = s > 0.02 ? v / s : vec3(1.0, 0.0, 0.0);
    float len = uArrowGrid.z * 0.85 * clamp(s / uArrowScale, 0.08, 1.0);
    vec3 base = vec3(p.x, g + uArrowH, p.y) - dir * len * 0.5;
    vec3 tip = base + dir * len;
    vec3 side = normalize(cross(dir, vec3(0.0, 1.0, 0.0)) + vec3(1e-4));
    vec3 wing = tip - dir * len * 0.3;
    vec3 q = vid == 0 ? base : vid == 1 || vid == 2 || vid == 4 ? tip : vid == 3 ? wing + side * len * 0.15 : wing - side * len * 0.15;
    vCol = speedColor(s);
    vFade = 1.0 - smoothstep(0.75, 1.0, length((p - uArrowGrid.xy) / (uArrowGrid.z * float(n)) * 2.0 - 1.0));
    vec2 dh = q.xz - uCam.xz;
    gl_Position = uVP * vec4(q.x, q.y - dot(dh, dh) / (2.0 * EARTH_R / 0.87), q.z, 1.0);
}
