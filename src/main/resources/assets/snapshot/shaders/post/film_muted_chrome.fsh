#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 color = source.rgb * vec3(0.95, 0.965, 1.015) + vec3(0.008, 0.012, 0.014);
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, 0.78);
    color = (color - 0.5) * 1.10 + 0.5;
    color.g *= 0.985 + color.b * 0.025;
    fragColor = vec4(clamp(color, 0.0, 1.0), source.a);
}
