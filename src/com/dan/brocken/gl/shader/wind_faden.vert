// wind_faden.vert — Phase 4: Rauchfäden des Windfelds als Linienzüge (Punkte x, y, z, Weg), vom Windrechner auf dem
// Prozessor gezogen. Erdkrümmung wie terrain.vert; zum Ende hin blasser.
#include "common.glsl"
layout(location = 0) in vec4 aPoint;
uniform float uLen;
out vec3 vCol;
out float vFade;
void main() {
    vec3 q = aPoint.xyz;
    vCol = vec3(0.96, 0.93, 0.88);
    vFade = 0.85 * (1.0 - smoothstep(0.6, 1.0, aPoint.w / uLen)) * smoothstep(0.0, 150.0, aPoint.w + 40.0);
    vec2 dh = q.xz - uCam.xz;
    gl_Position = uVP * vec4(q.x, q.y - dot(dh, dh) / (2.0 * EARTH_R / 0.87), q.z, 1.0);
}
