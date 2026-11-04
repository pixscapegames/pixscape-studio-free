#version 330 core

in vec2  a_position;
in vec2  a_texCoord0;
in vec4  a_color;
in float a_layer;
in float a_paramId;

uniform mat4 u_projTrans;

out vec2  v_uv;
out vec4  v_color;
flat out int v_layer;
flat out int v_paramId;

void main() {
    v_uv    = a_texCoord0;
    v_color = a_color;
    v_layer = int(a_layer + 0.5);
    v_paramId = int(a_paramId + 0.5);
    gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);
}
