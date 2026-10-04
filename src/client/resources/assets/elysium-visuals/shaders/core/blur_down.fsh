#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurInfo {
    // xy: texel size of the input, z: sample offset in texels
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

// Dual-filter (Kawase) downsample: the centre plus four diagonal taps, half resolution out.
void main() {
    vec2 h = Params.xy * Params.z;
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - h).rgb;
    sum += texture(InSampler, texCoord + h).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb;
    sum += texture(InSampler, texCoord - vec2(h.x, -h.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
