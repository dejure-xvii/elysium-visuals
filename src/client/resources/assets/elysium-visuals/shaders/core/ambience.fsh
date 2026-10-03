#version 330

uniform sampler2D MainSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform AmbienceInfo {
    mat4 InvProj;
    // x: saturation, y: 1 = blur fog on, z: fog strength, w: fog density
    vec4 Params;
    // rgb: theme tint, a: how much of the tint the fog takes
    vec4 Tint;
    // x: 1 if NDC depth is 0..1, y: render distance (blocks), zw: one texel
    vec4 Extra;
};

in vec2 texCoord;
out vec4 fragColor;

// Poisson-ish disk: soft, round blur with few taps.
const vec2 DISK[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598)
);

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec3 color = texture(MainSampler, texCoord).rgb;

    if (Params.y > 0.5) {
        float depth = texture(DepthSampler, texCoord).r;
        float z = Extra.x > 0.5 ? depth : depth * 2.0 - 1.0;
        vec4 view = InvProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
        float dist = length(view.xyz / view.w);
        // Clear up close, thickening with distance (squared exponential falloff).
        float d = dist / max(Extra.y, 16.0) * Params.w * 3.0;
        float fog = (1.0 - exp(-d * d)) * Params.z;

        if (fog > 0.003) {
            float radius = fog * 9.0;
            vec3 blurred = color;
            for (int i = 0; i < 12; i++) {
                blurred += texture(MainSampler, texCoord + DISK[i] * Extra.zw * radius).rgb;
            }
            blurred /= 13.0;
            // Fog body: the scene's own brightness, tinted towards the theme color.
            vec3 haze = mix(vec3(luma(blurred)) * 0.6 + 0.4, Tint.rgb, Tint.a);
            color = mix(color, mix(blurred, haze, 0.55), fog);
        }
    }

    color = mix(vec3(luma(color)), color, Params.x);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
