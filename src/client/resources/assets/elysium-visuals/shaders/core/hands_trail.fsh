#version 330

uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;

layout(std140) uniform TrailInfo {
    // x: how much of last frame's trail is kept, y: upward drift (uv per frame)
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

// Hand-trail memory: this frame's hand mask, plus last frame's trail faded a
// bit and shifted up (so it rises like flames). Stored in the red channel.
void main() {
    float mask = texture(DepthSampler, texCoord).r > 0.0 ? 1.0 : 0.0;
    float previous = texture(HistorySampler, texCoord - vec2(0.0, Params.y)).r * Params.x;
    fragColor = vec4(max(mask, previous), 0.0, 0.0, 1.0);
}
