#version 330

uniform sampler2D DepthSampler;

layout(std140) uniform SkyInfo {
    // Current NDC -> camera-relative direction (rotation and FOV only, so the sky stays put).
    mat4 InvViewProj;
    // x: time (s, scaled by speed), y: scale, z: intensity, w: opacity
    vec4 Params;
    vec4 ColorA;
    vec4 ColorB;
    // x: mode, y: theme tint 0..1, z: 1 if NDC depth is 0..1, w: daylight 0..1
    vec4 Extra;
};

in vec2 texCoord;
out vec4 fragColor;

// ---- noise -------------------------------------------------------------------

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x),
               mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// Stars on the sphere: fixed cells in direction space, each with its own twinkle.
float stars(vec3 dir, float density, float t) {
    vec3 p = dir * 180.0;
    vec3 cell = floor(p);
    float h = hash13(cell);
    if (h > density) {
        return 0.0;
    }
    vec3 center = cell + 0.5 + (vec3(hash13(cell + 1.3), hash13(cell + 2.7), hash13(cell + 4.1)) - 0.5) * 0.6;
    float d = length(p - center);
    float twinkle = 0.6 + 0.4 * sin(t * (1.0 + h * 40.0) + h * 100.0);
    return smoothstep(0.35, 0.0, d) * twinkle;
}

vec3 palette(float t, vec3 a, vec3 b) {
    return mix(a, b, 0.5 + 0.5 * sin(t * 6.2831));
}

// ---- modes -------------------------------------------------------------------

vec3 aurora(vec3 dir, float t, float s) {
    vec2 p = dir.xz / (abs(dir.y) + 0.35) * 1.5 * s;
    float n = fbm(p + vec2(t * 0.05, -t * 0.03));
    float bands = 0.5 + 0.5 * sin((p.x + p.y) * 1.5 + n * 6.0 + t * 0.4);
    vec3 col = mix(vec3(0.15, 0.05, 0.35), vec3(0.95, 0.35, 0.75), bands);
    col = mix(col, vec3(0.2, 0.85, 0.95), smoothstep(0.4, 0.9, n));
    return col * (0.55 + 0.6 * smoothstep(-0.2, 0.6, dir.y));
}

vec3 sakura(vec3 dir, float t, float s) {
    vec3 sky = mix(vec3(1.0, 0.86, 0.92), vec3(0.62, 0.72, 1.0), smoothstep(-0.1, 0.8, dir.y));
    vec2 p = dir.xz / (abs(dir.y) + 0.4) * 3.0 * s;
    vec3 petals = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float fl = float(layer);
        vec2 q = p * (1.0 + fl * 0.6) + vec2(t * (0.15 + fl * 0.05), -t * (0.25 + fl * 0.07));
        vec2 cell = floor(q);
        vec2 f = fract(q) - 0.5;
        float h = hash12(cell + fl * 17.0);
        if (h < 0.35) {
            float a = h * 40.0 + t * (0.5 + h);
            mat2 r = mat2(cos(a), -sin(a), sin(a), cos(a));
            vec2 e = r * (f - (vec2(hash12(cell + 3.1), hash12(cell + 7.7)) - 0.5) * 0.5);
            float petal = smoothstep(0.12, 0.05, length(e * vec2(1.0, 1.9)));
            petals += petal * vec3(1.0, 0.62, 0.78) * (0.6 + 0.4 * h);
        }
    }
    return sky + petals;
}

vec3 plasma(vec3 dir, float t, float s) {
    vec2 p = dir.xz / (abs(dir.y) + 0.5) * 2.0 * s;
    float v = sin(p.x * 1.3 + t) + sin(p.y * 1.7 - t * 0.8) + sin((p.x + p.y) * 1.1 + t * 0.6)
            + sin(length(p) * 2.0 - t * 1.2);
    return palette(v * 0.18 + t * 0.03, vec3(0.45, 0.15, 0.9), vec3(0.1, 0.8, 0.95)) * 0.95;
}

vec3 plasma2(vec3 dir, float t, float s) {
    vec2 p = dir.xz / (abs(dir.y) + 0.45) * 1.6 * s;
    // Domain warping: noise displacing noise, for liquid folds.
    vec2 q = vec2(fbm(p + t * 0.1), fbm(p + vec2(5.2, 1.3) - t * 0.08));
    vec2 r = vec2(fbm(p + 3.0 * q + vec2(1.7, 9.2)), fbm(p + 3.0 * q + vec2(8.3, 2.8) + t * 0.05));
    float f = fbm(p + 3.0 * r);
    vec3 col = mix(vec3(0.1, 0.02, 0.2), vec3(0.95, 0.3, 0.55), clamp(f * f * 2.5, 0.0, 1.0));
    col = mix(col, vec3(0.25, 0.75, 1.0), clamp(length(q), 0.0, 1.0) * 0.6);
    return col * (0.4 + f * 1.1);
}

