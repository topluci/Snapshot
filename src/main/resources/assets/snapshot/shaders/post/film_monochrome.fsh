#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    float luminance = dot(source.rgb, vec3(0.24, 0.68, 0.08));
    luminance = (luminance - 0.5) * 1.08 + 0.5;
    vec3 color = vec3(luminance * 1.01, luminance, luminance * 0.98);
    fragColor = vec4(clamp(color, 0.0, 1.0), source.a);
}
