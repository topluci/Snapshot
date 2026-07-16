#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform SnapshotOptics {
    vec4 Lens;
    vec4 DepthConfig;
    vec4 ImageConfig;
    vec4 Effects;
};

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const int TAP_COUNT = 18;

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

float circleOfConfusion(float subjectDistance) {
    float focusDistance = max(Lens.x, 0.06);
    float focalLength = Lens.y / 1000.0;
    float aperture = max(Lens.z, 1.0);
    float subject = max(subjectDistance, focalLength + 0.001);
    float focus = max(focusDistance, focalLength + 0.001);
    float circle = abs(focalLength * focalLength * (subject - focus) /
        max(0.00001, aperture * subject * (focus - focalLength)));
    return min(Lens.w, circle / 0.036 * ScreenSize.x * 0.68);
}

float polygonRadius(float angle, float blades) {
    float sector = 2.0 * PI / max(3.0, blades);
    float local = mod(angle + sector * 0.5, sector) - sector * 0.5;
    return cos(PI / max(3.0, blades)) / max(0.25, cos(local));
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
    vec4 centerSample = texture(InSampler, uv);
    float centerDistance = linearDepth(texture(MainDepthSampler, uv).r);
    float centerBlur = circleOfConfusion(centerDistance);
    vec2 texel = 1.0 / ScreenSize;

    float foregroundSpill = 0.0;
    for (int index = 0; index < 8; index++) {
        float angle = 2.0 * PI * float(index) / 8.0;
        vec2 probeUv = clamp(uv + vec2(cos(angle), sin(angle)) * texel * 7.0, vec2(0.001), vec2(0.999));
        float probeDistance = linearDepth(texture(MainDepthSampler, probeUv).r);
        float depthGap = (centerDistance - probeDistance) / max(0.1, centerDistance);
        float probeBlur = circleOfConfusion(probeDistance);
        if (depthGap > 0.12 && probeBlur > 0.65) {
            foregroundSpill = max(foregroundSpill, probeBlur * 0.42);
        }
    }

    float blurRadius = max(centerBlur, foregroundSpill);
    vec3 accumulated = centerSample.rgb;
    float totalWeight = 1.0;
    vec2 fromCenter = uv - 0.5;
    float edge = clamp(length(fromCenter) * 1.75, 0.0, 1.0);
    vec2 radial = normalize(fromCenter + vec2(0.00001));

    for (int index = 0; index < TAP_COUNT; index++) {
        float fraction = (float(index) + 0.5) / float(TAP_COUNT);
        float angle = float(index) * 2.39996323;
        float ring = sqrt(fraction);
        float shape = polygonRadius(angle, DepthConfig.z);
        vec2 apertureOffset = vec2(cos(angle), sin(angle)) * ring * shape;
        apertureOffset *= 1.0 - edge * DepthConfig.w * 0.28;
        apertureOffset -= radial * edge * DepthConfig.w * ring * 0.22;
        vec2 sampleUv = clamp(uv + apertureOffset * blurRadius * texel, vec2(0.001), vec2(0.999));
        vec3 sampleColor = texture(InSampler, sampleUv).rgb;
        float sampleDistance = linearDepth(texture(MainDepthSampler, sampleUv).r);
        float sampleBlur = circleOfConfusion(sampleDistance);
        float foregroundWeight = sampleDistance < centerDistance ? 1.0 : 0.0;
        float regularWeight = smoothstep(0.15, 1.5, max(centerBlur, sampleBlur));
        float weight = max(0.08, mix(regularWeight, foregroundWeight, step(centerBlur + 0.2, foregroundSpill)));
        float fartherDepth = (sampleDistance - centerDistance) / max(0.1, centerDistance);
        float fartherSample = smoothstep(0.025, 0.16, fartherDepth);
        float centerIsSharp = 1.0 - smoothstep(0.28, 1.25, centerBlur);
        weight *= mix(1.0, 0.04, fartherSample * centerIsSharp);
        float highlight = max(0.0, dot(sampleColor, vec3(0.2126, 0.7152, 0.0722)) - 0.70);
        sampleColor += sampleColor * highlight * highlight * smoothstep(1.5, 10.0, sampleBlur) * 0.72;
        accumulated += sampleColor * weight;
        totalWeight += weight;
    }

    vec3 color = mix(centerSample.rgb, accumulated / totalWeight, smoothstep(0.42, 1.75, blurRadius));

    float focusDelta = abs(centerDistance - Lens.x) / max(0.25, Lens.x);
    float edgeStrength = length(dFdx(centerSample.rgb)) + length(dFdy(centerSample.rgb));
    float peaking = Effects.x * smoothstep(0.10, 0.34, edgeStrength) * (1.0 - smoothstep(0.018, 0.055, focusDelta));
    color = mix(color, vec3(0.18, 0.95, 0.86), peaking * 0.82);

    float condensation = Effects.z;
    if (condensation > 0.01) {
        float haze = smoothstep(0.28, 0.92, hash12(floor(uv * vec2(17.0, 10.0))));
        color = mix(color, color * 0.82 + vec3(0.16, 0.18, 0.20), haze * condensation * 0.16);
    }
    float dust = smoothstep(0.985, 0.998, hash12(floor(uv * ScreenSize / 9.0)));
    color *= 1.0 - dust * Effects.w * 0.18;

    fragColor = vec4(clamp(gradeFilm(color), 0.0, 1.0), centerSample.a);
}