vec3 northernLights(vec3 dir, float t, float s) {
    vec3 sky = mix(vec3(0.01, 0.02, 0.06), vec3(0.03, 0.07, 0.16), smoothstep(0.6, -0.05, dir.y));
    sky += stars(dir, 0.08, t) * 0.9;
    vec3 acc = vec3(0.0);
    // A few curtain layers at different heights, swaying with noise.
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float h = 0.18 + fi * 0.06;
        if (dir.y <= 0.02) {
            break;
        }
        vec2 p = dir.xz / dir.y * h * s;
        float n = fbm(vec2(p.x * 0.8 + t * 0.05, p.y * 0.3 + fi * 0.7));
        float curtain = smoothstep(0.45, 0.75, n) * smoothstep(1.0, 0.0, abs(p.y + sin(p.x * 0.7 + t * 0.2) * 0.8) * 0.4);
        vec3 c = mix(vec3(0.1, 1.0, 0.55), vec3(0.45, 0.3, 1.0), fi / 6.0);
        acc += c * curtain * (1.0 - fi / 7.0) * 0.45;
    }
    return sky + acc * smoothstep(0.0, 0.25, dir.y);
}

vec3 nightSky(vec3 dir, float t, float s) {
    vec3 sky = mix(vec3(0.02, 0.02, 0.07), vec3(0.06, 0.05, 0.18), smoothstep(0.7, -0.1, dir.y));
    sky += stars(dir, 0.12 * s, t) * 1.1;
    // Faint milky band across the sky.
    float band = exp(-pow(dot(dir, normalize(vec3(0.3, 0.2, 1.0))) * 4.0, 2.0));
    sky += vec3(0.25, 0.2, 0.45) * band * fbm(dir.xy * 8.0 + dir.z) * 0.6;
    return sky;
}

vec3 summer(vec3 dir, float t, float s, float day) {
    vec3 dayTop = vec3(0.22, 0.5, 0.95), dayHorizon = vec3(0.72, 0.86, 1.0);
    vec3 nightTop = vec3(0.01, 0.02, 0.07), nightHorizon = vec3(0.05, 0.08, 0.2);
    float up = smoothstep(-0.05, 0.6, dir.y);
    vec3 sky = mix(mix(nightHorizon, nightTop, up), mix(dayHorizon, dayTop, up), day);
    // Warm glow near the horizon at dusk and dawn.
    float dusk = 1.0 - abs(day * 2.0 - 1.0);
    sky += vec3(1.0, 0.45, 0.2) * dusk * exp(-max(dir.y, 0.0) * 6.0) * 0.5;
    // Clouds by day, stars by night.
    if (dir.y > 0.0) {
        vec2 p = dir.xz / (dir.y + 0.2) * 1.2 * s + vec2(t * 0.02, 0.0);
        float c = smoothstep(0.5, 0.8, fbm(p * 1.5));
        sky = mix(sky, mix(vec3(0.3, 0.32, 0.4), vec3(1.0), day), c * 0.8 * smoothstep(0.0, 0.2, dir.y));
    }
    sky += stars(dir, 0.1, t) * (1.0 - day) * smoothstep(0.0, 0.2, dir.y);
    return sky;
}

vec3 caustics(vec3 dir, float t, float s) {
    vec2 p = dir.xz / (abs(dir.y) + 0.3) * 3.0 * s;
    float c = 0.0;
    vec2 i = p;
    // Classic iterative caustic pattern.
    for (int n = 0; n < 4; n++) {
        float tt = t * 0.4 * (1.0 - 3.5 / float(n + 1));
        i = p + vec2(cos(tt - i.x) + sin(tt + i.y), sin(tt - i.y) + cos(tt + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + tt) / 0.008), p.y / (cos(i.y + tt) / 0.008)));
    }
    c /= 4.0;
    c = 1.17 - pow(c, 1.4);
    float v = pow(abs(c), 8.0);
    vec3 base = mix(vec3(0.0, 0.15, 0.3), vec3(0.0, 0.35, 0.55), smoothstep(-0.2, 0.7, dir.y));
    return base + vec3(0.6, 0.9, 1.0) * clamp(v, 0.0, 1.2);
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    // Reverse depth: the sky is where nothing was drawn (cleared to 0).
    if (depth > 0.0) {
        discard;
    }
    float z = Extra.z > 0.5 ? 0.5 : 0.0;
    vec4 world = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    vec3 dir = normalize(world.xyz / world.w);

    float t = Params.x, s = Params.y;
    int mode = int(Extra.x + 0.5);
    vec3 col;
    if (mode == 0) col = aurora(dir, t, s);
    else if (mode == 1) col = sakura(dir, t, s);
    else if (mode == 2) col = plasma(dir, t, s);
    else if (mode == 3) col = plasma2(dir, t, s);
    else if (mode == 4) col = northernLights(dir, t, s);
    else if (mode == 5) col = nightSky(dir, t, s);
    else if (mode == 6) col = summer(dir, t, s, Extra.w);
    else col = caustics(dir, t, s);

    // Theme tint: recolor by brightness onto the theme's two colors.
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    vec3 themed = mix(ColorA.rgb, ColorB.rgb, smoothstep(0.1, 0.9, l + 0.15 * sin(dir.x * 3.0 + t * 0.3))) * (0.35 + l * 1.3);
    col = mix(col, themed, Extra.y);

    fragColor = vec4(clamp(col * Params.z, 0.0, 1.0), Params.w);
}
