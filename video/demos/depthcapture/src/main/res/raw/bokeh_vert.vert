#version 300 es

in vec4 aPosition;
out vec2 vTextureCoord;

void main() {
    gl_Position = aPosition;
    vTextureCoord = aPosition.xy;
}
