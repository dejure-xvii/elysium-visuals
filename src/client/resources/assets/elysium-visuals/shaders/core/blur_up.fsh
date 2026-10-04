#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurInfo {
    // xy: texel size of the input, z: sample offset in texels
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

// Dual-filter (Kawase) upsample: eight taps around the pixel, double resolution out.
void main() {
    vec2 h = Params.xy * Params.z;
    vec3 sum = texture(InSampler, texCoord + vec2(-h.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, h.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(h.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -h.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}
