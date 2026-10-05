// Shared by the Shaders module's passes: one uniform block, position and
// normal reconstruction from the depth buffer, and a little noise.

layout(std140) uniform ShaderInfo {
    // NDC -> camera-relative world position (camera rotation + projection, no translation).
    mat4 InvViewProj;
    // Camera-relative world position -> clip space.
    mat4 ViewProj;
    // xyz: camera position (x/z wrapped to 4096), w: time (s)
    vec4 Camera;
    // xy: texel size of the full-resolution screen, z: 1 if NDC depth is 0..1, w: frame counter
    vec4 Screen;
    // xyz: direction to the sun (or the moon at night), w: daylight 0..1
    vec4 SunDir;
    // rgb: sun/moon light, w: 1 by day (sun), 0 at night (moon)
    vec4 SunColor;
    // xy: sun position on screen (uv), z: how visible it is (0 behind the camera), w: unused
    vec4 SunScreen;
    // rgb: sky colour, w: rain level
    vec4 SkyColor;
    // rgb: water fog colour, w: 1 while the camera is under water
    vec4 FogColor;
    // x: reflection strength, y: reflection distance (blocks), z: light ray strength, w: AO strength
    vec4 Strength1;
    // x: bloom strength, y: bloom threshold, z: auto exposure strength, w: saturation
    vec4 Strength2;
    // x: wetness 0..1 (fades in and out), y: puddle amount, z: quality 0..2, w: wetness strength
    vec4 Strength3;
    // effects on (1) or off (0): x reflections, y light rays, z AO, w wetness
    vec4 Flags1;
    // x bloom, y tonemapping, z auto exposure, w underwater fog
    vec4 Flags2;
    // x depth of field, y chromatic aberration, z sharpening, w sky
    vec4 Flags3;
    // x: reflection steps, y: AO samples, z: light ray samples, w: depth of field taps
    vec4 Steps;
};

uniform sampler2D DepthSampler;

bool isSky(float depth) {
    return depth <= 0.0; // reverse depth: nothing drawn there
}

// Camera-relative world position of the pixel at uv with this depth.
vec3 positionAt(vec2 uv, float depth) {
    float z = Screen.z > 0.5 ? depth : depth * 2.0 - 1.0;
    if (isSky(depth)) {
        z = Screen.z > 0.5 ? 0.5 : 0.0; // any point along the ray: only the direction matters
    }
    vec4 w = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return w.xyz / w.w;
}

vec3 positionAt(vec2 uv) {
    return positionAt(uv, texture(DepthSampler, uv).r);
}

// Screen uv of a camera-relative world position; z < 0 when it is behind the camera.
vec3 project(vec3 p) {
    vec4 c = ViewProj * vec4(p, 1.0);
    if (c.w <= 1e-4) {
        return vec3(-1.0, -1.0, -1.0);
    }
    return vec3(c.xy / c.w * 0.5 + 0.5, 1.0);
}

// World-space normal from the depth buffer: of the two neighbours on each
// axis the one on the same surface (closer in depth) is used, so edges stay clean.
vec3 normalAt(vec2 uv, vec3 p) {
    vec2 t = Screen.xy;
    vec3 r = positionAt(uv + vec2(t.x, 0.0)), l = positionAt(uv - vec2(t.x, 0.0));
    vec3 u = positionAt(uv + vec2(0.0, t.y)), d = positionAt(uv - vec2(0.0, t.y));
    vec3 dx = length(r - p) < length(p - l) ? r - p : p - l;
    vec3 dy = length(u - p) < length(p - d) ? u - p : p - d;
    vec3 n = normalize(cross(dy, dx));
    return dot(n, p) > 0.0 ? -n : n;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += valueNoise(p) * a;
        p = p * 2.03 + vec2(17.3, 9.1);
        a *= 0.5;
    }
    return v;
}

// Interleaved gradient noise for per-pixel jitter.
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Where puddles lie on flat ground (0..1); p is camera-relative, n the normal.
float puddleMask(vec3 p, vec3 n) {
    vec3 w = p + Camera.xyz;
    float level = smoothstep(0.9, 0.98, n.y);
    // fbm sits around 0.45 ± 0.12: "0" leaves a few small puddles, "1" floods most of the ground.
    float threshold = 0.68 - Strength3.y * 0.3;
    float noise = fbm(w.xz * 0.11) * 0.85 + valueNoise(w.xz * 0.9) * 0.15;
    return level * smoothstep(threshold, threshold + 0.05, noise);
}

// How wet the surface looks (0..1), puddles fully wet.
float wetnessAt(vec3 p, vec3 n) {
    if (Flags1.w < 0.5 || Strength3.x <= 0.001) {
        return 0.0;
    }
    float up = clamp(n.y * 0.5 + 0.5, 0.0, 1.0);
    return Strength3.x * Strength3.w * mix(0.55, 1.0, up);
}
