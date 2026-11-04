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
    vec2 centered = v_uv - 0.5;

    float phase = u_time * pixscapeEntityFloat(2);
    float waveX = sin(centered.y * pixscapeEntityFloat(1) + phase);
    float waveY = cos(centered.x * pixscapeEntityFloat(1) * 0.7 + phase * 1.2);

    vec2 offset = vec2(waveX, waveY) * pixscapeEntityFloat(0);
    vec2 uv = clamp(v_uv + offset, 0.0, 1.0);

    vec4 tex = texture(u_array, vec3(uv, v_layer));
    fragColor = pixscapeApplyMaterial(tex, v_color, u_ambientMul);
}
