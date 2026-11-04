// Four RGBA32F texels per parameter row, 16 float slots per entity.
// The vertex-provided row is flat to keep every fragment on one primitive in the same row.
#ifdef GL_ES
precision highp sampler2D;
#endif
flat in highp int v_paramId;
uniform highp sampler2D u_entityParams;

float pixscapeEntityFloat(int slot) {
    vec4 values = texelFetch(u_entityParams, ivec2(slot / 4, v_paramId), 0);
    return values[slot % 4];
}
