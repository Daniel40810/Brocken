// wind_linie.frag — Phase 4: Farbe der Pfeile und Rauchfäden. Hell wie heller Grund im Licht (uInk, vorbelichtet),
// zum Rand und nach hinten ausgeblendet (vFade), Dunst wie das Gelände.
#include "common.glsl"
in vec3 vCol;
in float vFade;
out vec4 frag;
uniform float uInk;
void main() {
    if (vFade <= 0.0) discard;
    frag = vec4(vCol * uInk, vFade);
}
