#ifndef PIXSCAPE_COMMON_GLSL
#define PIXSCAPE_COMMON_GLSL

vec4 pixscapeApplyMaterial(vec4 texColor, vec4 vertexColor, vec3 ambientMul) {
    vec4 c = texColor * vertexColor;
    c.rgb *= ambientMul;
    return c;
}

#endif