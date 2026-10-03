#version 330

uniform sampler2D MainSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;

layout(std140) uniform MotionInfo {
    // Current NDC -> previous frame's NDC (camera rotation and/or movement).
    mat4 Reproj;
    // x: strength, y: samples, z: history blend, w: 1 if depth is 0..1 NDC
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

const float MAX_SHIFT = 0.06;

// Runs on the world only (before the hand is drawn), with the world's depth.
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    float z = Params.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, z, 1.0);

    vec4 prev = Reproj * ndc;
    vec2 prevUv = prev.xy / prev.w * 0.5 + 0.5;
    vec2 velocity = (texCoord - prevUv) * Params.x;
    float len = length(velocity);
    if (len > MAX_SHIFT) {
        velocity *= MAX_SHIFT / len;
    }

    int samples = int(Params.y);
    vec3 sum = texture(MainSampler, texCoord).rgb;
    for (int i = 1; i < samples; i++) {
        // Spread the samples along the motion, centred on the pixel.
        float t = float(i) / float(samples - 1) - 0.5;
        sum += texture(MainSampler, clamp(texCoord - velocity * t, 0.0, 1.0)).rgb;
    }
    vec3 blurred = sum / float(samples);

    vec3 history = texture(HistorySampler, texCoord).rgb;
    fragColor = vec4(mix(blurred, history, Params.z), 1.0);
}
