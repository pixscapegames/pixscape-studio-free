#version 300 es
precision mediump float;
precision mediump int;
precision mediump sampler2DArray;

#include "pixscape_common.glsl"
#include "pixscape_entity_params.glsl"

in vec2  v_uv;
in vec4  v_color;
flat in int v_layer;

uniform sampler2DArray u_array;
uniform float u_time;
uniform vec3  u_ambientMul;

out vec4 fragColor;

void main() {
    vec4 tex = texture(u_array, vec3(v_uv, v_layer));
    vec4 base = pixscapeApplyMaterial(tex, v_color, u_ambientMul);

    vec2 centered = v_uv * 2.0 - 1.0;
    float dist = length(centered);

    float wave = 0.5 + 0.5 * sin(u_time * pixscapeEntityFloat(1));
    float glow = smoothstep(pixscapeEntityFloat(0), 0.0, dist) * pixscapeEntityFloat(2) * wave;

    float boost = 1.0 + pixscapeEntityFloat(3);
    vec3 color = base.rgb * boost + glow * vec3(1.0, 0.9, 0.6);

    fragColor = vec4(color, base.a);
}
