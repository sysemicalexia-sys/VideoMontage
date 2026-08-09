#pragma once

namespace montage { namespace shaders {

// Fullscreen triangle, no VBO — positions come from gl_VertexID.
inline const char* kVertex = R"GLSL(
#version 300 es
uniform mat4 uMvp;
out vec2 vUv;
void main() {
    vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0, (gl_VertexID == 2) ? 3.0 : -1.0);
    vUv = p * 0.5 + 0.5;
    gl_Position = uMvp * vec4(p, 0.0, 1.0);
}
)GLSL";

// Source passthrough with opacity.
inline const char* kLayer = R"GLSL(
#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform float uOpacity;
out vec4 frag;
void main() {
    vec4 c = texture(uTex, vUv);
    frag = vec4(c.rgb, c.a * uOpacity);
}
)GLSL";

// Color-grade node: exposure/contrast/saturation/temperature in one pass.
// One pass beats four — fewer FBO switches, one texture fetch.
inline const char* kColorGrade = R"GLSL(
#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform float uExposure;    // stops, -3..3
uniform float uContrast;    // 0..2
uniform float uSaturation;  // 0..2
uniform float uTemperature; // -1..1
out vec4 frag;
void main() {
    vec3 c = texture(uTex, vUv).rgb;
    c *= exp2(uExposure);
    c = (c - 0.5) * uContrast + 0.5;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, uSaturation);
    c.r += uTemperature * 0.08;
    c.b -= uTemperature * 0.08;
    frag = vec4(clamp(c, 0.0, 1.0), 1.0);
}
)GLSL";

// Separable gaussian — run twice (H then V).
inline const char* kBlur = R"GLSL(
#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uDirection;   // (1/w,0) or (0,1/h)
uniform float uRadius;
out vec4 frag;
void main() {
    vec3 sum = vec3(0.0);
    float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    sum += texture(uTex, vUv).rgb * weights[0];
    for (int i = 1; i < 5; ++i) {
        vec2 off = uDirection * uRadius * float(i);
        sum += texture(uTex, vUv + off).rgb * weights[i];
        sum += texture(uTex, vUv - off).rgb * weights[i];
    }
    frag = vec4(sum, 1.0);
}
)GLSL";

// Dissolve between two completed layers.
inline const char* kDissolve = R"GLSL(
#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
out vec4 frag;
void main() {
    frag = mix(texture(uFrom, vUv), texture(uTo, vUv), smoothstep(0.0, 1.0, uProgress));
}
)GLSL";

}} // namespace montage::shaders
