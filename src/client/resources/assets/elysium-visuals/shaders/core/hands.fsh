#version 330

uniform sampler2D MainSampler;       // the frame with the hand
uniform sampler2D DepthSampler;      // > 0 where the hand / held item is (depth was cleared before them)
uniform sampler2D BackgroundSampler; // the world before the hand was drawn
uniform sampler2D HistorySampler;    // trail memory (red channel)

layout(std140) uniform HandsInfo {
    vec4 ColorA;
    vec4 ColorB;
    // x: mode, y: opacity, z: time (s), w: plasma speed
    vec4 P1;
    // x: blur strength, y: glow radius (px), z: glow strength, w: outline thickness (px)
    vec4 P2;
    // xy: one texel, z: 1 to draw the trail, w: unused
    vec4 P3;
};

in vec2 texCoord;
out vec4 fragColor;

const vec2 DISK[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598)
);

float mask(vec2 uv) {
    return texture(DepthSampler, uv).r > 0.0 ? 1.0 : 0.0;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

vec3 plasma(vec2 uv, float t) {
    vec2 p = uv * vec2(9.0, 6.0);
    float v = sin(p.x + t) + sin(p.y * 1.3 - t * 0.8) + sin((p.x + p.y) * 0.8 + t * 0.6) + sin(length(p - 4.0) * 1.5 - t);
    float k = 0.5 + 0.5 * sin(v * 1.2);
    return mix(ColorA.rgb, ColorB.rgb, k) + pow(1.0 - abs(sin(v * 1.6)), 6.0) * 0.3;
}

void main() {
    vec3 scene = texture(MainSampler, texCoord).rgb;
    float m = mask(texCoord);
    int mode = int(P1.x + 0.5);
    float opacity = P1.y, t = P1.z;
    vec2 texel = P3.xy;
    vec3 col = scene;

    if (m > 0.5) {
        if (mode == 0) {
            // Fill: a soft vertical gradient of the theme colors.
            vec3 fill = mix(ColorA.rgb, ColorB.rgb, smoothstep(0.0, 1.0, texCoord.y + 0.15 * sin(t + texCoord.x * 3.0)));
            col = mix(scene, fill, opacity);
        } else if (mode == 1) {
            // Glass: the blurred world behind the hand, tinted, with a bright rim and a sheen.
            float r = P2.x * 14.0;
            vec3 blurred = texture(BackgroundSampler, texCoord).rgb;
            for (int i = 0; i < 12; i++) {
                blurred += texture(BackgroundSampler, texCoord + DISK[i] * texel * r).rgb;
            }
            blurred /= 13.0;
            float edge = 0.0;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398;
                edge += 1.0 - mask(texCoord + vec2(cos(a), sin(a)) * texel * 2.5);
            }
            edge = clamp(edge / 4.0, 0.0, 1.0);
            vec3 glass = mix(blurred, ColorA.rgb, 0.18) + vec3(0.12) * smoothstep(0.3, 1.0, texCoord.y);
            glass += vec3(1.0) * edge * 0.55;
            col = mix(scene, glass, opacity);
        } else if (mode == 2 || mode == 6) {
            col = mix(scene, plasma(texCoord, t * P1.w), opacity);
        }
    } else {
        if (mode == 3) {
            // Outline: pixels just outside the hand within the thickness.
            float cover = 0.0;
            for (int i = 0; i < 12; i++) {
                float a = float(i) * 0.523599;
                vec2 dir = vec2(cos(a), sin(a)) * texel;
                cover = max(cover, mask(texCoord + dir * P2.w));
                cover = max(cover, mask(texCoord + dir * P2.w * 0.5));
            }
            col = mix(scene, mix(ColorA.rgb, ColorB.rgb, texCoord.y), cover * opacity);
        } else if (mode == 4) {
            // Halo: how much hand is around this pixel, in two rings.
            float glow = 0.0;
            for (int i = 0; i < 12; i++) {
                float a = float(i) * 0.523599;
                vec2 dir = vec2(cos(a), sin(a)) * texel;
                glow += mask(texCoord + dir * P2.y) * 0.6 + mask(texCoord + dir * P2.y * 0.5);
            }
            glow = clamp(glow / 12.0, 0.0, 1.0);
            col = scene + mix(ColorA.rgb, ColorB.rgb, glow) * glow * P2.z;
        }
    }

    // Fire trail behind the moving hand (outside the hand itself).
    if (P3.z > 0.5 && m < 0.5) {
        float h = texture(HistorySampler, texCoord).r;
        if (h > 0.01) {
            float flicker = noise(texCoord * vec2(40.0, 25.0) + vec2(0.0, -t * 6.0));
            vec3 fire = mix(ColorB.rgb, ColorA.rgb, h) * (0.7 + 0.6 * flicker);
            fire += vec3(1.0, 0.85, 0.6) * pow(h, 3.0) * 0.4;
            col += fire * h * opacity;
        }
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
