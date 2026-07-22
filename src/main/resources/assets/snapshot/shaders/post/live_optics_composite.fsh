#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D OriginalSampler;
uniform sampler2D BlurredSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform SnapshotOptics {
    vec4 Lens;
    vec4 DepthConfig;
    vec4 ImageConfig;
    vec4 Effects;
    vec4 Performance;
};

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 point) {
    vec3 value = fract(vec3(point.xyx) * 0.1031);
    value += dot(value, value.yzx + 33.33);
    return fract((value.x + value.y) * value.z);
}

float linearDepth(float depth) {
    float nearPlane = DepthConfig.x;
    float farPlane = DepthConfig.y;
    float ndc = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) /
        max(0.0001, farPlane + nearPlane - ndc * (farPlane - nearPlane));
}

vec2 lensWeatherOffset(vec2 uv) {
    float rain = Effects.y;
    if (rain < 0.01) {
        return vec2(0.0);
    }
    vec2 cells = vec2(24.0, 13.0);
    vec2 cell = floor(uv * cells);
    vec2 local = fract(uv * cells) - 0.5;
    float random = hash12(cell);
    local.y += fract(GameTime * (0.08 + random * 0.11) + random) - 0.5;
    float radius = 0.10 + random * 0.16;
    float drop = smoothstep(radius, radius * 0.42, length(local));
    return normalize(local + vec2(0.0001)) * drop * rain * 0.0018;
}

vec3 gradeFilm(vec3 color) {
    float profile = ImageConfig.x;
    float temperature = ImageConfig.y;
    color *= vec3(1.0 + temperature * 0.22, 1.0, 1.0 - temperature * 0.25);

    if (profile > 0.5 && profile < 1.5) {
        color = color * vec3(1.045, 1.0, 0.94) + vec3(0.012, 0.008, 0.018);
        color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.94);
        color = (color - 0.5) * 0.96 + 0.5;
        vec3 shoulder = max(color - 0.72, 0.0);
        color -= shoulder * shoulder * 0.18;
    } else if (profile > 1.5 && profile < 2.5) {
        color = color * vec3(0.95, 0.965, 1.015) + vec3(0.008, 0.012, 0.014);
        color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.78);
        color = (color - 0.5) * 1.10 + 0.5;
        color.g *= 0.985 + color.b * 0.025;
    } else if (profile > 2.5) {
        float luminance = dot(color, vec3(0.24, 0.68, 0.08));
        luminance = (luminance - 0.5) * 1.08 + 0.5;
        color = vec3(luminance * 1.01, luminance, luminance * 0.98);
    }

    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, ImageConfig.w);
    color = (color - 0.5) * ImageConfig.z + 0.5;
    return color;
}

void main() {
    vec2 uv = clamp(texCoord + lensWeatherOffset(texCoord), vec2(0.001), vec2(0.999));
    vec4 original = texture(OriginalSampler, uv);
    vec4 blurred = texture(BlurredSampler, uv);
    vec3 color = mix(original.rgb, blurred.rgb, blurred.a);

    float centerDistance = linearDepth(texture(MainDepthSampler, uv).r);
    float focusDelta = abs(centerDistance - Lens.x) / max(0.25, Lens.x);
    float edgeStrength = length(dFdx(original.rgb)) + length(dFdy(original.rgb));
    float peaking = Effects.x * smoothstep(0.10, 0.34, edgeStrength)
        * (1.0 - smoothstep(0.018, 0.055, focusDelta));
    color = mix(color, Performance.yzw, peaking * 0.82);

    float condensation = Effects.z;
    if (condensation > 0.01) {
        float haze = smoothstep(0.28, 0.92, hash12(floor(uv * vec2(17.0, 10.0))));
        color = mix(color, color * 0.82 + vec3(0.16, 0.18, 0.20), haze * condensation * 0.16);
    }
    float dust = smoothstep(0.985, 0.998, hash12(floor(uv * ScreenSize / 9.0)));
    color *= 1.0 - dust * Effects.w * 0.18;

    fragColor = vec4(clamp(gradeFilm(color), 0.0, 1.0), original.a);
}
